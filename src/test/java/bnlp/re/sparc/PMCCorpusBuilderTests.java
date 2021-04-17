package bnlp.re.sparc;

import bnlp.common.CharSetEncoding;
import bnlp.common.index.NEInfo;
import bnlp.re.common.PhraseLookupManager;
import bnlp.re.common.Span;
import bnlp.re.common.Tokenizer;
import bnlp.re.util.FileUtils;
import org.junit.Assert;
import org.junit.Test;

import java.net.URISyntaxException;
import java.util.Arrays;

/**
 * Created by bozyurt on 10/23/20.
 */
public class PMCCorpusBuilderTests {


    @Test
    public void testHandle() throws Exception {
        String corpusDir = getPath("testdata/papers");
        String outIdxXmlFile = "/tmp/sparc_test_idx.xml";
        String HOME_DIR = System.getProperty("user.home");
        String vocabFile = HOME_DIR + "/dev/java/bnlp-re/data/sparc/sparc_vocabulary_combined.txt";

        String[] vocabulary = FileUtils.readLines(vocabFile, true, CharSetEncoding.UTF8);

        PhraseLookupManager plm = new PhraseLookupManager(Arrays.asList(vocabulary), true,
                new Tokenizer());

        PMCCorpusBuilder builder = new PMCCorpusBuilder(corpusDir, plm);

        builder.handle(outIdxXmlFile);
    }

    @Test
    public void testPLM() throws Exception {
        String HOME_DIR = System.getProperty("user.home");
        String vocabFile = HOME_DIR + "/dev/java/bnlp-re/data/sparc/sparc_vocabulary_combined.txt";

        String[] vocabulary = FileUtils.readLines(vocabFile, true, CharSetEncoding.UTF8);

        PhraseLookupManager plm = new PhraseLookupManager(Arrays.asList(vocabulary), true,
                new Tokenizer());
        Assert.assertTrue(plm.isInTrieFully("dorsal root ganglia"));

        String sentence = "The brain, spinal cord, and, in some experiments, dorsal root ganglia (DRG) L4 and L5 were removed, postfixed for 24 hours in 4% paraformaldehyde, allowed to sink in cryoprotectant (30% sucrose in phosphate buffer [PB]), and stored at 4°C.";
        int offset = 0;
        String rest = sentence;
        while (true) {
            Span span = plm.findLongestMatching(rest);
            if (span == null) {
                break;
            }
            int start = span.getStart() + offset;
            int end = span.getEnd() + offset;
            NEInfo nei = new NEInfo("structure", String.valueOf(start),
                    String.valueOf(end), "machine");
            offset += span.getEnd();
            System.out.println(nei.extractNE(sentence));
            if (offset < sentence.length()) {
                rest = sentence.substring(offset);
            } else {
                break;
            }
        }
    }

    public static String getPath(String resourcePath) throws URISyntaxException {
        return PMCCorpusBuilderTests.class.getClassLoader().getResource(resourcePath).toURI().getPath();
    }
}
