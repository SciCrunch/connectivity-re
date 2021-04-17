package bnlp.re.sparc;

import bnlp.common.CharSetEncoding;
import bnlp.common.index.*;
import bnlp.util.GenUtils;
import org.jdom2.Comment;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by bozyurt on 9/22/20.
 */

@SuppressWarnings("Duplicates")
public class CleanupUtils {
    public final static String HOME_DIR = System.getProperty("user.home");

    public static boolean overlaps(int startIdx1, int endIdx1, int startIdx2, int endIdx2) {
        return (endIdx1 >= startIdx2 || endIdx1 >= endIdx2) && (endIdx2 >= startIdx1 || endIdx2 >= endIdx1);
    }

    public static void doSample2(String inIdxXmlFile, String outIdxXmlFile) throws Exception {
        FileInfo fi = new FileInfo(inIdxXmlFile, CharSetEncoding.UTF8);
        FileInfo newFI = new FileInfo("", "", "");
        List<DocumentInfo> filtered = new ArrayList<>();
        boolean found = false;
        for (DocumentInfo di : fi.getDiList()) {
            if (!found) {
                if (di.getPMID().equals("PMC4375658")) {
                    found = true;
                }
            }
            if (found) {
                if (di.getPMID().equals("PMC5372003")) {
                    break;
                }
                filtered.add(di);
            }
        }

        Random rnd = new Random(2726475L);
        Map<String, List<SampleLoc>> sampleMap = sampleRelations(filtered, rnd, 100);
        List<SampleLoc> sampleLocs = new ArrayList<>();
        sampleMap.values().forEach(sampleLocs::addAll);
        Collections.sort(sampleLocs);
        Iterator<SampleLoc> it = sampleLocs.iterator();
        SampleLoc curSL = it.next();
        boolean finished = false;
        for (DocumentInfo di : fi.getDiList()) {
            DocumentInfo ndi = null;
            for (SentenceInfo si : di.getSiList()) {
                if (curSL.getDocIdx() == di.getDocIdx() && curSL.getSentIdx() == si.getSentIdx()) {
                    if (ndi == null) {
                        ndi = new DocumentInfo(di.getDocIdx(), di.getPMID());
                        newFI.appendDocument(ndi);
                    }
                    SentenceInfo nsi = new SentenceInfo(si);
                    // addIEFrames(si, nsi);
                    ndi.addSentenceInfo(nsi);
                    if (it.hasNext()) {
                        curSL = it.next();
                    } else {
                        finished = true;
                        break;
                    }
                }
            }
            if (finished) {
                break;
            }
        }
        Comment comment = new Comment(
                GenUtils.prepCreatorComment(CleanupUtils.class
                        .getName()));
        newFI.saveAsXML(outIdxXmlFile, comment, CharSetEncoding.UTF8);
        System.out.println("saved " + outIdxXmlFile);
    }


    public static void doSimpleRandomSample(String inIdxXmlFile, String outIdxXmlFile,
                                     String excludeIdxXmlFile,
                                     int sampleSize) throws Exception {
        Set<String> excludeSet = prepExcludeSentenceSet(excludeIdxXmlFile);
        FileInfo fi = new FileInfo(inIdxXmlFile, CharSetEncoding.UTF8);
        FileInfo newFI = new FileInfo("", "", "");

        Random rnd = new Random(2726475L);
        Map<String, List<SampleLoc>> sampleMap = sample(fi.getDiList(), 1000000, rnd);
        List<SampleLoc> sampleLocs = new ArrayList<>();
        sampleMap.values().forEach(sampleLocs::addAll);
        Collections.shuffle(sampleLocs, rnd);
        sampleLocs = new ArrayList<>( sampleLocs.subList(0, 2 * sampleSize));
        Collections.sort(sampleLocs);
        Iterator<SampleLoc> it = sampleLocs.iterator();
        SampleLoc curSL = it.next();
        boolean finished = false;
        int count = 0;
        for (DocumentInfo di : fi.getDiList()) {
            DocumentInfo ndi = null;
            for (SentenceInfo si : di.getSiList()) {
                if (curSL.getDocIdx() == di.getDocIdx() && curSL.getSentIdx() == si.getSentIdx()) {
                    if (!excludeSet.isEmpty()) {
                        String sentence = si.getText().getText();
                        if (excludeSet.contains(sentence)) {
                            if (it.hasNext()) {
                                curSL = it.next();
                            }
                            continue;
                        }
                    }
                    if (ndi == null) {
                        ndi = new DocumentInfo(di.getDocIdx(), di.getPMID());
                        newFI.appendDocument(ndi);
                    }
                    SentenceInfo nsi = new SentenceInfo(si);
                    ndi.addSentenceInfo(nsi);
                    count++;
                    if (it.hasNext() && count < sampleSize) {
                        curSL = it.next();
                    } else {
                        finished = true;
                        break;
                    }
                }
            }
            if (finished) {
                break;
            }
        }
        Comment comment = new Comment(
                GenUtils.prepCreatorComment(CleanupUtils.class
                        .getName()));
        newFI.saveAsXML(outIdxXmlFile, comment, CharSetEncoding.UTF8);
        System.out.println("saved " + outIdxXmlFile);
    }


    public static Set<String> prepExcludeSentenceSet(String excludeIdxXmlFile) throws Exception {
        if (excludeIdxXmlFile == null) {
            return Collections.emptySet();
        }
        Set<String> excludeSet = new HashSet<>();
        FileInfo fi = new FileInfo(excludeIdxXmlFile, CharSetEncoding.UTF8);
        for(DocumentInfo di : fi.getDiList()) {
            for(SentenceInfo si : di.getSiList()) {
                String sentence = si.getText().getText();
                excludeSet.add(sentence);
            }
        }
        return excludeSet;
    }

    /**
     * samples equal number of sentences from each unique entity (oversampling from rare entities,
     * undersampling for abundant entities).
     *
     * @param inIdxXmlFile
     * @param outIdxXmlFile
     * @param maxPerEntity
     * @throws Exception
     */
    public static void doSample(String inIdxXmlFile, String outIdxXmlFile, int maxPerEntity) throws Exception {
        FileInfo fi = new FileInfo(inIdxXmlFile, CharSetEncoding.UTF8);
        FileInfo newFI = new FileInfo("", "", "");

        Random rnd = new Random(2726475L);
        Map<String, List<SampleLoc>> sampleMap = sample(fi.getDiList(), maxPerEntity, rnd);
        List<SampleLoc> sampleLocs = new ArrayList<>(maxPerEntity * sampleMap.size());
        sampleMap.values().forEach(sampleLocs::addAll);
        Collections.sort(sampleLocs);
        Iterator<SampleLoc> it = sampleLocs.iterator();
        SampleLoc curSL = it.next();
        boolean finished = false;
        for (DocumentInfo di : fi.getDiList()) {
            DocumentInfo ndi = null;
            for (SentenceInfo si : di.getSiList()) {
                if (curSL.getDocIdx() == di.getDocIdx() && curSL.getSentIdx() == si.getSentIdx()) {
                    if (ndi == null) {
                        ndi = new DocumentInfo(di.getDocIdx(), di.getPMID());
                        newFI.appendDocument(ndi);
                    }
                    SentenceInfo nsi = new SentenceInfo(si);
                    addIEFrames(si, nsi);
                    ndi.addSentenceInfo(nsi);
                    if (it.hasNext()) {
                        curSL = it.next();
                    } else {
                        finished = true;
                        break;
                    }
                }
            }
            if (finished) {
                break;
            }
        }
        Comment comment = new Comment(
                GenUtils.prepCreatorComment(CleanupUtils.class
                        .getName()));
        newFI.saveAsXML(outIdxXmlFile, comment, CharSetEncoding.UTF8);
        System.out.println("saved " + outIdxXmlFile);
    }

    static void addIEFrames(SentenceInfo si, SentenceInfo nsi) {
        if (si.getBaseFrameList() == null) {
            return;
        }
        for (BaseIEFrameInfo bfi : si.getBaseFrameList()) {
            BaseIEFrameInfo nbfi = new BaseIEFrameInfo(bfi.getType());
            for (BaseSlotInfo bsi : bfi.getSlots()) {
                BaseSlotInfo nbsi = new BaseSlotInfo(bsi.getType(), bsi.getStart(),
                        bsi.getEnd(), bsi.getEnd());
                nbfi.addSlot(nbsi);
            }
            nsi.addBaseIEFrameInfo(nbfi);
        }
    }

    public static void cleanBadNamedEntities(String inIdxXmlFile, String outIdxXmlFile) throws Exception {
        FileInfo fi = new FileInfo(inIdxXmlFile, CharSetEncoding.UTF8);
        FileInfo newFI = new FileInfo("", "", "");

        for (DocumentInfo di : fi.getDiList()) {
            DocumentInfo ndi = new DocumentInfo(di.getDocIdx(), di.getPMID());
            for (SentenceInfo si : di.getSiList()) {
                if (si.hasNamedEntities()) {
                    filterBadNamedEntities(si);
                    if (si.getNeList().size() > 1) {
                        SentenceInfo nsi = new SentenceInfo(si);
                        ndi.addSentenceInfo(nsi);
                    }
                }
            }
            if (!ndi.getSiList().isEmpty()) {
                newFI.appendDocument(ndi);
            }
        }
        Comment comment = new Comment(
                GenUtils.prepCreatorComment(CleanupUtils.class
                        .getName()));
        newFI.saveAsXML(outIdxXmlFile, comment, CharSetEncoding.UTF8);
        System.out.println("saved " + outIdxXmlFile);
    }

    public static void filterSameBinaryEntities(SentenceInfo si) {
        if (si.hasNamedEntities() && si.getNeList().size() == 2) {
            String sentence = si.getText().getText();
            String ne1 = si.getNeList().get(0).extractNE(sentence).toLowerCase();
            String ne2 = si.getNeList().get(1).extractNE(sentence).toLowerCase();
            if (ne1.equals(ne2)) {
                si.getNeList().clear();
            }
        }
    }

    public static void filterBadNamedEntities(SentenceInfo si) {
        String sentence = si.getText().getText();
        si.orderNamedEntities();
        for (Iterator<NEInfo> it = si.getNeList().iterator(); it.hasNext(); ) {
            NEInfo nei = it.next();
            if (!isAtWordBoundaries(nei, sentence)) {
                it.remove();
            }
        }
        if (si.getNeList().size() > 1) {
            Set<NEInfo> badSet = new HashSet<>();
            for (Iterator<NEInfo> it = si.getNeList().iterator(); it.hasNext(); ) {
                NEInfo nei = it.next();
                if (badSet.contains(nei)) {
                    continue;
                }
                Set<NEInfo> overlapSet = new HashSet<>();
                for (NEInfo n : si.getNeList()) {
                    if (n == nei) {
                        continue;
                    }
                    if (overlaps(nei.getStartIdx(), nei.getEndIdx(), n.getStartIdx(), n.getEndIdx())) {
                        overlapSet.add(n);
                    }
                }
                if (!overlapSet.isEmpty()) {
                    overlapSet.add(nei);
                    NEInfo longestNE = findLongest(overlapSet);
                    badSet.addAll(overlapSet.stream().filter(one -> one != longestNE).collect(Collectors.toList()));
                }
            }
            si.getNeList().removeAll(badSet);
        }
    }

    public static NEInfo findLongest(Set<NEInfo> neiSet) {
        int maxLen = -1;
        NEInfo longestNE = null;
        for (NEInfo nei : neiSet) {
            int len = nei.getEndIdx() - nei.getStartIdx();
            if (len > maxLen) {
                maxLen = len;
                longestNE = nei;
            }
        }

        return longestNE;
    }

    public static boolean isAtWordBoundaries(NEInfo nei, String sentence) {
        int startIdx = nei.getStartIdx();
        int endIdx = nei.getEndIdx();
        if (startIdx > 0) {
            char c = sentence.charAt(startIdx - 1);
            if (!Character.isWhitespace(c)) {
                return false;
            }
        }
        if (endIdx < sentence.length()) {
            char c = sentence.charAt(endIdx);
            if (!Character.isWhitespace(c) && c != '.' && c != ',' && c != ';') {
                return false;
            }
        }
        return true;
    }

    public static Map<String, List<SampleLoc>> sampleRelations(List<DocumentInfo> diList, Random rnd, int max) {
        Map<String, List<SampleLoc>> map = new HashMap<>();
        int count = 0;
        for (DocumentInfo di : diList) {
            for (SentenceInfo si : di.getSiList()) {
                if (si.hasNamedEntities() && si.getBaseFrameList() != null && !si.getBaseFrameList().isEmpty()) {
                    boolean connectivity = false;
                    for (BaseIEFrameInfo bfi : si.getBaseFrameList()) {
                        if (!bfi.getType().equals("no-relation")) {
                            connectivity = true;
                        }
                    }
                    String key = "no-relation";
                    if (connectivity) {
                        key = "connectivity";
                    }
                    List<SampleLoc> slList = map.get(key);
                    if (slList == null) {
                        slList = new ArrayList<>();
                        map.put(key, slList);
                    }
                    slList.add(new SampleLoc(di.getDocIdx(), si.getSentIdx()));
                    count++;
                }
            }
        }
        System.out.println("count:" + count);
        Map<String, List<SampleLoc>> sampleMap = new HashMap<>();
        int remaining;
        List<SampleLoc> list = map.get("connectivity");
        Collections.shuffle(list, rnd);
        int size = Math.min(max/2, list.size());
        List<SampleLoc> slList = new ArrayList<>(list.subList(0, size));
        Collections.sort(slList);
        sampleMap.put("connectivity", slList);
        remaining = max - size;
        list = map.get("no-relation");
        Collections.shuffle(list, rnd);
        size = Math.min(list.size(), remaining);
        slList = new ArrayList<>(list.subList(0, size));
        Collections.sort(slList);
        sampleMap.put("no-relation", slList);
        return sampleMap;
    }

    public static Map<String, List<SampleLoc>> sample(List<DocumentInfo> diList, int maxPerEntity, Random rnd) {
        Map<String, List<SampleLoc>> map = new HashMap<>();
        for (DocumentInfo di : diList) {
            for (SentenceInfo si : di.getSiList()) {
                if (si.hasNamedEntities()) {
                    String sentence = si.getText().getText();
                    boolean added = false;
                    for (NEInfo nei : si.getNeList()) {
                        String ne = nei.extractNE(sentence).toLowerCase();
                        List<SampleLoc> slList = map.get(ne);
                        if (slList == null) {
                            slList = new ArrayList<>();
                            map.put(ne, slList);
                        }
                        if (!added) {
                            slList.add(new SampleLoc(di.getDocIdx(), si.getSentIdx()));
                            added = true;
                        }
                    }
                }
            }
        }
        Map<String, List<SampleLoc>> sampleMap = new HashMap<>();
        for (String key : map.keySet()) {
            List<SampleLoc> list = map.get(key);
            Collections.shuffle(list, rnd);
            int size = Math.min(list.size(), maxPerEntity);
            List<SampleLoc> slList = new ArrayList<>(list.subList(0, size));
            Collections.sort(slList);
            sampleMap.put(key, slList);
        }
        return sampleMap;
    }

    public static class SampleLoc implements Comparable<SampleLoc> {
        final int docIdx;
        final int sentIdx;

        public SampleLoc(int docIdx, int sentIdx) {
            this.docIdx = docIdx;
            this.sentIdx = sentIdx;
        }

        public int getDocIdx() {
            return docIdx;
        }

        public int getSentIdx() {
            return sentIdx;
        }

        @Override
        public int compareTo(SampleLoc o) {
            int cmp = Integer.compare(docIdx, o.getDocIdx());
            if (cmp == 0) {
                return Integer.compare(sentIdx, o.getSentIdx());
            }
            return cmp;
        }
    }

    public static void sampleIter1() throws Exception {
        String inIdxXmlFile = HOME_DIR + "/dev/java/bnlp-re/sparc_connectivity_predicted_idx.xml";
        String outIdxXmlFile = "/tmp/sparc_connectivity_predicted_sampled_idx.xml";
        doSample(inIdxXmlFile, outIdxXmlFile, 10);

        inIdxXmlFile = HOME_DIR + "/dev/java/bnlp-re/sparc_connectivity_active_learning_idx.xml";
        outIdxXmlFile = "/tmp/sparc_connectivity_active_learning_sampled_idx.xml";
        doSample(inIdxXmlFile, outIdxXmlFile, 10);
    }

    public static void main(String[] args) throws Exception {
        testDriver();
        //sampleIter1();
    }

    private static void testDriver() throws Exception {
        String inIdxXmlFile = HOME_DIR + "/dev/java/bnlp-re/sparc_neg_examples_idx.xml";
        String outIdxXmlFile = "/tmp/sparc_neg_examples_idx_filtered.xml";
        // cleanBadNamedEntities(inIdxXmlFile, outIdxXmlFile);

        inIdxXmlFile = HOME_DIR + "/dev/java/bnlp-re/sparc_neg_examples_idx_full.xml";
        outIdxXmlFile = "/tmp/sparc_neg_examples_idx_sampled.xml";
        // doSample(inIdxXmlFile, outIdxXmlFile, 10);

        inIdxXmlFile = "/tmp/sparc_connectivity_nerve_ganglia_sampled_idx_joe2.xml";
        outIdxXmlFile = "/tmp/sparc_connectivity_nerve_ganglia_sampled_test_idx.xml";
        doSample2(inIdxXmlFile, outIdxXmlFile);
    }

}
