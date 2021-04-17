package bnlp.re.sparc;

import bnlp.common.CharSetEncoding;
import bnlp.common.index.*;
import bnlp.re.util.Assertion;
import bnlp.re.util.FileUtils;
import bnlp.re.util.SimpleSequentialIDGenerator;
import bnlp.util.GenUtils;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.jdom2.Comment;

import java.io.BufferedReader;
import java.util.*;

/**
 * Created by bozyurt on 9/18/20.
 */
public class CSVDataExtractor {

    public static List<ConnectivityAnnotationRecord> loadCSV(String csvFile) throws Exception {
        List<ConnectivityAnnotationRecord> carList = new ArrayList<>();
        BufferedReader in = null;
        try {
            in = FileUtils.newUTF8CharSetReader(csvFile);
            Iterable<CSVRecord> records = CSVFormat.RFC4180.parse(in);
            boolean first = true;
            for (CSVRecord record : records) {
                if (first) {
                    first = false;
                    continue;
                }
                carList.add(ConnectivityAnnotationRecord.fromCSV(record));
            }
        } finally {
            FileUtils.close(in);
        }
        return carList;
    }

    public static FileInfo toIdxXmlFormat(List<ConnectivityAnnotationRecord> carList,
                                          SimpleSequentialIDGenerator idGen) {
        FileInfo fi = new FileInfo("", "", "");
        Map<String, DocumentInfo> pmid2DIMap = new HashMap<>();
        Map<String, SimpleSequentialIDGenerator> pmid2IdGenMap = new HashMap<>();
        for (ConnectivityAnnotationRecord car : carList) {
            String pmid = car.getPmid().replaceFirst("PMID:\\s+", "");
            DocumentInfo di = pmid2DIMap.get(pmid);
            if (di == null) {
                int docIdx = idGen.nextID();
                di = new DocumentInfo(docIdx, pmid);
                pmid2DIMap.put(pmid, di);
                fi.appendDocument(di);
                pmid2IdGenMap.put(pmid, new SimpleSequentialIDGenerator());
            }
            int sentIdx = pmid2IdGenMap.get(pmid).nextID();
            TextInfo ti = new TextInfo(car.getClaim(), sentIdx);
            TextInfo pt = new TextInfo("", sentIdx);
            SentenceInfo si = new SentenceInfo(di.getDocIdx(), sentIdx, ti, pt);
            String sentence = car.getClaim();
            if (GenUtils.isEmpty(car.getStructure1()) || GenUtils.isEmpty(car.getStructure2())) {
                continue;
            }

            NEInfo nei1 = createNeInfo(car.getStructure1(), sentence);
            if (nei1 == null) {
                continue;
            }
            si.addNEInfo(nei1);
            NEInfo nei2 = createNeInfo(car.getStructure2(), sentence);
            if (nei2 == null) {
                continue;
            }
            si.addNEInfo(nei2);
            BaseIEFrameInfo bfi = new BaseIEFrameInfo("anatomical-connectivity");
            si.addBaseIEFrameInfo(bfi);
            BaseSlotInfo bsi = new BaseSlotInfo("structure", nei1.getStart(), nei1.getEnd(), "machine");
            bfi.addSlot(bsi);
            bsi = new BaseSlotInfo("structure", nei2.getStart(), nei2.getEnd(), "machine");
            bfi.addSlot(bsi);
            di.addSentenceInfo(si);
        }
        return fi;
    }

    public static void combine(String negIdxXmlFile, List<DocumentInfo> diList, String outIdxXmlFile,
                               boolean collapseRelationTypes) throws Exception {
        FileInfo nfi = new FileInfo("", "", "");
        FileInfo fi = new FileInfo(negIdxXmlFile, CharSetEncoding.UTF8);
        Map<String, DocumentInfo> pmid2DiMap = new HashMap<>();
        for (Iterator<DocumentInfo> it = diList.iterator(); it.hasNext(); ) {
            DocumentInfo di = it.next();
            if (di.getSiList() == null || di.getSiList().isEmpty()) {
                it.remove();
            } else {
                pmid2DiMap.put(di.getPMID(), di);
            }
        }
        String collapsedType = null;
        if (collapseRelationTypes) {
            collapsedType = "connectivity";
        }
        int maxDocIdx = -1;
        for (DocumentInfo di : fi.getDiList()) {
            Assertion.assertTrue(!pmid2DiMap.containsKey(di.getPMID()));
            if (maxDocIdx < di.getDocIdx()) {
                maxDocIdx = di.getDocIdx();
            }
            DocumentInfo ndi = new DocumentInfo(di.getDocIdx(), di.getPMID());
            for (SentenceInfo si : di.getSiList()) {
                if (si.getBaseFrameList() == null || si.getBaseFrameList().isEmpty()) {
                    continue;
                }
                SentenceInfo nsi = new SentenceInfo(si);
                addBaseFrames(si, nsi, collapsedType);
                ndi.addSentenceInfo(nsi);
            }
            nfi.appendDocument(ndi);
        }

        int docIdx = maxDocIdx++;

        for (DocumentInfo di : diList) {
            DocumentInfo ndi = new DocumentInfo(docIdx, di.getPMID());
            for (SentenceInfo si : di.getSiList()) {
                SentenceInfo nsi = new SentenceInfo(si, docIdx);
                addBaseFrames(si, nsi, collapsedType);
                ndi.addSentenceInfo(nsi);
            }
            nfi.appendDocument(ndi);
            docIdx++;
        }

        Comment comment = new Comment(
                GenUtils.prepCreatorComment(CSVDataExtractor.class
                        .getName()));
        nfi.saveAsXML(outIdxXmlFile, comment, CharSetEncoding.UTF8);
        System.out.println("saved " + outIdxXmlFile);
    }

    private static void addBaseFrames(SentenceInfo si, SentenceInfo nsi, String collapsedType) {
        String sentence = si.getText().getText();
        if (nsi.getBaseFrameList() == null || nsi.getBaseFrameList().isEmpty()) {
            for (BaseIEFrameInfo bfi : si.getBaseFrameList()) {
                String type = bfi.getType();
                if (collapsedType != null && !type.equals("no-relation")) {
                    type = collapsedType;
                }
                BaseIEFrameInfo nbfi = new BaseIEFrameInfo(type);
                List entities = new ArrayList<>(2);
                for (BaseSlotInfo bsi : bfi.getSlots()) {
                    entities.add(bsi.getMatchingText(sentence));
                    BaseSlotInfo nbsi = new BaseSlotInfo(bsi.getType(), bsi.getStart(), bsi.getEnd(), bsi.getStatus());
                    nbfi.addSlot(nbsi);
                }
                if (!entities.get(0).equals(entities.get(1))) {
                    nsi.addBaseIEFrameInfo(nbfi);
                }
            }
        }
    }


    private static NEInfo createNeInfo(String entity, String sentence) {
        sentence = sentence.toLowerCase();
        entity = entity.toLowerCase();
        int idx = sentence.indexOf(entity);
        if (idx == -1) {
            return null;
        }
        Assertion.assertTrue(idx != -1);
        int endIdx = idx + entity.length();
        return new NEInfo("structure", String.valueOf(idx), String.valueOf(endIdx), "human");
    }

    public static void prepIdxXmlFile() throws Exception {
        String HOME_DIR = System.getProperty("user.home");
        String csvFile = HOME_DIR + "/dev/java/bnlp-re/data/sparc/Keast_FlatmapRefs_revised.csv";

        List<ConnectivityAnnotationRecord> carList = CSVDataExtractor.loadCSV(csvFile);

        FileInfo fi = toIdxXmlFormat(carList, new SimpleSequentialIDGenerator());
        Comment comment = new Comment(
                GenUtils.prepCreatorComment(CSVDataExtractor.class
                        .getName()));
        fi.saveAsXML("/tmp/positive_idx.xml", comment, CharSetEncoding.UTF8);

        String negIdxXmFile = HOME_DIR + "/dev/java/bnlp-re/data/sparc/sparc_neg_examples_idx_sampled_MSZ.xml";

        boolean collapseRelationTypes = true;
        combine(negIdxXmFile, fi.getDiList(), "/tmp/connectivity_seed_collapsed_type_idx.xml", collapseRelationTypes);
    }


    public static void main(String[] args) throws Exception {
        extractUniqueStructures();
        //prepIdxXmlFile();
        // showKeatsStats();
    }

    private static void showKeatsStats() throws Exception {
        String HOME_DIR = System.getProperty("user.home");
        String csvFile = HOME_DIR + "/dev/java/bnlp-re/data/sparc/Keast_FlatmapRefs_revised.csv";

        List<ConnectivityAnnotationRecord> carList = CSVDataExtractor.loadCSV(csvFile);
        int claimCount = 0;
        int emptyClaimCount = 0;
        for (ConnectivityAnnotationRecord car : carList) {
            if (GenUtils.isEmpty(car.getClaim()) || allCapitalLetters(car.getClaim())) {
                emptyClaimCount++;
            } else {
                claimCount++;
            }
        }
        System.out.println(String.format("claimCount:%d emptyClaimCount:%d", claimCount, emptyClaimCount));
    }

    public static boolean allCapitalLetters(String text) {
        char[] chars = text.toCharArray();
        for (char c : chars) {
            if (Character.isLetter(c) && !Character.isUpperCase(c)) {
                return false;
            }
        }
        return true;
    }

    private static void extractUniqueStructures() throws Exception {
        String HOME_DIR = System.getProperty("user.home");
        String csvFile = HOME_DIR + "/dev/java/bnlp-re/data/sparc/keast_flatmap_refs.csv";

        List<ConnectivityAnnotationRecord> carList = CSVDataExtractor.loadCSV(csvFile);
        Set<String> structureSet = new HashSet<>();
        for (ConnectivityAnnotationRecord car : carList) {
            structureSet.add(car.getStructure1());
            structureSet.add(car.getStructure2());
        }
        List<String> structures = new ArrayList<>(structureSet);
        Collections.sort(structures);
        structures.forEach(System.out::println);
        System.out.println("# structures:" + structures.size());
    }
}
