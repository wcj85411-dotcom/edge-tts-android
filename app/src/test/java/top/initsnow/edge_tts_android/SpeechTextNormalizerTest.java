package top.initsnow.edge_tts_android;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class SpeechTextNormalizerTest {
    @Test public void distinguishesGeographicalDiFromAdverbialDe() {
        assertEquals("并在不到两千年的时间内接受了这一批在新月沃帝形成的驯化动植物",
                SpeechTextNormalizer.normalize("并在不到2000年的时间内接受了这一批在新月沃地形成的驯化动植物").getText());
        assertEquals("他慢慢的走过土帝", SpeechTextNormalizer.normalize("他慢慢地走过土地").getText());
        assertEquals("沿海帝区的居民认真子细的核对资料",
                SpeechTextNormalizer.normalize("沿海地区的居民认真仔细地核对资料").getText());
        assertEquals("脚印留在帝上", SpeechTextNormalizer.normalize("脚印留在地上").getText());
    }

    @Test public void distinguishesCalendarYearsFromDurations() {
        assertEquals("公元二零零零年", SpeechTextNormalizer.normalize("公元2000年").getText());
        assertEquals("在二零零零年", SpeechTextNormalizer.normalize("在2000年").getText());
        assertEquals("二零零零年代", SpeechTextNormalizer.normalize("2000年代").getText());
        assertEquals("版本3.14保持不变", SpeechTextNormalizer.normalize("版本3.14保持不变").getText());
        assertEquals("不到两千年的时间", SpeechTextNormalizer.normalize("不到2000年的时间").getText());
        assertEquals("两千多年前", SpeechTextNormalizer.normalize("2000多年前").getText());
        assertEquals("第二零零零号文件", SpeechTextNormalizer.normalize("第2000号文件").getText());
    }


    @Test public void normalizesPolyphonicWordsByLexicalContext() {
        assertEquals("请子细检查", SpeechTextNormalizer.normalize("请仔细检查").getText());
        assertEquals("请子细的检查", SpeechTextNormalizer.normalize("请仔细地检查").getText());

        assertEquals("银航职员正在形走", SpeechTextNormalizer.normalize("银行职员正在行走").getText());
        assertEquals("这个航业的行情很好", SpeechTextNormalizer.normalize("这个行业的行情很好").getText());

        assertEquals("众量很大，需要虫新测量", SpeechTextNormalizer.normalize("重量很大，需要重新测量").getText());
        assertEquals("他掌大后，走过常远的路", SpeechTextNormalizer.normalize("他长大后，走过长远的路").getText());

        assertEquals("月乐和快勒", SpeechTextNormalizer.normalize("音乐和快乐").getText());
        assertEquals("孩有时间就把书环给图书馆", SpeechTextNormalizer.normalize("还有时间就把书还给图书馆").getText());
        assertEquals("这位绍将很年轻", SpeechTextNormalizer.normalize("这位少将很年轻").getText());
    }

    @Test public void polyphonicPhraseReplacementsPreserveOriginalOffsets() {
        SpeechTextNormalizer.Result result = SpeechTextNormalizer.normalize("请仔细检查");
        assertEquals("请子细检查", result.getText());
        assertEquals(1, result.originalStart(1));
        assertEquals(3, result.originalEnd(3));
    }

    @Test public void mapsChangedSpeechOffsetsBackToOriginalUtf16Offsets() {
        SpeechTextNormalizer.Result result = SpeechTextNormalizer.normalize("不到2000年的时间");
        assertEquals("不到两千年的时间", result.getText());
        int start = result.getText().indexOf("两千");
        assertEquals(2, result.originalStart(start));
        assertEquals(6, result.originalEnd(start + 2));
    }
}
