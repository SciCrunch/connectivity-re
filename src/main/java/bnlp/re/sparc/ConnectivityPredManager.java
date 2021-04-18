package bnlp.re.sparc;

import bnlp.common.CharSetEncoding;
import bnlp.common.Pair;
import bnlp.common.index.*;
import bnlp.re.sparc.PredictionResultPreparer.MultiRelPrediction;
import bnlp.re.sparc.preprocessing.LRModelDataPreparer;
import bnlp.re.util.Assertion;
import bnlp.re.util.FileUtils;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import java.io.BufferedWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Created by bozyurt on 3/26/21.
 */
public class ConnectivityPredManager {
    public static String HOME_DIR = System.getProperty("user.home");

    public static void prepRemainingNerveGangliaCorpus() throws Exception {
        String rootDir = HOME_DIR + "/dev/java/connectivity-re/data/sparc";
        String inCorpusIdxXmlFile = rootDir + "/sparc_connectivity_nerve_ganglia_corpus_idx.xml";
        List<String> excludeList = new ArrayList<>();
        excludeList.add(rootDir + "/base/sparc_connectivity_nerve_ganglia_sampled_idx_joe_full.xml");
        excludeList.add(rootDir + "/base/active_learning/sparc_connectivity_nerve_ganglia_random_set_idx.xml");
        excludeList.add(rootDir + "/base/active_learning/iterations/full_train_idx.xml");
        String outCorpusIdxXmlFile = "/tmp/nerve_ganglia_unlabeled_idx.xml";
        ActiveLearningManager.prepareFilteredCorpusIdxXmlFile(inCorpusIdxXmlFile, excludeList, outCorpusIdxXmlFile);

        // prepare for prediction
        String outTSVFile = "/tmp/nerve_ganglia_unlabeled_4pred.tsv";
        LRModelDataPreparer.prepPredictionSet(outCorpusIdxXmlFile, outTSVFile);
    }

    public static void prepCSVOutput(String predictionFile, String idxXmlFile, String outCSVFile) throws Exception {
        Map<String, Pair<String, SentenceInfo>> map = LRModelDataPreparer.prepMasked2SentenceInfoMap(idxXmlFile,
                "$STRUCTURE$");
        List<MultiRelPrediction> predictions = PredictionResultPreparer.loadMultiRelPredictions(predictionFile);
        List<Row> acList = new ArrayList<>();
        List<Row> fcList = new ArrayList<>();
        for (MultiRelPrediction prediction : predictions) {
            String key = prediction.getMaskedSentence().replaceAll("\\n", " ");
            Pair<String, SentenceInfo> pair = map.get(key);
            if (pair == null) {
                System.err.println("Missing: " + prediction.getMaskedSentence());
                continue;
            }
            Pair<String, Float> predInfo = ErrorReportGenerator.getArgMax(prediction);
            String predicted = predInfo.getFirst();
            if (predicted.equals("AC") || predicted.equals("FC")) {
                String sentence = pair.getSecond().getText().getText();
                String pmid = pair.getFirst();
                Pair<String, String> structures = findStructures(pair.getSecond(), key);
                Assertion.assertNotNull(structures);
                Row row = new Row(sentence, pmid, structures.getFirst(),
                        structures.getSecond(), predInfo.getSecond());
                if (predicted.equals("AC")) {
                    acList.add(row);
                } else {
                    fcList.add(row);
                }
            }
            // Assertion.assertNotNull(si);
        }
        Collections.sort(acList);
        Collections.sort(fcList);
        System.out.println("acList.size: " + acList.size());
        System.out.println("fcList.size: " + fcList.size());

        saveCSV(acList.subList(0, 100), "/tmp/nerve_ganglia_unlabeled_anatomical_connectivity.csv");
        saveCSV(fcList.subList(0, 100), "/tmp/nerve_ganglia_unlabeled_functional_connectivity.csv");
    }


    public static Pair<String, String> findStructures(SentenceInfo si, String maskedSentence) {
        String mask = "$STRUCTURE$";
        String sentence = si.getText().getText().replaceAll("\\n", " ");
        for (int i = 0; i < si.getNeList().size(); i++) {
            NEInfo subject = si.getNeList().get(i);
            for (int j = i + 1; j < si.getNeList().size(); j++) {
                NEInfo object = si.getNeList().get(j);
                StringBuilder sb = new StringBuilder(128);
                sb.append(sentence.substring(0, subject.getStartIdx()));
                sb.append(mask);
                sb.append(sentence.substring(subject.getEndIdx(), object.getStartIdx()));
                sb.append(mask);
                sb.append(sentence.substring(object.getEndIdx()));
                String candidate = sb.toString();
                candidate = candidate.replaceAll("\\n", "");
                if (candidate.equals(maskedSentence)) {
                    String structure1 = subject.extractNE(si.getText().getText());
                    String structure2 = object.extractNE(si.getText().getText());
                    return new Pair<>(structure1, structure2);
                }
            }
        }
        return null;
    }

    public static void saveCSV(List<Row> rows, String outCSVFile) throws Exception {
        BufferedWriter out = null;
        CSVFormat csvFormat = CSVFormat.DEFAULT.withRecordSeparator("\n").withDelimiter(',');
        try {
            out = FileUtils.newUTF8CharSetWriter(outCSVFile);
            CSVPrinter csvPrinter = new CSVPrinter(out, csvFormat);
            csvPrinter.printRecord(new Object[]{"pmcid", "sentence", "structure_1", "structure_2", "score", "pubmed_url"});
            List<String> record = new ArrayList<>(2);
            for (Row row : rows) {
                record.clear();
                record.add(row.getPmid());
                record.add(row.getSentence());
                record.add(row.getStructure1());
                record.add(row.getStructure2());
                record.add(String.valueOf(row.getScore()));
                String url = "http://www.ncbi.nlm.nih.gov/pubmed/" + row.getPmid();
                record.add(url);
                csvPrinter.printRecord(record);
            }
            System.out.println("saved " + outCSVFile);
        } finally {
            FileUtils.close(out);
        }
    }


    public static List<Row> extractRowsOfType(String annotatedIdxXmlFile, String type) throws Exception {
        List<Row> rows = new ArrayList<>();
        FileInfo fi = new FileInfo(annotatedIdxXmlFile, CharSetEncoding.UTF8);
        for (DocumentInfo di : fi.getDiList()) {
            for (SentenceInfo si : di.getSiList()) {
                if (si.hasAnyBaseFrames()) {
                    for (BaseIEFrameInfo bfi : si.getBaseFrameList()) {
                        if (bfi.getType().equals(type)) {
                            String pmid = di.getPMID();
                            String sentence = si.getText().getText();
                            BaseSlotInfo slot1 = bfi.getSlots().get(0);
                            BaseSlotInfo slot2 = bfi.getSlots().get(1);
                            String structure1 = sentence.substring(slot1.getStartIdx(), slot1.getEndIdx());
                            String structure2 = sentence.substring(slot2.getStartIdx(), slot2.getEndIdx());
                            Row row = new Row(sentence, pmid, structure1, structure2, 1);
                            rows.add(row);
                        }
                    }
                }
            }
        }
        return rows;
    }

    public static void saveAnnotatedCSVFiles() throws Exception {
        String rootDir = HOME_DIR + "/dev/java/connectivity-re/data/sparc";
        String annotatedIdxXmlFile =  rootDir + "/base/sparc_connectivity_nerve_ganglia_sampled_idx_joe_full.xml";

        List<Row> acList = extractRowsOfType(annotatedIdxXmlFile, "anatomical-connectivity");
        List<Row> fcList = extractRowsOfType(annotatedIdxXmlFile, "functional-connectivity");

        saveCSV(acList.subList(0, 100), "/tmp/nerve_ganglia_labeled_anatomical_connectivity.csv");
        saveCSV(fcList.subList(0, 100), "/tmp/nerve_ganglia_labeled_functional_connectivity.csv");
    }

    public static class Row implements Comparable<Row> {
        String sentence;
        String structure1;
        String structure2;
        String pmid;
        double score;

        public Row(String sentence, String pmid, String structure1, String structure2, double score) {
            this.sentence = sentence;
            this.pmid = pmid;
            this.structure1 = structure1;
            this.structure2 = structure2;
            this.score = score;
        }

        public String getSentence() {
            return sentence;
        }

        public String getStructure1() {
            return structure1;
        }

        public String getStructure2() {
            return structure2;
        }

        public String getPmid() {
            return pmid;
        }

        public double getScore() {
            return score;
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder("Row{");
            sb.append("sentence='").append(sentence).append('\'');
            sb.append(", pmid='").append(pmid).append('\'');
            sb.append(", score=").append(score);
            sb.append(", structure1='").append(structure1).append('\'');
            sb.append(", structure2='").append(structure2).append('\'');
            sb.append('}');
            return sb.toString();
        }

        @Override
        public int compareTo(Row o) {
            return Double.compare(o.getScore(), score);
        }
    }


    public static void main(String[] args) throws Exception {

        // prepRemainingNerveGangliaCorpus();
        String idxXmlFile = "/tmp/nerve_ganglia_unlabeled_idx.xml";
        String predictionFile = HOME_DIR + "/dev/java/connectivity-re/nerve_ganglia_unlabeled_predicted.txt";
        // prepCSVOutput(predictionFile, idxXmlFile, null);

        saveAnnotatedCSVFiles();
    }
}
