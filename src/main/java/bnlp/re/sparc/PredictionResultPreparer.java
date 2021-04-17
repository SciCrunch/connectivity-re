package bnlp.re.sparc;

import bnlp.common.CharSetEncoding;
import bnlp.common.index.*;
import bnlp.re.util.FileUtils;
import bnlp.re.util.NumberUtils;
import bnlp.util.GenUtils;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.jdom2.Comment;

import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Created by bozyurt on 11/3/20.
 */
public class PredictionResultPreparer {

    public final static String HOME_DIR = System.getProperty("user.home");

    public static List<Prediction> filterPredictions(String predictionFile, double probThreshold) throws Exception {
        return filterPredictions(predictionFile, probThreshold, false);
    }

    public static List<Prediction> filterPredictions(String predictionFile, double probThreshold,
                                                     boolean reverse) throws Exception {
        List<Prediction> predictions = new ArrayList<>();
        BufferedReader in = null;
        try {
            in = FileUtils.newUTF8CharSetReader(predictionFile);
            Iterable<CSVRecord> records = CSVFormat.RFC4180.withDelimiter('\t').parse(in);
            boolean first = true;
            for (CSVRecord record : records) {
                if (first) {
                    first = false;
                    continue;
                }
                double relProb = NumberUtils.getDouble(record.get(2));
                if (reverse) {
                    if (relProb <= probThreshold) {
                        String maskedSentence = record.get(0);
                        predictions.add(new Prediction(maskedSentence, (float) relProb));
                    }
                } else {
                    if (relProb >= probThreshold) {
                        String maskedSentence = record.get(0);
                        predictions.add(new Prediction(maskedSentence, (float) relProb));
                    }
                }
            }
        } finally {
            FileUtils.close(in);
        }
        return predictions;
    }

    public static List<Prediction> loadPredictions(String predictionFile) throws Exception {
        List<Prediction> predictions = new ArrayList<>();
        BufferedReader in = null;
        try {
            in = FileUtils.newUTF8CharSetReader(predictionFile);
            Iterable<CSVRecord> records = CSVFormat.RFC4180.withDelimiter('\t').parse(in);
            boolean first = true;
            for (CSVRecord record : records) {
                if (first) {
                    first = false;
                    continue;
                }
                double relProb = NumberUtils.getDouble(record.get(2));
                String maskedSentence = record.get(0);
                predictions.add(new Prediction(maskedSentence, (float) relProb));
            }
        } finally {
            FileUtils.close(in);
        }
        return predictions;
    }

    public static List<MultiRelPrediction> loadMultiRelPredictions(String predictionFile) throws Exception {
        List<MultiRelPrediction> predictions = new ArrayList<>();
        BufferedReader in = null;
        try {
            in = FileUtils.newUTF8CharSetReader(predictionFile);
            Iterable<CSVRecord> records = CSVFormat.RFC4180.withDelimiter('\t').parse(in);
            boolean first = true;
            for (CSVRecord record : records) {
                if (first) {
                    first = false;
                    continue;
                }
                Map<String, Float> label2ProbMap = new HashMap<>(7);
                label2ProbMap.put("AC", (float) NumberUtils.getDouble(record.get(1)));
                label2ProbMap.put("FC", (float) NumberUtils.getDouble(record.get(2)));
                label2ProbMap.put("no-rel", (float) NumberUtils.getDouble(record.get(3)));

                String maskedSentence = record.get(0);
                predictions.add(new MultiRelPrediction(maskedSentence, label2ProbMap));
            }
        } finally {
            FileUtils.close(in);
        }
        return predictions;
    }

    public static List<Prediction> extractLeastConfident(List<Prediction> predictions, double spread) {
        List<Prediction> filtered;
        double low = 0.5 - spread;
        double high = 0.5 + spread;
        filtered = predictions.stream().filter(pred -> pred.getRelProb() >= low && pred.getRelProb() <= high)
                .collect(Collectors.toList());
        return filtered;
    }

    public static void prep4Annotation(String idxXmlFile, List<Prediction> predictions,
                                       String outIdxXmlFile) throws Exception {
        Map<String, Prediction> predMap = new HashMap<>();
        predictions.forEach(pred -> predMap.put(pred.getMaskedSentence(), pred));
        FileInfo fi = new FileInfo(idxXmlFile, CharSetEncoding.UTF8);
        FileInfo nfi = new FileInfo("", "", "");
        int count = 0;
        int predCount = 0;
        for (DocumentInfo di : fi.getDiList()) {
            DocumentInfo ndi = new DocumentInfo(di.getDocIdx(), di.getPMID());
            boolean found = false;
            for (SentenceInfo si : di.getSiList()) {
                if (si.hasNamedEntities() && si.getNeList().size() >= 2) {
                    String sentence = si.getText().getText();
                    if (sentence.length() < 300 && sentence.indexOf("Table ") == -1) {
                        SentenceInfo nsi = prepRelations(si, predMap, ndi.getDocIdx());
                        count++;
                        if (nsi != null) {
                            ndi.addSentenceInfo(nsi);
                            predCount++;
                            found = true;
                        }
                    }
                }
                if ((count % 100) == 0) {
                    System.out.print("\rHandled so far:" + count);
                }
            }
            if (found) {
                nfi.appendDocument(ndi);
            }
        }
        System.out.println();
        System.out.println("predCount:" + predCount);

        Comment comment = new Comment(
                GenUtils.prepCreatorComment(PredictionResultPreparer.class
                        .getName()));
        nfi.saveAsXML(outIdxXmlFile, comment, CharSetEncoding.UTF8);
        System.out.println("saved " + outIdxXmlFile);

    }

    public static SentenceInfo prepRelations(SentenceInfo si,
                                             Map<String, Prediction> predictionMap,
                                             int docIdx) {
        String sentence = si.getText().getText();
        si.orderNamedEntities();
        List<NEInfo> filteredList = new ArrayList<>(si.getNeList().size());
        filteredList.addAll(si.getNeList().stream()
                .filter(nei -> nei.getType().equals("structure"))
                .collect(Collectors.toList()));
        int neCount = filteredList.size();
        if (neCount < 2) {
            return null;
        }
        SentenceInfo nsi = new SentenceInfo(si, docIdx);
        String mask = "$STRUCTURE$";
        boolean found = false;
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
                    sb.append(sentence.substring(subject.getEndIdx(), object.getStartIdx()));
                    sb.append(mask);
                    sb.append(sentence.substring(object.getEndIdx()));
                    String maskedSentence = sb.toString();
                    String type = "no-relation";
                    if (predictionMap.containsKey(maskedSentence)) {
                        found = true;
                        type = "anatomical-connectivity";
                    }

                    BaseIEFrameInfo bfi = new BaseIEFrameInfo(type);
                    BaseSlotInfo bsi1 = new BaseSlotInfo("structure",
                            subject.getStart(), subject.getEnd(), "machine");
                    BaseSlotInfo bsi2 = new BaseSlotInfo("structure",
                            object.getStart(), object.getEnd(), "machine");
                    bfi.addSlot(bsi1);
                    bfi.addSlot(bsi2);
                    nsi.addBaseIEFrameInfo(bfi);
                }
            }
        }
        if (found) {
            return nsi;
        }
        return null;
    }


    public static class Prediction implements Comparable<Prediction> {
        final String maskedSentence;
        final float relProb;

        public Prediction(String maskedSentence, float relProb) {
            this.maskedSentence = maskedSentence;
            this.relProb = relProb;
        }

        public String getMaskedSentence() {
            return maskedSentence;
        }

        public float getRelProb() {
            return relProb;
        }

        @Override
        public int compareTo(Prediction o) {
            return Float.compare(relProb, o.relProb);
        }
    }

    public static class MultiRelPrediction {
        final String maskedSentence;
        final Map<String, Float> label2ProbMap;

        public MultiRelPrediction(String maskedSentence, Map<String, Float> label2ProbMap) {
            this.maskedSentence = maskedSentence;
            this.label2ProbMap = label2ProbMap;
        }

        public String getMaskedSentence() {
            return maskedSentence;
        }

        public Map<String, Float> getLabel2ProbMap() {
            return label2ProbMap;
        }
    }

    public static void prepFullSet4Annotation() throws Exception {
        String predictionFile = HOME_DIR + "/dev/java/bnlp-re/sparc_predictions.txt";
        List<Prediction> predictions = filterPredictions(predictionFile, 0.35);
        System.out.println("# filtered predictions: " + predictions.size());
        String idxXmlFile = HOME_DIR + "/dev/java/bnlp-re/sparc_combined_vocab_idx_11_06_2020.xml";
        String outIdxXmlFile = "/tmp/sparc_connectivity_predicted_idx.xml";
        prep4Annotation(idxXmlFile, predictions, outIdxXmlFile);
    }


    public static void main(String[] args) throws Exception {
        // prepActiveLearningIter1AnnotationSet();
        prepFullSet4Annotation();
    }

    public static void prepActiveLearningIter1AnnotationSet() throws Exception {
        String predictionFile = HOME_DIR + "/dev/java/bnlp-re/sparc_predictions.txt";
        List<Prediction> predictions = filterPredictions(predictionFile, 0.45);
        predictions = extractLeastConfident(predictions, 0.005);
        System.out.println("# filtered predictions: " + predictions.size());
        String idxXmlFile = HOME_DIR + "/dev/java/bnlp-re/sparc_combined_vocab_idx_11_06_2020.xml";
        // String outIdxXmlFile = "/tmp/sparc_connectivity_predicted_idx.xml";
        String outIdxXmlFile = "/tmp/sparc_connectivity_active_learning_idx.xml";
        prep4Annotation(idxXmlFile, predictions, outIdxXmlFile);
    }
}
