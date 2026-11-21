package top.initsnow.edge_tts_android;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import com.hankcs.hanlp.HanLP;
import com.hankcs.hanlp.seg.common.Term;
import com.hankcs.hanlp.tokenizer.StandardTokenizer;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

/**
 * Research-only baseline. This deliberately does not rewrite synthesis text.
 * It records what phrase segmentation and phrase-aware pinyin conversion
 * can provide before we design a contextual disambiguation layer.
 */
public class HanLpPronunciationBaselineTest {

    @Test
    public void inspectPolyphoneContextsWithoutChangingSourceText() {
        List<String> samples = Arrays.asList(
                "这片土地的气候并不稳定，沿海地区的居民对此有着深刻的感受。",
                "他慢慢地走到门口，仔细地看了看地上的脚印。",
                "这家银行的业务范围很广，附近的行业也在发展。",
                "这样做可行吗？如果不行，就换一种方式。",
                "牛仔穿过街道，仔细检查了小牛仔的衣服。",
                "他是个仔细认真的人，做事从来不马虎。"
        );

        for (String sample : samples) {
            List<Term> terms = StandardTokenizer.segment(sample);
            assertNotNull("HanLP segmentation result", terms);
            assertFalse("Expected non-empty segmentation for: " + sample, terms.isEmpty());

            List<?> pinyin = HanLP.convertToPinyinList(sample);
            assertNotNull("HanLP pinyin result", pinyin);
            assertEquals("Pinyin output must align with source characters: " + sample,
                    sample.length(), pinyin.size());

            System.out.println("SOURCE: " + sample);
            System.out.println("SEGMENT: " + terms);
            System.out.println("PINYIN: " + pinyin);
        }
    }
}
