package top.initsnow.edge_tts_android;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Conservative Chinese speech-text normalization.
 *
 * The visible/source text is never changed. The normalized text is sent to
 * Edge TTS, while a UTF-16 offset map translates word boundaries back to the
 * original text so Reader highlighting remains aligned.
 */
final class SpeechTextNormalizer {
    private static final Pattern NUMBER = Pattern.compile("(?<![0-9.,])([0-9]{1,12})(\\s*[%％])?(?![0-9.,])");
    private static final String[] DE_PARTICLE_PHRASES = {
            "慢慢地", "认真地", "仔细地", "轻轻地", "渐渐地",
            "静静地", "悄悄地", "快速地", "缓缓地", "坚定地", "谨慎地",
            "小心地", "清楚地", "明确地", "深刻地", "平静地"
    };
    // Lexical-context overrides for common polyphonic words. Replacements use
    // same-pronunciation characters to guide Edge's grapheme-to-phoneme model;
    // the original display text and UTF-16 offsets remain unchanged.
    private static final String[][] POLYPHONE_PHRASES = {
            {"仔细", "子细"}, {"仔细地", "子细的"}, {"土著", "土住"},
            {"银行", "银航"}, {"行业", "航业"}, {"行情", "航情"},
            {"行列", "航列"}, {"行家", "航家"},
            {"行走", "形走"}, {"行人", "形人"}, {"行驶", "形驶"},
            {"行动", "形动"}, {"行为", "形为"}, {"行程", "形程"},
            {"行李", "形李"}, {"行政", "形政"}, {"行军", "形军"},
            {"行进", "形进"}, {"执行", "执形"}, {"进行", "进形"},
            {"实行", "实形"}, {"品行", "品形"}, {"德行", "德形"},
            {"重量", "众量"}, {"重要", "众要"}, {"重视", "众视"},
            {"重大", "众大"}, {"重点", "众点"}, {"重心", "众心"},
            {"重病", "众病"}, {"重伤", "众伤"},
            {"重新", "虫新"}, {"重复", "虫复"}, {"重来", "虫来"},
            {"重建", "虫建"}, {"重逢", "虫逢"}, {"重申", "虫申"},
            {"重阳", "虫阳"},
            {"长大", "掌大"}, {"长高", "掌高"}, {"长子", "掌子"},
            {"长女", "掌女"}, {"长辈", "掌辈"}, {"长官", "掌官"},
            {"长进", "掌进"},
            {"长短", "常短"}, {"长久", "常久"}, {"长远", "常远"},
            {"长处", "常处"}, {"长城", "常城"}, {"长江", "常江"},
            {"长篇", "常篇"},
            {"音乐", "月乐"}, {"乐器", "月器"}, {"乐曲", "月曲"},
            {"乐队", "月队"}, {"乐谱", "月谱"},
            {"快乐", "快勒"}, {"欢乐", "欢勒"}, {"乐于", "勒于"},
            {"乐意", "勒意"}, {"乐观", "勒观"}, {"乐趣", "勒趣"},
            {"乐此不疲", "勒此不疲"},
            {"还有", "孩有"}, {"还是", "孩是"}, {"还要", "孩要"},
            {"还在", "孩在"}, {"还没", "孩没"}, {"还会", "孩会"},
            {"归还", "归环"}, {"偿还", "偿环"}, {"还钱", "环钱"},
            {"还债", "环债"}, {"还书", "环书"}, {"还给", "环给"},
            {"还原", "环原"},
            {"少将", "绍将"}, {"少校", "绍校"}, {"少尉", "绍尉"}
    };
    private static final String[] DI_WORDS = {
            "新月沃地", "土地", "地方", "当地", "地区", "地域", "地理", "地名", "地面",
            "地上", "地下", "地形", "地貌", "地质", "地球", "地震", "地带",
            "地缘", "地铁", "地板", "地点", "地势", "地块", "耕地", "湿地",
            "盆地", "高地", "领地", "基地", "根据地", "殖民地", "飞地",
            "封地", "荒地", "平地", "洼地", "沃地"
    };

    private SpeechTextNormalizer() {}

    static Result normalize(String source) {
        if (source == null || source.isEmpty()) return new Result(source == null ? "" : source, new int[0], new int[0]);
        StringBuilder spoken = new StringBuilder(source.length() + 16);
        List<Integer> starts = new ArrayList<>();
        List<Integer> ends = new ArrayList<>();
        int cursor = 0;
        Matcher matcher = NUMBER.matcher(source);
        while (matcher.find()) {
            appendContextual地(source, cursor, matcher.start(), spoken, starts, ends);
            String digits = matcher.group(1);
            String replacement = matcher.group(2) != null
                    ? "百分之" + cardinal(digits)
                    : classifyNumber(source, matcher.start(), matcher.end(), digits);
            appendMapped(replacement, matcher.start(), matcher.end(), spoken, starts, ends);
            cursor = matcher.end();
        }
        appendContextual地(source, cursor, source.length(), spoken, starts, ends);

        int[] startMap = new int[starts.size()];
        int[] endMap = new int[ends.size()];
        for (int i = 0; i < starts.size(); i++) {
            startMap[i] = starts.get(i);
            endMap[i] = ends.get(i);
        }
        return new Result(spoken.toString(), startMap, endMap);
    }

    private static void appendContextual地(
            String source, int from, int to, StringBuilder out,
            List<Integer> starts, List<Integer> ends
    ) {
        int cursor = from;
        while (cursor < to) {
            String match = null;
            String replacement = null;

            // Fixed lexical items are matched longest-first, then grammatical
            // adverbial 地 is handled conservatively as 的.
            for (String word : DI_WORDS) {
                if (cursor + word.length() <= to && source.startsWith(word, cursor)
                        && (match == null || word.length() > match.length())) {
                    match = word;
                    replacement = word.startsWith("地")
                            ? "帝" + word.substring(1)
                            : word.substring(0, word.length() - 1) + "帝";
                }
            }
            for (String phrase : DE_PARTICLE_PHRASES) {
                if (cursor + phrase.length() <= to && source.startsWith(phrase, cursor)
                        && (match == null || phrase.length() > match.length())) {
                    match = phrase;
                    replacement = phrase.substring(0, phrase.length() - 1) + "的";
                }
            }
            for (String[] phrase : POLYPHONE_PHRASES) {
                if (cursor + phrase[0].length() <= to && source.startsWith(phrase[0], cursor)
                        && (match == null || phrase[0].length() >= match.length())) {
                    match = phrase[0];
                    replacement = phrase[1];
                }
            }
            if (match != null) {
                appendMapped(replacement, cursor, cursor + match.length(), out, starts, ends);
                cursor += match.length();
            } else {
                char c = source.charAt(cursor);
                out.append(c);
                starts.add(cursor);
                ends.add(cursor + 1);
                cursor++;
            }
        }
    }

    private static String classifyNumber(String source, int start, int end, String digits) {
        String before = source.substring(Math.max(0, start - 12), start);
        String after = source.substring(end, Math.min(source.length(), end + 12));

        // Explicit era/calendar-year contexts are read digit by digit.
        if (before.endsWith("公元前")) {
            return cardinal(digits);
        }
        if (before.endsWith("公元") || before.endsWith("西元")
                || before.endsWith("民国") || before.endsWith("民國")) {
            return digitByDigit(digits);
        }
        // Ordinals, identifiers, document numbers and numbered routes.
        if (before.endsWith("第") || before.endsWith("编号") || before.endsWith("編號")
                || before.endsWith("序号") || before.endsWith("序號")
                || after.startsWith("号") || after.startsWith("號")
                || after.startsWith("号文件") || after.startsWith("号楼")
                || after.startsWith("号線") || after.startsWith("号线")) {
            return digitByDigit(digits);
        }
        // Explicit duration cues take priority over a following 年.
        if (before.endsWith("不到") || before.endsWith("超过") || before.endsWith("超過")
                || before.endsWith("持续") || before.endsWith("持續") || before.endsWith("历时")
                || before.endsWith("歷時") || before.endsWith("长达") || before.endsWith("長達")
                || before.endsWith("共计") || before.endsWith("共計") || before.endsWith("累计")
                || before.endsWith("累計") || before.endsWith("花了") || before.endsWith("用时")
                || before.endsWith("用時") || before.endsWith("历经") || before.endsWith("歷經")) {
            return cardinal(digits);
        }
        // Date-like year references with explicit calendar cues.
        if (after.startsWith("年代") || after.startsWith("年初") || after.startsWith("年底")
                || after.startsWith("年末") || after.startsWith("年间") || after.startsWith("年間")) {
            return digitByDigit(digits);
        }
        if (after.startsWith("年") && (before.endsWith("在") || before.endsWith("于")
                || before.endsWith("於") || before.endsWith("从") || before.endsWith("從")
                || before.endsWith("自"))) {
            return digitByDigit(digits);
        }
        // Durations and quantities are cardinal numbers, including the user's
        // contrastive case: “不到2000年的时间内”.
        return cardinal(digits);
    }

    private static String digitByDigit(String digits) {
        final String[] names = {"零", "一", "二", "三", "四", "五", "六", "七", "八", "九"};
        StringBuilder out = new StringBuilder(digits.length() * 2);
        for (int i = 0; i < digits.length(); i++) out.append(names[digits.charAt(i) - '0']);
        return out.toString();
    }

    private static String cardinal(String digits) {
        long value;
        try {
            value = Long.parseLong(digits);
        } catch (NumberFormatException ignored) {
            return digits;
        }
        if (value == 0) return "零";
        String[] small = {"零", "一", "二", "三", "四", "五", "六", "七", "八", "九"};
        String[] units = {"", "十", "百", "千"};
        String[] big = {"", "万", "亿"};
        int[] groups = new int[3];
        int count = 0;
        while (value > 0 && count < groups.length) {
            groups[count++] = (int) (value % 10000);
            value /= 10000;
        }
        StringBuilder result = new StringBuilder();
        boolean emitted = false;
        boolean skippedGroup = false;
        for (int i = count - 1; i >= 0; i--) {
            int group = groups[i];
            if (group == 0) {
                if (emitted) skippedGroup = true;
                continue;
            }
            if (emitted && (skippedGroup || group < 1000)) result.append("零");
            result.append(sectionToChinese(group, small, units)).append(big[i]);
            emitted = true;
            skippedGroup = false;
        }
        String text = result.toString();
        if (text.startsWith("一十")) text = text.substring(1);
        return text.replace("二千", "两千")
                .replace("二百", "两百")
                .replace("二万", "两万")
                .replace("二亿", "两亿");
    }

    private static String sectionToChinese(int value, String[] digits, String[] units) {
        StringBuilder out = new StringBuilder();
        int unit = 0;
        boolean zero = false;
        while (value > 0) {
            int n = value % 10;
            if (n == 0) {
                if (out.length() > 0) zero = true;
            } else {
                if (zero) out.insert(0, "零");
                out.insert(0, digits[n] + units[unit]);
                zero = false;
            }
            value /= 10;
            unit++;
        }
        return out.toString();
    }

    private static void appendMapped(
            String value, int sourceStart, int sourceEnd, StringBuilder out,
            List<Integer> starts, List<Integer> ends
    ) {
        out.append(value);
        if (value.length() == sourceEnd - sourceStart) {
            // One-to-one replacements (e.g. 地 -> 帝 / 的) preserve precise
            // per-character highlighting positions.
            for (int i = 0; i < value.length(); i++) {
                starts.add(sourceStart + i);
                ends.add(sourceStart + i + 1);
            }
        } else {
            // Number verbalization can change length; map the spoken token back
            // to the complete original digit span.
            for (int i = 0; i < value.length(); i++) {
                starts.add(sourceStart);
                ends.add(sourceEnd);
            }
        }
    }

    static final class Result {
        private final String text;
        private final int[] originalStarts;
        private final int[] originalEnds;

        Result(String text, int[] originalStarts, int[] originalEnds) {
            this.text = text;
            this.originalStarts = originalStarts;
            this.originalEnds = originalEnds;
        }

        String getText() { return text; }

        int originalStart(int speechIndex) {
            if (originalStarts.length == 0) return 0;
            int index = Math.max(0, Math.min(speechIndex, originalStarts.length - 1));
            return originalStarts[index];
        }

        int originalEnd(int speechExclusiveEnd) {
            if (originalEnds.length == 0) return 0;
            int index = Math.max(0, Math.min(speechExclusiveEnd - 1, originalEnds.length - 1));
            return originalEnds[index];
        }
    }
}
