package top.initsnow.edge_tts_android;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.json.JSONObject;

public final class VoiceInfo {
    private static final Locale CHINESE = Locale.SIMPLIFIED_CHINESE;
    private static final Map<String, String> SPEAKER_NAMES = createSpeakerNames();

    private final String name;
    private final String shortName;
    private final String localeTag;
    private final String gender;
    private final String friendlyName;
    private final Locale locale;
    private final String iso3Language;
    private final String iso3Country;
    private final String normalizedSearchText;

    public VoiceInfo(String name, String shortName, String localeTag, String gender, String friendlyName) {
        this.name = valueOrEmpty(name);
        this.shortName = valueOrEmpty(shortName);
        this.localeTag = valueOrEmpty(localeTag);
        this.gender = valueOrEmpty(gender);
        this.friendlyName = valueOrEmpty(friendlyName);
        this.locale = Locale.forLanguageTag(this.localeTag.replace('_', '-'));
        this.iso3Language = computeIso3Language(locale);
        this.iso3Country = computeIso3Country(locale);
        this.normalizedSearchText = String.join("\n", this.shortName, this.localeTag, this.name,
                this.friendlyName, this.gender).toLowerCase(Locale.US);
    }

    public static VoiceInfo fromJson(JSONObject object) {
        return new VoiceInfo(object.optString("Name"), object.optString("ShortName"),
                object.optString("Locale"), object.optString("Gender"), object.optString("FriendlyName"));
    }

    public String getName() { return name; }
    public String getShortName() { return shortName; }
    public String getLocaleTag() { return localeTag; }
    public boolean isChineseVoice() {
        return "zh".equalsIgnoreCase(locale.getLanguage());
    }
    public String getGender() { return gender; }
    public String getFriendlyName() { return friendlyName; }
    public Locale toLocale() { return locale; }

    public boolean matchesIso3(String language, String country) {
        boolean countryMatches = country == null || country.isEmpty();
        if (iso3Language.isEmpty() || !iso3Language.equalsIgnoreCase(language)) return false;
        if (!countryMatches) countryMatches = !iso3Country.isEmpty() && iso3Country.equalsIgnoreCase(country);
        return countryMatches;
    }

    public boolean matchesIso3Language(String language) {
        return !iso3Language.isEmpty() && iso3Language.equalsIgnoreCase(language);
    }

    // Human-readable Chinese label; keep the original ShortName separately as the engine identifier.
    public String getDisplayName() {
        String speaker = getLocalizedSpeakerName();
        String language = locale.getDisplayLanguage(CHINESE);
        String country = locale.getDisplayCountry(CHINESE);
        String localeName = language;
        if (!country.isEmpty()) localeName += "（" + country + "）";
        String localizedGender = "Female".equalsIgnoreCase(gender) ? "女声"
                : ("Male".equalsIgnoreCase(gender) ? "男声" : "声音");
        if (speaker.isEmpty()) return localeName + " · " + localizedGender;
        return speaker + " · " + localeName + " · " + localizedGender;
    }

    private String getLocalizedSpeakerName() {
        String prefix = localeTag.replace('_', '-');
        String speaker = shortName.startsWith(prefix + "-")
                ? shortName.substring(prefix.length() + 1) : shortName;
        String key = speaker.replace("Neural", "").replace("Multilingual", "").toLowerCase(Locale.US);
        String localized = SPEAKER_NAMES.get(key);
        return localized != null ? localized : speaker;
    }

    public boolean matchesQuery(String query) { return normalizedSearchText.contains(query); }

    private static Map<String, String> createSpeakerNames() {
        Map<String, String> names = new HashMap<>();
        names.put("hsiaochen", "晓臻");
        names.put("xiaoxiao", "晓晓"); names.put("yunxi", "云希");
        names.put("yunjian", "云健"); names.put("xiaoyi", "晓伊");
        names.put("xiaohan", "晓涵"); names.put("xiaomeng", "晓梦");
        names.put("xiaomo", "晓墨"); names.put("xiaorui", "晓睿");
        names.put("xiaoshuang", "晓双"); names.put("yunyang", "云扬");
        names.put("yunfeng", "云枫"); names.put("yunye", "云野");
        names.put("yunjie", "云杰"); names.put("xiaoxuan", "晓萱");
        names.put("xiaoni", "晓妮"); names.put("xiaobei", "晓北");
        names.put("xiaoqiu", "晓秋"); names.put("xiaochun", "晓春");
        names.put("yunxia", "云夏"); names.put("yunhao", "云皓");
        names.put("yunrui", "云睿"); names.put("yunze", "云泽");
        names.put("yunfan", "云帆"); names.put("xiaomei", "晓梅");
        names.put("xiaomiao", "晓妙"); names.put("xiaoqing", "晓清");
        names.put("jenny", "珍妮"); names.put("aria", "阿莉娅");
        names.put("guy", "盖伊"); names.put("davis", "戴维斯");
        names.put("tony", "托尼"); names.put("nancy", "南希");
        names.put("sara", "萨拉"); names.put("sonia", "索尼娅");
        names.put("ryan", "瑞安"); names.put("natasha", "娜塔莎");
        names.put("michelle", "米歇尔"); names.put("jane", "简");
        names.put("christopher", "克里斯托弗"); names.put("eric", "埃里克");
        names.put("ana", "安娜"); names.put("andrew", "安德鲁");
        names.put("ava", "艾娃"); names.put("brian", "布莱恩");
        names.put("emma", "艾玛"); names.put("olivia", "奥利维亚");
        names.put("thomas", "托马斯"); names.put("steffan", "斯蒂芬");
        return names;
    }

    private static String computeIso3Language(Locale locale) {
        try { return locale.getISO3Language(); } catch (RuntimeException ignored) { return ""; }
    }

    private static String computeIso3Country(Locale locale) {
        try { return locale.getISO3Country(); } catch (RuntimeException ignored) { return ""; }
    }

    private static String valueOrEmpty(String value) { return value == null ? "" : value; }
}
