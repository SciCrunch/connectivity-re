package bnlp.re.sparc.preprocessing;

import bnlp.common.CharSetEncoding;
import bnlp.common.Pair;
import bnlp.common.index.*;
import bnlp.re.util.Assertion;
import bnlp.re.util.FileUtils;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.xmlpull.v1.XmlPullParserException;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Created by bozyurt on 10/17/20.
 */
public class LRModelDataPreparer {
    public final static String HOME_DIR = System.getProperty("user.home");

    public static void getSentenceLengthStats(String idxXMLFile) throws Exception {
        FileInfo fi = new FileInfo(idxXMLFile, CharSetEncoding.UTF8);
        DescriptiveStatistics ds = new DescriptiveStatistics(50000);
        int count = 0;
        for (DocumentInfo di : fi.getDiList()) {
            for (SentenceInfo si : di.getSiList()) {
                if (si.hasNamedEntities() && si.getNeList().size() >= 2) {
                    String sentence = si.getText().getText();
                    ds.addValue(sentence.length());
                    if (sentence.length() < 300 && sentence.indexOf("Table ") == -1) {
                        count++;
                    }
                }
            }
        }
        System.out.println(ds.toString());

        System.out.println("Mean length: " + ds.getMean());
        System.out.println("Standard Dev: " + ds.getStandardDeviation());
        System.out.println(" # of sentences < 300 chars: " + count);
    }

    public static List<Pair<String, String>> prepPredictionInputData(String idxXMLFile) throws Exception {
        List<Pair<String, String>> labeledInstances = new ArrayList<>();
        FileInfo fi = new FileInfo(idxXMLFile, CharSetEncoding.UTF8);
        for (DocumentInfo di : fi.getDiList()) {
            for (SentenceInfo si : di.getSiList()) {
                if (si.hasNamedEntities() && si.getNeList().size() >= 2) {
                    String sentence = si.getText().getText();
                    if (sentence.length() < 300 && sentence.indexOf("Table ") == -1) {
                        labeledInstances.addAll(preparePredictionInstances(si, "$STRUCTURE$"));
                    }
                }
            }
        }
        return labeledInstances;
    }

    public static List<Pair<String, String>> prepInputData(List<String> idxXMLFiles,
                                                           boolean multiRelation) throws Exception {
        List<Pair<String, String>> labeledInstances = new ArrayList<>();
        for (String idxXMLFile : idxXMLFiles) {
            FileInfo fi = new FileInfo(idxXMLFile, CharSetEncoding.UTF8);
            for (DocumentInfo di : fi.getDiList()) {
                di.getSiList().stream().filter(si -> si.hasAnyBaseFrames()).forEach(si ->
                        {
                            if (multiRelation) {
                                labeledInstances.addAll(toMultiRelationInstances(si, "$STRUCTURE$"));
                            } else {
                                labeledInstances.addAll(toInstances(si, "$STRUCTURE$"));
                            }
                        }
                );
            }
        }
        return labeledInstances;
    }

    public static List<Pair<String, String>> preparePredictionInstances(SentenceInfo si, String mask) {
        List<Pair<String, String>> instances = new ArrayList<>();
        String sentence = si.getText().getText();
        si.orderNamedEntities();
        List<NEInfo> filteredList = new ArrayList<>(si.getNeList().size());
        filteredList.addAll(si.getNeList().stream()
                .filter(nei -> nei.getType().equals("structure"))
                .collect(Collectors.toList()));

        int neCount = filteredList.size();
        if (neCount > 1) {
            for (int i = 0; i < neCount; i++) {
                for (int j = i + 1; j < neCount; j++) {
                    String entity1 = filteredList.get(i).extractNE(sentence);
                    String entity2 = filteredList.get(j).extractNE(sentence);
                    if (!entity1.equalsIgnoreCase(entity2)) {
                        NEInfo subject = filteredList.get(i);
                        NEInfo object = filteredList.get(j);
                        if (subject.getStartIdx() > object.getStartIdx()) {
                            subject = filteredList.get(j);
                            object = filteredList.get(i);
                        }
                        StringBuilder sb = new StringBuilder(256);
                        sb.append(sentence.substring(0, subject.getStartIdx()));
                        sb.append(mask);
                        if (subject.getEndIdx() < 0 || subject.getEndIdx() >= sentence.length()
                                || object.getStartIdx() < 0 || object.getStartIdx() >= sentence.length()) {
                            System.out.println("Should not happen");
                        }
                        if (subject.getEndIdx() >= object.getStartIdx()) {
                            System.out.println("Issue with sentence:" + sentence);
                            System.out.println("subject:" + subject);
                            System.out.println("object:" + object);
                            continue;
                        }
                        sb.append(sentence.substring(subject.getEndIdx(), object.getStartIdx()));
                        sb.append(mask);
                        sb.append(sentence.substring(object.getEndIdx()));
                        String maskedSentence = sb.toString();
                        String label = "0";
                        instances.add(new Pair<>(maskedSentence, label));
                    }
                }
            }
        }
        return instances;
    }


    public static List<Pair<String, String>> toInstances(SentenceInfo si, String mask) {
        List<Pair<String, String>> instances = new ArrayList<>(si.getBaseFrameList().size());
        String sentence = si.getText().getText();
        sentence = sentence.replaceAll("\\n", "");
        String noRelation = "no-relation";
        Set<String> noRelSet = new HashSet<>(Arrays.asList("structural-connectivity", "topological-connectivity",
                "general-connectivity"));
        noRelSet.add(noRelation);
        for (BaseIEFrameInfo pairInfo : si.getBaseFrameList()) {
            BaseSlotInfo subject = pairInfo.getSlots().get(0);
            BaseSlotInfo object = pairInfo.getSlots().get(1);
            StringBuilder sb = new StringBuilder(256);
            if (subject.getStartIdx() > object.getStartIdx()) {
                subject = pairInfo.getSlots().get(1);
                object = pairInfo.getSlots().get(0);
            }
            sb.append(sentence.substring(0, subject.getStartIdx()));
            sb.append(mask);
            sb.append(sentence.substring(subject.getEndIdx(), object.getStartIdx()));
            sb.append(mask);
            sb.append(sentence.substring(object.getEndIdx()));
            String maskedSentence = sb.toString();
            String label = noRelSet.contains(pairInfo.getType()) ? "0" : "1";
            instances.add(new Pair<>(maskedSentence, label));
        }
        return instances;
    }

    public static List<Pair<String, String>> toMultiRelationInstances(SentenceInfo si, String mask) {
        List<Pair<String, String>> instances = new ArrayList<>(si.getBaseFrameList().size());
        String sentence = si.getText().getText();
        Set<String> noRelSet = new HashSet<>(Arrays.asList("structural-connectivity", "topological-connectivity",
                "general-connectivity", "no-relation"));
        for (BaseIEFrameInfo pairInfo : si.getBaseFrameList()) {
            BaseSlotInfo subject = pairInfo.getSlots().get(0);
            BaseSlotInfo object = pairInfo.getSlots().get(1);
            StringBuilder sb = new StringBuilder(256);
            if (subject.getStartIdx() > object.getStartIdx()) {
                subject = pairInfo.getSlots().get(1);
                object = pairInfo.getSlots().get(0);
            }
            sb.append(sentence.substring(0, subject.getStartIdx()));
            sb.append(mask);
            sb.append(sentence.substring(subject.getEndIdx(), object.getStartIdx()));
            sb.append(mask);
            sb.append(sentence.substring(object.getEndIdx()));
            String maskedSentence = sb.toString();
            maskedSentence = maskedSentence.replaceAll("\\n", "");
            String label;
            if (noRelSet.contains(pairInfo.getType())) {
                label = "False";
            } else if (pairInfo.getType().startsWith("anatomical")) {
                label = "AC";
            } else {
                label = "FC"; // functional connectivity
            }
            instances.add(new Pair<>(maskedSentence, label));
        }
        return instances;
    }


    public static Map<String, Pair<String, SentenceInfo>> prepMasked2SentenceInfoMap(String idxXmlFile,
                                                                                     String mask) throws Exception {
        FileInfo fi = new FileInfo(idxXmlFile, CharSetEncoding.UTF8);
        Map<String, Pair<String, SentenceInfo>> map = new HashMap<>();
        for (DocumentInfo di : fi.getDiList()) {
            for (SentenceInfo si : di.getSiList()) {
                String sentence = si.getText().getText();
                if (!si.hasNamedEntities()) {
                    continue;
                }
                String pmid = di.getPMID();
                for (int i = 0; i < si.getNeList().size(); i++) {
                    NEInfo subject = si.getNeList().get(i);
                    for (int j = i + 1; j < si.getNeList().size(); j++) {
                        NEInfo object = si.getNeList().get(j);
                        if (subject.getEndIdx() >= object.getStartIdx()) {
                            System.out.println("skipping " + subject + " " + object);
                            continue;
                        }

                        StringBuilder sb = new StringBuilder(128);
                        sb.append(sentence.substring(0, subject.getStartIdx()));
                        sb.append(mask);
                        sb.append(sentence.substring(subject.getEndIdx(), object.getStartIdx()));
                        sb.append(mask);
                        sb.append(sentence.substring(object.getEndIdx()));
                        String maskedSentence = sb.toString();
                        maskedSentence = maskedSentence.replaceAll("\\n", "");
                        map.put(maskedSentence, new Pair(pmid, si));
                    }
                }
            }
        }
        return map;
    }

    public static Map<String, Pair<SentenceInfo, BaseIEFrameInfo>>
    prepMasked2RelationMap(String idxXmlFile, String mask) throws Exception {
        FileInfo fi = new FileInfo(idxXmlFile, CharSetEncoding.UTF8);
        Map<String, Pair<SentenceInfo, BaseIEFrameInfo>> map = new HashMap<>();
        for (DocumentInfo di : fi.getDiList()) {
            for (SentenceInfo si : di.getSiList()) {
                String sentence = si.getText().getText();
                if (si.getBaseFrameList() == null) {
                    System.out.println("No relations for " + si.getText().getText());
                    continue;
                }

                for (BaseIEFrameInfo pairInfo : si.getBaseFrameList()) {
                    BaseSlotInfo subject = pairInfo.getSlots().get(0);
                    BaseSlotInfo object = pairInfo.getSlots().get(1);
                    StringBuilder sb = new StringBuilder(256);
                    if (subject.getStartIdx() > object.getStartIdx()) {
                        subject = pairInfo.getSlots().get(1);
                        object = pairInfo.getSlots().get(0);
                    }
                    sb.append(sentence.substring(0, subject.getStartIdx()));
                    sb.append(mask);
                    sb.append(sentence.substring(subject.getEndIdx(), object.getStartIdx()));
                    sb.append(mask);
                    sb.append(sentence.substring(object.getEndIdx()));
                    String maskedSentence = sb.toString();
                    maskedSentence = maskedSentence.replaceAll("\\n", "");
                    Pair<SentenceInfo, BaseIEFrameInfo> pair = new Pair<>(si, pairInfo);
                    map.put(maskedSentence, pair);
                }
            }
        }
        return map;
    }

    public static void save(List<Pair<String, String>> instances,
                            String outTSVFile) throws IOException {
        save(instances, outTSVFile, true);
    }


    public static List<Pair<String, String>> loadTSV(String tsvFile) throws IOException {
        List<Pair<String, String>> pairList = new ArrayList<>();
        BufferedReader in = null;
        try {
            in = FileUtils.newUTF8CharSetReader(tsvFile);
            Iterable<CSVRecord> records = CSVFormat.DEFAULT.withSkipHeaderRecord()
                    .withRecordSeparator('\n').withDelimiter('\t').parse(in);
            for (CSVRecord record : records) {
                // System.out.println(record);
                Pair<String, String> pair = new Pair<>(record.get(0), record.get(1));
                pairList.add(pair);
            }
        } finally {
            FileUtils.close(in);
        }
        return pairList;
    }

    public static void save(List<Pair<String, String>> instances,
                            String outTSVFile, boolean addHeader) throws IOException {

        BufferedWriter out = null;
        CSVFormat csvFormat = CSVFormat.DEFAULT.withRecordSeparator("\n").withDelimiter('\t');
        try {
            out = FileUtils.newUTF8CharSetWriter(outTSVFile);
            CSVPrinter csvPrinter = new CSVPrinter(out, csvFormat);
            if (addHeader) {
                csvPrinter.printRecord(new Object[]{"sentence", "label"});
            }
            List<String> record = new ArrayList<>(2);
            for (Pair<String, String> instance : instances) {
                record.clear();
                record.add(instance.getFirst());
                record.add(instance.getSecond());
                csvPrinter.printRecord(record);
            }
            System.out.println("saved " + outTSVFile);
        } finally {
            FileUtils.close(out);
        }
    }


    public static void toMultiRelationTSVFile(String inTSVFile, String outTSVFile,
                                              Map<String, Pair<SentenceInfo, BaseIEFrameInfo>> map) throws Exception {
        Set<String> noRelSet = new HashSet<>(Arrays.asList("structural-connectivity", "topological-connectivity",
                "general-connectivity", "no-relation"));
        List<Pair<String, String>> pairs = loadTSV(inTSVFile);
        List<Pair<String, String>> outPairs = new ArrayList<>(pairs.size());
        for (Pair<String, String> pair : pairs) {
            String maskedSentence = pair.getFirst();
            Pair<SentenceInfo, BaseIEFrameInfo> relationInfo = map.get(maskedSentence);
            if (relationInfo == null) {
                System.out.println("No relation info for " + maskedSentence);
                continue;
            }
            Assertion.assertNotNull(relationInfo);
            String type = relationInfo.getSecond().getType();
            String label;
            if (noRelSet.contains(type)) {
                label = "False";
            } else if (type.startsWith("anatomical")) {
                label = "AC";
            } else {
                label = "FC"; // functional connectivity
            }
            Pair<String, String> outPair = new Pair<>(maskedSentence, label);
            outPairs.add(outPair);
        }
        save(outPairs, outTSVFile);
    }

    public static void prepTrainTestSets(List<Pair<String, String>> instances, double testFrac) throws Exception {
        List<Integer> indices = IntStream.range(0, instances.size()).boxed().collect(Collectors.toList());
        Random rnd = new Random(374643758L);
        Collections.shuffle(indices, rnd);
        int testSize = (int) (testFrac * instances.size());
        List<Integer> testIndices = new ArrayList<>(indices.subList(0, testSize));
        List<Integer> trainIndices = new ArrayList<>(indices.subList(testSize, indices.size()));
        List<Pair<String, String>> trainList = new ArrayList<>(instances.size() - testSize);
        List<Pair<String, String>> testList = new ArrayList<>(testSize);
        trainIndices.forEach(i -> trainList.add(instances.get(i)));
        testIndices.forEach(i -> testList.add(instances.get(i)));
        save(trainList, "/tmp/train.tsv");
        save(testList, "/tmp/test.tsv");
    }

    public static void prepPredictionSet(String idxXMLFile) throws Exception {
        prepPredictionSet(idxXMLFile, "/tmp/test.tsv");
    }

    public static void prepPredictionSet(String idxXMLFile, String outTSVFile) throws Exception {
        List<Pair<String, String>> predData = prepPredictionInputData(idxXMLFile);
        save(predData, outTSVFile);
    }

    public static void prepBaseTwoClassTrainTestSet() throws Exception {
        List<String> allIdxXmlFiles = Arrays.asList(HOME_DIR +
                "/dev/java/bnlp-re/data/sparc/base/sparc_connectivity_nerve_ganglia_sampled_idx_joe_full.xml");
        List<Pair<String, String>> instances = prepInputData(allIdxXmlFiles, false);
        prepTrainTestSets(instances, 0.2);
    }

    public static void prepFullMultiClassTrainTestSet() throws Exception {
        String BASE_DIR = HOME_DIR + "/dev/java/bnlp-re/data/sparc/base";
        String baseIdxXmlFile = BASE_DIR + "/sparc_connectivity_nerve_ganglia_sampled_idx_joe_full.xml";
        String addedIdxXmlFile = BASE_DIR + "/active_learning/iterations/full_train_idx.xml";

        Map<String, Pair<SentenceInfo, BaseIEFrameInfo>> map = prepMasked2RelationMap(baseIdxXmlFile, "$STRUCTURE$");
        map.putAll(prepMasked2RelationMap(addedIdxXmlFile, "$STRUCTURE$"));

        String inTrainTSVFile = BASE_DIR + "/active_learning/iterations/train_full.tsv";
        String outTrainTSVFile = BASE_DIR + "/active_learning/iterations/multi_rel_train_full.tsv";

        // toMultiRelationTSVFile(inTrainTSVFile, outTrainTSVFile, map);

        String inTestTSVFile = BASE_DIR + "/active_learning/test.tsv";
        String outTestTSVFile = BASE_DIR + "/active_learning/multi_rel_test.tsv";

        toMultiRelationTSVFile(inTestTSVFile, outTestTSVFile, map);
    }


    public static void main(String[] args) throws Exception {
        //testDriver();
        //prepBaseTwoClassTrainTestSet();
        prepFullMultiClassTrainTestSet();
    }

    public static void testDriver() throws Exception {
        List<String> allIdxXmlFiles = Arrays.asList(HOME_DIR +
                "/dev/java/bnlp-re/data/sparc/connectivity_seed_collapsed_type_idx.xml");

        //     List<Pair<String, String>> instances = prepInputData(allIdxXmlFiles);
        // prepTrainTestSets(instances, 0.2);


        //String idxXmlFile = HOME_DIR + "/dev/java/bnlp-re/sparc_combined_vocab_idx_11_02_2020.xml";
        String idxXmlFile = HOME_DIR + "/dev/java/bnlp-re/sparc_combined_vocab_idx_11_06_2020.xml";
        // getSentenceLengthStats(idxXmlFile);
        prepPredictionSet(idxXmlFile);
    }

}
