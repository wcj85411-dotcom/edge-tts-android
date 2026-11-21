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
    private static final Pattern NUMBER = Pattern.compile("(?<![0-9])([0-9]{1,12})(?![0-9])");
    private static final String[] DE_PARTICLE_PHRASES = {
            "慢慢地", "慢慢地", "认真地", "仔细地", "轻轻地", "渐渐地",
            "静静地", "悄悄地", "快速地", "缓缓地", "坚定地", "谨慎地",
            "小心地", "清楚地", "明确地", "深刻地", "平静地"
    };
    private static final String[] DI_WORDS = {
            "新月沃地", "土地", "地方", "地区", "地域", "地理", "地名", "地面",
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
            String replacement = classifyNumber(source, matcher.start(), matcher.end(), digits);
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
                    replacement = word.substring(0, word.length() - 1) + "帝";
                }
            }
            for (String phrase : DE_PARTICLE_PHRASES) {
                if (cursor + phrase.length() <= to && source.startsWith(phrase, cursor)
                        && (match == null || phrase.length() > match.length())) {
                    match = phrase;
                    replacement = phrase.substring(0, phrase.length() - 1) + "的";
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
        if (before.endsWith("公元前") || before.endsWith("公元") || before.endsWith("西元")
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
        // Date-like year references with an explicit temporal preposition.
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
        for (int i = 0; i < value.length(); i++) {
            starts.add(sourceStart);
            ends.add(sourceEnd);
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
