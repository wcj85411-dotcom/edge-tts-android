package top.initsnow.edge_tts_android;

import android.util.Log;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Streams one upcoming utterance ahead of playback.
 *
 * Unlike the old implementation, this does not wait for the whole paragraph
 * to be synthesized before making audio available. A bounded queue provides
 * back-pressure and caps queued PCM at approximately 3 MiB per utterance.
 */
final class PrefetchManager {
    private static final String TAG = "EdgeTtsPrefetch";
    private static final int READ_CHUNK_BYTES = 32 * 1024;
    private static final int MAX_QUEUED_CHUNKS = 96;
    private static final int MAX_PENDING_REQUESTS = 2;
    private static final Object LOCK = new Object();
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "EdgeTts-Prefetch");
        thread.setDaemon(true);
        return thread;
    });

    private static final Map<String, PrefetchStream> PENDING = new LinkedHashMap<>();
    private static final Set<PrefetchStream> ALL_STREAMS = new LinkedHashSet<>();
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

    private static final class AudioChunk {
        static final AudioChunk END = new AudioChunk(null, null);
        final byte[] pcm;
        final String error;

        AudioChunk(byte[] pcm, String error) {
            this.pcm = pcm;
            this.error = error;
        }
    }

    static final class PrefetchStream {
        private final String key;
        private final ArrayBlockingQueue<AudioChunk> queue =
                new ArrayBlockingQueue<>(MAX_QUEUED_CHUNKS);
        private final AtomicBoolean cancelled = new AtomicBoolean(false);
        private final AtomicBoolean terminalQueued = new AtomicBoolean(false);
        private volatile Future<?> future;
        private volatile String requestId;

        private PrefetchStream(String key) {
            this.key = key;
        }

        private boolean offer(byte[] pcm) {
            if (cancelled.get()) return false;
            try {
                queue.put(new AudioChunk(pcm, null));
                return !cancelled.get();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return false;
            }
        }

        private void finish(String error) {
            if (!terminalQueued.compareAndSet(false, true)) return;
            try {
                queue.put(error == null || error.isEmpty()
                        ? AudioChunk.END
                        : new AudioChunk(null, error));
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                queue.clear();
                queue.offer(AudioChunk.END);
            }
        }

        byte[] readNext() throws IOException {
            final AudioChunk item;
            try {
                item = queue.take();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while waiting for prefetched audio", interrupted);
            }
            if (item == AudioChunk.END) {
                release(this);
                return null;
            }
            if (item.error != null) {
                release(this);
                throw new IOException(item.error);
            }
            return item.pcm;
        }

        void cancel() {
            if (!cancelled.compareAndSet(false, true)) return;
            Future<?> task = future;
            if (task != null) task.cancel(true);
            String id = requestId;
            if (id != null) EdgeTtsNative.stop(id);
            queue.clear();
            queue.offer(AudioChunk.END);
            release(this);
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
        String key = config.key(text);
        synchronized (LOCK) {
            if (PENDING.containsKey(key)) return true;
            if (PENDING.size() >= MAX_PENDING_REQUESTS) return false;
            PrefetchStream stream = new PrefetchStream(key);
            PENDING.put(key, stream);
            ALL_STREAMS.add(stream);
            stream.future = EXECUTOR.submit(() -> synthesizeAhead(stream, text, config));
            Log.i(TAG, "Queued streaming prefetch chars=" + text.length()
                    + " pending=" + PENDING.size());
            return true;
        }
    }

    static PrefetchStream take(String text, String voice, String rate, String volume, String pitch) {
        String key = new SynthesisConfig(voice, rate, volume, pitch).key(text);
        synchronized (LOCK) {
            PrefetchStream stream = PENDING.remove(key);
            if (stream != null) {
                Log.i(TAG, "Streaming prefetch claimed");
            } else {
                Log.i(TAG, "Streaming prefetch miss");
            }
            return stream;
        }
    }

    static void cancelAll() {
        ArrayList<PrefetchStream> streams;
        synchronized (LOCK) {
            streams = new ArrayList<>(ALL_STREAMS);
            PENDING.clear();
        }
        for (PrefetchStream stream : streams) {
            stream.cancel();
        }
        Log.i(TAG, "Cancelled all prefetch streams count=" + streams.size());
    }

    private static void release(PrefetchStream stream) {
        synchronized (LOCK) {
            PENDING.remove(stream.key, stream);
            ALL_STREAMS.remove(stream);
        }
    }

    private static void synthesizeAhead(
            PrefetchStream stream,
            String text,
            SynthesisConfig config
    ) {
        String requestId = "prefetch-" + UUID.randomUUID();
        stream.requestId = requestId;
        long startedAt = android.os.SystemClock.elapsedRealtime();
        int totalBytes = 0;
        try {
            String beginError = EdgeTtsNative.beginSynthesis(
                    requestId, text, config.voice, config.rate, config.volume, config.pitch
            );
            if (beginError != null && !beginError.isEmpty()) {
                stream.finish(beginError);
                return;
            }

            while (!Thread.currentThread().isInterrupted() && !stream.cancelled.get()) {
                byte[] chunk = EdgeTtsNative.readPcmChunk(requestId, READ_CHUNK_BYTES);
                if (chunk == null || chunk.length == 0) break;
                if (!stream.offer(chunk)) return;
                totalBytes += chunk.length;
            }

            if (stream.cancelled.get() || Thread.currentThread().isInterrupted()) return;
            String streamError = EdgeTtsNative.getLastError(requestId);
            stream.finish(streamError);
            Log.i(TAG, "Prefetch stream finished bytes=" + totalBytes
                    + " elapsedMs="
                    + (android.os.SystemClock.elapsedRealtime() - startedAt));
        } catch (Exception exception) {
            Log.w(TAG, "Background streaming synthesis failed", exception);
            stream.finish(exception.getMessage() == null
                    ? exception.getClass().getSimpleName()
                    : exception.getMessage());
        } finally {
            EdgeTtsNative.stop(requestId);
            stream.requestId = null;
        }
    }
}
