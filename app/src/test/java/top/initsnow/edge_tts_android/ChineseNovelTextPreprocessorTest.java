package top.initsnow.edge_tts_android;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ChineseNovelTextPreprocessorTest {
    @Test
    public void keepsExistingSentencePunctuationAndText() {
        assertEquals("他说：“你好！”", ChineseNovelTextPreprocessor.prepare("他说：“你好！”"));
    }

    @Test
    public void addsSentencePauseAtUnpunctuatedParagraphEnd() {
        assertEquals("夜深了。", ChineseNovelTextPreprocessor.prepare("夜深了"));
    }

    @Test
    public void separatesParagraphsWithoutDroppingTheirContent() {
        assertEquals("第一段。 第二段！", ChineseNovelTextPreprocessor.prepare("第一段\n\n第二段！"));
    }

    @Test
    public void normalizesFullWidthSpacesAndRepeatedHorizontalWhitespace() {
        assertEquals("甲 乙。", ChineseNovelTextPreprocessor.prepare("甲　 乙"));
    }

    @Test
    public void emptyInputStaysEmpty() {
        assertEquals("", ChineseNovelTextPreprocessor.prepare("  \n "));
    }
}
