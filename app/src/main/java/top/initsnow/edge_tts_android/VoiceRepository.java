package top.initsnow.edge_tts_android;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public final class VoiceRepository {
    private static volatile VoiceRepository instance;
    private static final String KEY_VOICE_CACHE = "voice_cache_json";

    private final Context applicationContext;
    private final SharedPreferences sharedPreferences;
    private final Object lock = new Object();
    private final Set<String> voiceFeatures;
    private List<VoiceInfo> cachedVoices = Collections.emptyList();
    private Map<String, VoiceInfo> cachedVoicesByShortName = Collections.emptyMap();

    private VoiceRepository(Context applicationContext) {
        this.applicationContext = applicationContext.getApplicationContext();
        this.sharedPreferences = PreferenceManager.getDefaultSharedPreferences(
                this.applicationContext
        );
        Set<String> features = new HashSet<>();
        features.add(android.speech.tts.TextToSpeech.Engine.KEY_FEATURE_NETWORK_SYNTHESIS);
        this.voiceFeatures = Collections.unmodifiableSet(features);
    }

    public static VoiceRepository getInstance(Context context) {
        if (instance == null) {
            synchronized (VoiceRepository.class) {
                if (instance == null) {
                    instance = new VoiceRepository(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    public List<VoiceInfo> loadVoices(boolean forceRefresh) throws IOException {
        synchronized (lock) {
            if (!forceRefresh && !cachedVoices.isEmpty()) {
                return cachedVoices;
            }
            if (!forceRefresh) {
                List<VoiceInfo> storedVoices = loadStoredVoices();
                if (!storedVoices.isEmpty()) {
                    updateCache(storedVoices);
                    return cachedVoices;
                }
            }
            if (!EdgeTtsNative.isReady()) {
                throw new IOException(applicationContext.getString(
                        R.string.native_library_missing,
                        EdgeTtsNative.getLoadError()
                ));
            }
            String json = null;
            IOException fetchError = null;
            // A reset connection can be transient (especially on VPN/mobile routes).
            // Retry briefly, then keep the last known-good Chinese voice list if available.
            for (int attempt = 0; attempt < 3; attempt++) {
                try {
                    String response = EdgeTtsNative.listVoicesJson();
                    if (response.startsWith("ERROR:")) {
                        throw new IOException(response.substring("ERROR:".length()).trim());
                    }
                    json = response;
                    fetchError = null;
                    break;
                } catch (IOException exception) {
                    fetchError = exception;
                    if (attempt < 2) {
                        try {
                            Thread.sleep(attempt == 0 ? 300L : 700L);
                        } catch (InterruptedException interrupted) {
                            Thread.currentThread().interrupt();
                            throw new IOException("voice list request interrupted", interrupted);
                        }
                    }
                }
            }

            if (json == null) {
                List<VoiceInfo> storedVoices = loadStoredVoices();
                if (!storedVoices.isEmpty()) {
                    updateCache(storedVoices);
                    return cachedVoices;
                }
                throw fetchError != null ? fetchError : new IOException("voice list request failed");
            }

            try {
                JSONArray array = new JSONArray(json);
                List<VoiceInfo> voices = new ArrayList<>(array.length());
                for (int index = 0; index < array.length(); index++) {
                    JSONObject object = array.getJSONObject(index);
                    VoiceInfo voice = VoiceInfo.fromJson(object);
                    if (voice.isChineseVoice()) voices.add(voice);
                }
                voices.sort(Comparator
                        .comparing(VoiceInfo::getLocaleTag)
                        .thenComparing(VoiceInfo::getShortName));
                updateCache(voices);
                sharedPreferences.edit().putString(KEY_VOICE_CACHE, json).apply();
                return cachedVoices;
            } catch (JSONException exception) {
                throw new IOException("failed to parse voice list", exception);
            }
        }
    }

    public List<VoiceInfo> getCachedVoices() {
        synchronized (lock) {
            return cachedVoices;
        }
    }

    public VoiceInfo findByShortName(List<VoiceInfo> voices, String shortName) {
        if (voices == cachedVoices) {
            return cachedVoicesByShortName.get(shortName);
        }
        for (VoiceInfo voice : voices) {
            if (voice.getShortName().equals(shortName)) {
                return voice;
            }
        }
        return null;
    }

    public VoiceInfo findBestVoice(
            List<VoiceInfo> voices,
            String preferredShortName,
            String iso3Language,
            String iso3Country
    ) {
        if (preferredShortName != null && !preferredShortName.isEmpty()) {
            VoiceInfo preferred = findByShortName(voices, preferredShortName);
            if (preferred != null) {
                return preferred;
            }
        }
        for (VoiceInfo voice : voices) {
            if (voice.matchesIso3(iso3Language, iso3Country)) {
                return voice;
            }
        }
        for (VoiceInfo voice : voices) {
            if (voice.matchesIso3Language(iso3Language)) {
                return voice;
            }
        }
        return voices.isEmpty() ? null : voices.get(0);
    }

    public int getLanguageStatus(
            List<VoiceInfo> voices,
            String iso3Language,
            String iso3Country,
            String variant
    ) {
        boolean languageMatch = false;
        boolean countryMatch = false;
        boolean variantMatch = variant == null || variant.isEmpty();
        for (VoiceInfo voice : voices) {
            if (voice.matchesIso3Language(iso3Language)) {
                languageMatch = true;
                Locale locale = voice.toLocale();
                if (iso3Country == null || iso3Country.isEmpty()
                        || voice.matchesIso3(iso3Language, iso3Country)) {
                    countryMatch = true;
                    if (variant == null || variant.isEmpty()
                            || locale.getVariant().equalsIgnoreCase(variant)) {
                        variantMatch = true;
                    }
                }
            }
        }
        if (languageMatch && countryMatch && variantMatch) {
            return android.speech.tts.TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE;
        }
        if (languageMatch && countryMatch) {
            return android.speech.tts.TextToSpeech.LANG_COUNTRY_AVAILABLE;
        }
        if (languageMatch) {
            return android.speech.tts.TextToSpeech.LANG_AVAILABLE;
        }
        return android.speech.tts.TextToSpeech.LANG_NOT_SUPPORTED;
    }

    public Set<String> buildVoiceFeatures() {
        return voiceFeatures;
    }

    private List<VoiceInfo> loadStoredVoices() throws IOException {
        String json = sharedPreferences.getString(KEY_VOICE_CACHE, "");
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            JSONArray array = new JSONArray(json);
            List<VoiceInfo> voices = new ArrayList<>(array.length());
            for (int index = 0; index < array.length(); index++) {
                VoiceInfo voice = VoiceInfo.fromJson(array.getJSONObject(index));
                if (voice.isChineseVoice()) voices.add(voice);
            }
            voices.sort(Comparator
                    .comparing(VoiceInfo::getLocaleTag)
                    .thenComparing(VoiceInfo::getShortName));
            return voices;
        } catch (JSONException exception) {
            throw new IOException("failed to parse cached voice list", exception);
        }
    }

    private void updateCache(List<VoiceInfo> voices) {
        cachedVoices = Collections.unmodifiableList(new ArrayList<>(voices));
        Map<String, VoiceInfo> voicesByShortName = new HashMap<>(cachedVoices.size());
        for (VoiceInfo voice : cachedVoices) {
            voicesByShortName.put(voice.getShortName(), voice);
        }
        cachedVoicesByShortName = Collections.unmodifiableMap(voicesByShortName);
    }
}
