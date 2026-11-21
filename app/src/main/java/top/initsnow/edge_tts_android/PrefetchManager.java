package top.initsnow.edge_tts_android;

import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Keeps at most one upcoming utterance synthesized ahead of playback.
 * Audio is held in memory only and discarded when consumed or playback stops.
 */
final class PrefetchManager {
    private static final String TAG = "EdgeTtsPrefetch";
    private static final int MAX_PCM_BYTES = 3 * 1024 * 1024;
    private static final int READ_CHUNK_BYTES = 32 * 1024;
    private static final Object LOCK = new Object();
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "EdgeTts-Prefetch");
        thread.setDaemon(true);
        return thread;
    });

    private static CachedAudio cachedAudio;
    private static String activeKey;
    private static String activeRequestId;
    private static Future<?> activeFuture;
    private static volatile SynthesisConfig lastConfig;

    private PrefetchManager() {
    }

    static final class SynthesisConfig {
        final String voice;
        final String rate;
        final String volume;
        final String pitch;

        SynthesisConfig(String voice, String rate, String volume, String pitch) {
            this.voice = voice;
            this.rate = rate;
            this.volume = volume;
            this.pitch = pitch;
        }

        String key(String text) {
            return text + '\u0000' + voice + '\u0000' + rate + '\u0000' + volume + '\u0000' + pitch;
        }
    }

    private static final class CachedAudio {
        final String key;
        final byte[] pcm;

        CachedAudio(String key, byte[] pcm) {
            this.key = key;
            this.pcm = pcm;
        }
    }

    static void updateLastConfig(String voice, String rate, String volume, String pitch) {
        lastConfig = new SynthesisConfig(voice, rate, volume, pitch);
    }

    static SynthesisConfig getLastConfig() {
        return lastConfig;
    }

    static boolean prefetch(String text, SynthesisConfig config) {
        if (text == null || text.trim().isEmpty() || config == null) return false;
        final String key = config.key(text);
        synchronized (LOCK) {
            if (cachedAudio != null && cachedAudio.key.equals(key)) return true;
            if (key.equals(activeKey)) return true;
            // One bounded prefetch at a time. Never accumulate an entire chapter.
            if (activeKey != null) return false;
            activeKey = key;
            activeFuture = EXECUTOR.submit(() -> synthesizeAhead(key, text, config));
            return true;
        }
    }

    static byte[] take(String text, String voice, String rate, String volume, String pitch, long waitMs) {
        SynthesisConfig config = new SynthesisConfig(voice, rate, volume, pitch);
        String key = config.key(text);
        long deadline = android.os.SystemClock.elapsedRealtime() + Math.max(0, waitMs);
        String cancelRequestId = null;

        synchronized (LOCK) {
            while ((cachedAudio == null || !cachedAudio.key.equals(key))
                    && key.equals(activeKey)
                    && android.os.SystemClock.elapsedRealtime() < deadline) {
                long remaining = deadline - android.os.SystemClock.elapsedRealtime();
                try {
                    LOCK.wait(Math.max(1, remaining));
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }

            if (cachedAudio != null && cachedAudio.key.equals(key)) {
                byte[] pcm = cachedAudio.pcm;
                cachedAudio = null;
                LOCK.notifyAll();
                return pcm;
            }

            if (key.equals(activeKey)) {
                cancelRequestId = activeRequestId;
                if (activeFuture != null) activeFuture.cancel(true);
                activeFuture = null;
                activeRequestId = null;
                activeKey = null;
                LOCK.notifyAll();
            }
        }

        if (cancelRequestId != null) {
            EdgeTtsNative.stop(cancelRequestId);
        }
        return null;
    }

    static void cancelAll() {
        String requestId;
        synchronized (LOCK) {
            requestId = activeRequestId;
            if (activeFuture != null) activeFuture.cancel(true);
            activeFuture = null;
            activeRequestId = null;
            activeKey = null;
            cachedAudio = null;
            LOCK.notifyAll();
        }
        if (requestId != null) {
            EdgeTtsNative.stop(requestId);
        }
    }

    private static void synthesizeAhead(String key, String text, SynthesisConfig config) {
        String requestId = "prefetch-" + UUID.randomUUID();
        boolean registered = false;
        try {
            synchronized (LOCK) {
                if (!key.equals(activeKey)) return;
                activeRequestId = requestId;
                registered = true;
            }

            String beginError = EdgeTtsNative.beginSynthesis(
                    requestId, text, config.voice, config.rate, config.volume, config.pitch
            );
            if (beginError != null && !beginError.isEmpty()) {
                Log.w(TAG, "Background synthesis could not start: " + beginError);
                return;
            }

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            boolean tooLarge = false;
            while (!Thread.currentThread().isInterrupted()) {
                byte[] chunk = EdgeTtsNative.readPcmChunk(requestId, READ_CHUNK_BYTES);
                if (chunk == null || chunk.length == 0) break;
                if (output.size() + chunk.length > MAX_PCM_BYTES) {
                    tooLarge = true;
                    break;
                }
                output.write(chunk, 0, chunk.length);
            }

            if (tooLarge) {
                Log.i(TAG, "Prefetch discarded: audio exceeded memory limit");
                return;
            }

            String streamError = EdgeTtsNative.getLastError(requestId);
            byte[] pcm = output.toByteArray();
            if (streamError != null && !streamError.isEmpty()) {
                Log.w(TAG, "Background synthesis failed: " + streamError);
                return;
            }
            if (pcm.length == 0 || Thread.currentThread().isInterrupted()) return;

            synchronized (LOCK) {
                if (key.equals(activeKey) && !Thread.currentThread().isInterrupted()) {
                    cachedAudio = new CachedAudio(key, pcm);
                    Log.i(TAG, "Prefetched next utterance: " + pcm.length + " PCM bytes");
                }
            }
        } catch (Exception exception) {
            Log.w(TAG, "Background synthesis failed", exception);
        } finally {
            if (registered) EdgeTtsNative.stop(requestId);
            synchronized (LOCK) {
                if (key.equals(activeKey)) {
                    activeRequestId = null;
                    activeFuture = null;
                    activeKey = null;
                }
                LOCK.notifyAll();
            }
        }
    }
}
