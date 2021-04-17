package bnlp.re.sparc;

import bnlp.common.CharSetEncoding;
import bnlp.common.index.*;
import bnlp.re.common.FrequencyTable;
import bnlp.re.common.PhraseLookupManager;
import bnlp.re.common.Span;
import bnlp.re.common.Tokenizer;
import bnlp.re.util.FileUtils;
import bnlp.util.GenUtils;
import org.jdom2.Comment;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static bnlp.re.sparc.CleanupUtils.overlaps;

/**
 * Created by bozyurt on 1/4/21.
 */
public class NerveGangliaFilter {
    PhraseLookupManager plm;
    FrequencyTable<String> structureTable = new FrequencyTable<>();

    public NerveGangliaFilter(PhraseLookupManager plm) {
        this.plm = plm;
    }


    public boolean isEligible(SentenceInfo si) throws IOException {
        String sentence = si.getText().getText();
        for (NEInfo nei : si.getNeList()) {
            String ne = nei.extractNE(sentence);
            Span span = plm.findLongestMatching(ne);
            if (span != null) {
                return true;
            }
        }
        return false;
    }

    public boolean isEligible2(SentenceInfo si) throws IOException {
        String sentence = si.getText().getText();
        if (sentence.length() >= 300 || sentence.indexOf("Table ") != -1) {
            return false;
        }
        Span span = plm.findLongestMatching(sentence);
        if (span != null) {
            String phrase = sentence.substring(span.getStart(), span.getEnd());
            System.out.println(phrase);
            structureTable.addValue(phrase);
            boolean changed = false;
            if (si.hasNamedEntities()) {
                boolean found = false;
                NEInfo overlappingNE = null;
                int s1 = span.getStart();
                int e1 = span.getEnd();
                Set<NEInfo> toBeRemoved = new HashSet<>();
                for (NEInfo nei : si.getNeList()) {
                    String ne = nei.extractNE(sentence);
                    if (nei.getStartIdx() == span.getStart() && nei.getEndIdx() == span.getEnd()) {
                        found = true;
                        continue;
                    }
                    if (ne.equalsIgnoreCase("nerve") || ne.equalsIgnoreCase("nerves") ||
                            ne.equalsIgnoreCase("artery") || ne.equalsIgnoreCase("vein")
                            || ne.equalsIgnoreCase("veins") || ne.equalsIgnoreCase("arteries")) {
                        toBeRemoved.add(nei);
                    }
                    int s2 = nei.getStartIdx();
                    int e2 = nei.getEndIdx();
                    if (overlaps(s1, e1, s2, e2) || overlaps(s2, e2, s1, e1)) {
                        overlappingNE = nei;
                    }
                }
                if (!toBeRemoved.isEmpty()) {
                    si.getNeList().removeAll(toBeRemoved);
                    changed = true;
                }
                if (!found) {
                    if (overlappingNE != null) {
                        si.getNeList().remove(overlappingNE);
                    }
                    NEInfo nn = new NEInfo("structure", String.valueOf(span.getStart()), String.valueOf(span.getEnd()),
                            "machine");
                    si.addNEInfo(nn);
                    si.orderNamedEntities();
                    changed = true;
                }
            } else {

                NEInfo nn = new NEInfo("structure", String.valueOf(span.getStart()), String.valueOf(span.getEnd()),
                        "machine");
                si.addNEInfo(nn);
                si.orderNamedEntities();
                changed = true;
            }
            if (si.getNeList().size() < 2) {
                return false;
            }
            return true;
        }
        return false;
    }

    public void showStats() {
        structureTable.dumpSortedByFreq();
    }


    public static void copyIEFrames(SentenceInfo from, SentenceInfo to) {
        if (from.getBaseFrameList() == null) {
            return;
        }
        for (BaseIEFrameInfo bfi : from.getBaseFrameList()) {
            BaseIEFrameInfo nbfi = new BaseIEFrameInfo(bfi.getType());
            for (BaseSlotInfo bsi : bfi.getSlots()) {
                BaseSlotInfo nbsi = new BaseSlotInfo(bsi.getType(), bsi.getStart(), bsi.getEnd(), bsi.getStatus());
                nbfi.addSlot(nbsi);
            }
            to.addBaseIEFrameInfo(nbfi);
        }
    }

    public static void applyFilter(String idxXmlFile, String outIdxXmlFile, NerveGangliaFilter filter) throws Exception {
        FileInfo fi = new FileInfo(idxXmlFile, CharSetEncoding.UTF8);
        FileInfo outFI = new FileInfo("", "", "");
        for (DocumentInfo di : fi.getDiList()) {
            DocumentInfo ndi = null;
            for (SentenceInfo si : di.getSiList()) {
                if (filter.isEligible2(si)) {
                    if (ndi == null) {
                        ndi = new DocumentInfo(di.getDocIdx(), "PMC" + di.getPMID());
                        outFI.appendDocument(ndi);
                    }
                    SentenceInfo nsi = new SentenceInfo(si, ndi.getDocIdx());
                    // copyIEFrames(si, nsi);
                    ndi.addSentenceInfo(nsi);
                }
            }
        }
        Comment comment = new Comment(
                GenUtils.prepCreatorComment(NerveGangliaFilter.class
                        .getName()));
        outFI.saveAsXML(outIdxXmlFile, comment, CharSetEncoding.UTF8);
        System.out.println("saved " + outIdxXmlFile);
    }


    public static void main(String[] args) throws Exception {
        String HOME_DIR = System.getProperty("user.home");
        String vocabFile = HOME_DIR + "/dev/java/bnlp-re/scripts/nerve_ganglia_vocab.txt";
        String[] vocabulary = FileUtils.readLines(vocabFile, true, CharSetEncoding.UTF8);
        PhraseLookupManager plm = new PhraseLookupManager(Arrays.asList(vocabulary), true,
                new Tokenizer());
        NerveGangliaFilter filter = new NerveGangliaFilter(plm);


        String inIdxXmlFile = "/tmp/sparc_connectivity_predicted_idx.xml";
        inIdxXmlFile = HOME_DIR + "/dev/java/bnlp-re/sparc_combined_vocab_idx_11_06_2020.xml";
        String outIdxXmlFile = "/tmp/sparc_connectivity_nerve_ganglia_idx.xml";
      //  applyFilter(inIdxXmlFile, outIdxXmlFile, filter);
      //  filter.showStats();
        String excludeIdxXmlFile = HOME_DIR + "/dev/java/bnlp-re/data/sparc/base/sparc_connectivity_nerve_ganglia_sampled_idx_joe_full.xml";
        String randomSampleIdxXmlFile = "/tmp/sparc_connectivity_nerve_ganglia_random_set_idx.xml";
        CleanupUtils.doSimpleRandomSample(outIdxXmlFile, randomSampleIdxXmlFile, excludeIdxXmlFile, 250);

        // CleanupUtils.doSample(outIdxXmlFile, "/tmp/sparc_connectivity_nerve_ganglia_sampled_idx.xml", 3);

    }
}
