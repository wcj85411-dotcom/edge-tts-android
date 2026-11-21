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
 * Research-only pronunciation baseline.
 * Records segmentation and per-character pinyin for contrastive contexts.
 * It deliberately does not rewrite synthesis text or claim contextual
 * disambiguation is solved.
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
                "他是个仔细认真的人，做事从来不马虎。",
                "土地、地方、地道，和慢慢地、认真地，是不同用法。",
                "仔细、仔猪、仔鸡、牛仔、小牛仔、靓仔，各有不同语境。",
                "银行、行业、行走、行为、不行、可行、一行、行列、行家、进行。",
                "重量很重，重新称重；长短不同，长大以后路更长。",
                "音乐令人快乐；他喜欢听乐曲，也懂得欣赏音乐。",
                "困难并不难；他还要回来，把剩下的东西还给我。",
                "少数人少说话；处理此事时，办公室里有几处需要注意。",
                "看着书，想着事情，做完了以后就走了。"
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
