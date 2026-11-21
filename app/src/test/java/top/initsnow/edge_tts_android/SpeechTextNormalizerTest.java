package top.initsnow.edge_tts_android;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class SpeechTextNormalizerTest {
    @Test public void distinguishesGeographicalDiFromAdverbialDe() {
        assertEquals("并在不到两千年的时间内接受了这一批在新月沃帝形成的驯化动植物",
                SpeechTextNormalizer.normalize("并在不到2000年的时间内接受了这一批在新月沃地形成的驯化动植物").getText());
        assertEquals("他慢慢的走过土帝", SpeechTextNormalizer.normalize("他慢慢地走过土地").getText());
        assertEquals("沿海帝区的居民认真仔细的核对资料",
                SpeechTextNormalizer.normalize("沿海地区的居民认真仔细地核对资料").getText());
    }

    @Test public void distinguishesCalendarYearsFromDurations() {
        assertEquals("公元二零零零年", SpeechTextNormalizer.normalize("公元2000年").getText());
        assertEquals("在二零零零年", SpeechTextNormalizer.normalize("在2000年").getText());
        assertEquals("不到两千年的时间", SpeechTextNormalizer.normalize("不到2000年的时间").getText());
        assertEquals("两千多年前", SpeechTextNormalizer.normalize("2000多年前").getText());
        assertEquals("第二零零零号文件", SpeechTextNormalizer.normalize("第2000号文件").getText());
    }

    @Test public void mapsChangedSpeechOffsetsBackToOriginalUtf16Offsets() {
        SpeechTextNormalizer.Result result = SpeechTextNormalizer.normalize("不到2000年的时间");
        assertEquals("不到两千年的时间", result.getText());
        int start = result.getText().indexOf("两千");
        assertEquals(2, result.originalStart(start));
        assertEquals(6, result.originalEnd(start + 2));
    }
}
