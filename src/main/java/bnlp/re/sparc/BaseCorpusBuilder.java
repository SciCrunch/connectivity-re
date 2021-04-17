package bnlp.re.sparc;

import bnlp.common.CharSetEncoding;
import bnlp.common.index.NEInfo;
import bnlp.common.index.SentenceInfo;
import bnlp.nlp.sbt.SentenceBoundaryClassifierFactory;
import bnlp.nlp.sbt.SentenceBoundaryDetector;
import bnlp.re.common.PhraseLookupManager;
import bnlp.re.common.Span;

import java.io.IOException;

/**
 * Created by bozyurt on 1/15/21.
 */
public class BaseCorpusBuilder {
    SentenceBoundaryDetector sbd;
    PhraseLookupManager plm;

    public BaseCorpusBuilder(PhraseLookupManager plm) {
        this.plm = plm;
        SentenceBoundaryDetector.Config config = new SentenceBoundaryDetector.Config();
        this.sbd = new SentenceBoundaryDetector(config,
                SentenceBoundaryClassifierFactory.SVM_CLASSIFIER,
                CharSetEncoding.UTF8);
    }

    void addNamedEntities(SentenceInfo si) throws IOException {
        String sentence = si.getText().getText().toLowerCase();
        String rest = sentence;
        int offset = 0;
        while (true) {
            Span span = plm.findLongestMatching(rest);
            if (span == null) {
                break;
            }
            int start = span.getStart() + offset;
            int end = span.getEnd() + offset;
            NEInfo nei = new NEInfo("structure", String.valueOf(start),
                    String.valueOf(end), "machine");
            si.addNEInfo(nei);
            offset += span.getEnd();
            if (offset < sentence.length()) {
                rest = sentence.substring(offset);
            } else {
                break;
            }
        }
        if (si.hasNamedEntities() && si.getNeList().size() < 2) {
            si.getNeList().clear();
        }
    }

    public boolean isSentenceEligible(String sentence) throws IOException {
        Span span = plm.findLongestMatching(sentence);
        if (span == null || span.getEnd() >= sentence.length()) {
            return false;
        }
        int idx = span.getEnd();
        String rest = sentence.substring(idx);
        span = plm.findLongestMatching(rest);
        return span != null;
    }
}
