package bnlp.re.sparc;

import bnlp.common.CharSetEncoding;
import bnlp.common.index.DocumentInfo;
import bnlp.common.index.FileInfo;
import bnlp.common.index.NEInfo;
import bnlp.common.index.SentenceInfo;
import bnlp.re.common.FrequencyTable;

/**
 * Created by bozyurt on 10/23/20.
 */
public class SanityCheckUtils {

    public static void dumpEntityStats(String idxXMLFile) throws Exception {
        FrequencyTable<String> ft = new FrequencyTable<>();
        FileInfo fi = new FileInfo(idxXMLFile, CharSetEncoding.UTF8);
        for(DocumentInfo di : fi.getDiList()) {
            for(SentenceInfo si : di.getSiList()) {
                if (si.hasNamedEntities()) {
                    String sentence = si.getText().getText();
                    for(NEInfo nei : si.getNeList()) {
                        String entity = nei.extractNE(sentence).toLowerCase();
                        ft.addValue(entity);
                    }
                }
            }
        }
        ft.dumpSortedByFreq();
    }

    public static void main(String[] args) throws Exception {
        dumpEntityStats("/tmp/sparc_test_idx.xml");
    }
}
