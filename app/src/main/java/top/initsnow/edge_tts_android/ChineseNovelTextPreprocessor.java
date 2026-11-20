package top.initsnow.edge_tts_android;

/**
 * Conservative Chinese novel text preparation.
 *
 * This stage deliberately preserves every spoken character. The current native
 * Edge bridge accepts plain text and escapes it before wrapping it in SSML, so
 * injecting raw <break> or <phoneme> elements here would make them be spoken
 * literally. SSML-aware native transport can be added as a separate stage.
 */
public final class ChineseNovelTextPreprocessor {
    private ChineseNovelTextPreprocessor() {
    }

    public static String prepare(String source) {
        if (source == null || source.isEmpty()) {
            return "";
        }

        String normalized = source
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .replace('\u00a0', ' ')
                .replace('\u3000', ' ');

        String[] paragraphs = normalized.split("\\n+", -1);
        StringBuilder result = new StringBuilder(normalized.length() + 16);
        for (String rawParagraph : paragraphs) {
            String paragraph = collapseHorizontalWhitespace(rawParagraph).trim();
            if (paragraph.isEmpty()) {
                continue;
            }
            if (result.length() > 0) {
                result.append(' ');
            }
            result.append(paragraph);
            if (!endsWithSentencePunctuation(paragraph)) {
                result.append('。');
            }
        }
        return result.toString();
    }

    private static String collapseHorizontalWhitespace(String text) {
        StringBuilder result = new StringBuilder(text.length());
        boolean pendingSpace = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == ' ' || c == '\t' || c == '\f') {
                pendingSpace = result.length() > 0;
            } else {
                if (pendingSpace && !isClosingPunctuation(c)) {
                    result.append(' ');
                }
                pendingSpace = false;
                result.append(c);
            }
        }
        return result.toString();
    }

    private static boolean endsWithSentencePunctuation(String text) {
        if (text.isEmpty()) {
            return true;
        }
        char c = text.charAt(text.length() - 1);
        return c == '。' || c == '！' || c == '？' || c == '!' || c == '?'
                || c == '…' || c == '”' || c == '’' || c == '"' || c == '\''
                || c == '》' || c == '）' || c == ')';
    }

    private static boolean isClosingPunctuation(char c) {
        return c == '，' || c == '。' || c == '！' || c == '？'
                || c == '；' || c == '：' || c == '、' || c == '…'
                || c == '”' || c == '’' || c == '"' || c == '\''
                || c == '）' || c == ')' || c == '》';
    }
}
