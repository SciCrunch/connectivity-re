package bnlp.re.sparc;

import bnlp.common.Pair;
import bnlp.common.index.BaseIEFrameInfo;
import bnlp.common.index.BaseSlotInfo;
import bnlp.common.index.SentenceInfo;
import bnlp.re.sparc.PredictionResultPreparer.MultiRelPrediction;
import bnlp.re.sparc.PredictionResultPreparer.Prediction;
import bnlp.re.util.Assertion;

import java.util.*;

import static bnlp.re.sparc.preprocessing.LRModelDataPreparer.prepMasked2RelationMap;
import static bnlp.re.sparc.preprocessing.LRModelDataPreparer.toMultiRelationTSVFile;

/**
 * Created by bozyurt on 2/25/21.
 */
public class ErrorReportGenerator {
    boolean multiRelation = false;
    Map<String, Pair<SentenceInfo, BaseIEFrameInfo>> map;
    public static String HOME_DIR = System.getProperty("user.home");
    public static String BASE_DIR = HOME_DIR + "/dev/java/bnlp-re/data/sparc/base";

    public ErrorReportGenerator(boolean multiRelation) throws Exception {
        this.multiRelation = multiRelation;
        String baseIdxXmlFile = BASE_DIR + "/sparc_connectivity_nerve_ganglia_sampled_idx_joe_full.xml";
        String addedIdxXmlFile = BASE_DIR + "/active_learning/iterations/full_train_idx.xml";

        this.map = prepMasked2RelationMap(baseIdxXmlFile,
                "$STRUCTURE$");
        map.putAll(prepMasked2RelationMap(addedIdxXmlFile, "$STRUCTURE$"));
    }


    @SuppressWarnings("Duplicates")
    public void prepMultiRelReport(String predictionFile) throws Exception {
        Set<String> noRelSet = new HashSet<>(Arrays.asList("structural-connectivity", "topological-connectivity",
                "general-connectivity", "no-relation"));
        List<MultiRelPrediction> predictions = PredictionResultPreparer.loadMultiRelPredictions(predictionFile);
        int fpAC = 0, fpFC = 0;
        int fnAC = 0, fnFC = 0;
        int numACConnectivity = 0;
        int numFCConnectivity = 0;
        List<ErrorRecord> erACList = new ArrayList<>();
        List<ErrorRecord> erFCList = new ArrayList<>();
        List<ErrorRecord> correctList = new ArrayList<>();
        for (MultiRelPrediction prediction : predictions) {
            Pair<SentenceInfo, BaseIEFrameInfo> relInfo = map.get(prediction.getMaskedSentence());
            Assertion.assertNotNull(relInfo);
            String type = relInfo.getSecond().getType();
            String actual;
            if (noRelSet.contains(type)) {
                actual = "no-rel";
            } else if (type.startsWith("anatomical")) {
                actual = "AC";
            } else {
                actual = "FC";
            }
            Pair<String, Float> predInfo = getArgMax(prediction);
            String predicted = predInfo.getFirst();
            if (actual.equals("AC")) {
                numACConnectivity++;
                if (!predicted.equals(actual)) {
                    fnAC++;
                    ErrorRecord er = prepErrorRecord(predInfo, actual, relInfo);
                    erACList.add(er);
                } else {
                    ErrorRecord correct = prepErrorRecord(predInfo, actual, relInfo);
                    correctList.add(correct);
                }
            } else if (actual.equals("FC")) {
                numFCConnectivity++;
                if (!predicted.equals(actual)) {
                    fnFC++;
                    ErrorRecord er = prepErrorRecord(predInfo, actual, relInfo);
                    erFCList.add(er);
                } else {
                    ErrorRecord correct = prepErrorRecord(predInfo, actual, relInfo);
                    correctList.add(correct);
                }
            } else {
                if (!predicted.equals(actual)) {
                    ErrorRecord er = prepErrorRecord(predInfo, actual, relInfo);
                    if (predicted.equals("AC")) {
                        fpAC++;
                        erACList.add(er);
                    } else {
                        fpFC++;
                        erFCList.add(er);
                    }
                }
            }
        }
        int totErrors = erACList.size() + erFCList.size();
        System.out.println("# of errors:" + totErrors + " out of " + predictions.size());
        System.out.println("AC FP:" + fpAC + " FN:" + fnAC + " # of anatomical connectivity relations:" + numACConnectivity);
        System.out.println("AC Errors\n=========");
        for (ErrorRecord er : erACList) {
            System.out.println(er);
            System.out.println("-------------------");
        }
        System.out.println();
        System.out.println("FC FP:" + fpFC + " FN:" + fnFC + " # of functional connectivity relations:" + numFCConnectivity);
        System.out.println("FC Errors\n=========");
        for (ErrorRecord er : erFCList) {
            System.out.println(er);
            System.out.println("-------------------");
        }
        System.out.println();
        System.out.println("Correct connectivity records");
        System.out.println("============================");
        for (ErrorRecord cr : correctList) {
            System.out.println(cr);
            System.out.println("----------------------");
        }
        System.out.println("# of correct connectivity predictions: " + correctList.size());
    }

    public static Pair<String, Float> getArgMax(MultiRelPrediction prediction) {
        Map<String, Float> label2ProbMap = prediction.getLabel2ProbMap();
        float max = Float.NEGATIVE_INFINITY;
        String maxLabel = null;
        for (String label : label2ProbMap.keySet()) {
            float prob = label2ProbMap.get(label);
            if (prob > max) {
                max = prob;
                maxLabel = label;
            }
        }
        return new Pair(maxLabel, max);
    }

    public static ErrorRecord prepErrorRecord(Pair<String, Float> predInfo, String actual,
                                              Pair<SentenceInfo, BaseIEFrameInfo> relInfo) {
        String sentence = relInfo.getFirst().getText().getText();
        sentence = sentence.replaceAll("\\n", "");
        String predicted = predInfo.getFirst();
        float score = predInfo.getSecond();
        ErrorRecord er = new ErrorRecord(actual, predicted, score);
        BaseIEFrameInfo bfi = relInfo.getSecond();
        BaseSlotInfo subject = bfi.getSlots().get(0);
        BaseSlotInfo object = bfi.getSlots().get(1);
        if (subject.getStartIdx() > object.getStartIdx()) {
            subject = bfi.getSlots().get(1);
            object = bfi.getSlots().get(0);
        }
        er.addBlock(new TextBlock(sentence.substring(0, subject.getStartIdx())));
        er.addBlock(new TextBlock(sentence.substring(subject.getStartIdx(), subject.getEndIdx()), "structure"));
        er.addBlock(new TextBlock(sentence.substring(subject.getEndIdx(), object.getStartIdx())));
        er.addBlock(new TextBlock(sentence.substring(object.getStartIdx(), object.getEndIdx()), "structure"));
        er.addBlock(new TextBlock(sentence.substring(object.getEndIdx())));
        return er;
    }


    public void prepReport(String predictionFile) throws Exception {
        Set<String> noRelSet = new HashSet<>(Arrays.asList("structural-connectivity", "topological-connectivity",
                "general-connectivity", "no-relation"));
        List<Prediction> predictions = PredictionResultPreparer.loadPredictions(predictionFile);
        List<ErrorRecord> erList = new ArrayList<>();
        List<ErrorRecord> correctList = new ArrayList<>();
        int fp = 0;
        int fn = 0;
        int numConnectivity = 0;
        for (Prediction prediction : predictions) {
            Pair<SentenceInfo, BaseIEFrameInfo> relInfo = map.get(prediction.getMaskedSentence());
            Assertion.assertNotNull(relInfo);
            String type = relInfo.getSecond().getType();
            int actual = noRelSet.contains(type) ? 0 : 1;
            if (actual == 1) {
                numConnectivity++;
                if (prediction.getRelProb() < 0.5) {
                    ErrorRecord er = prepErrorRecord(prediction, relInfo, noRelSet);
                    erList.add(er);
                    fn++;
                } else {
                    ErrorRecord correct = prepErrorRecord(prediction, relInfo, noRelSet);
                    correctList.add(correct);
                }
            } else {
                if (prediction.getRelProb() >= 0.5) {
                    ErrorRecord er = prepErrorRecord(prediction, relInfo, noRelSet);
                    erList.add(er);
                    fp++;
                }
            }
        }
        System.out.println("Errors\n=========");
        for (ErrorRecord er : erList) {
            System.out.println(er);
            System.out.println("-------------------");
        }
        System.out.println("# of errors:" + erList.size() + " out of " + predictions.size());
        System.out.println("FP:" + fp + " FN:" + fn + " # of connectibity relations:" + numConnectivity);
        System.out.println("Correct connectivity records");
        System.out.println("============================");
        for (ErrorRecord cr : correctList) {
            System.out.println(cr);
            System.out.println("----------------------");
        }
        System.out.println("# of correct connectivity predictions: " + correctList.size());
    }


    public static ErrorRecord prepErrorRecord(Prediction prediction, Pair<SentenceInfo, BaseIEFrameInfo> relInfo, Set<String> noRelSet) {
        String sentence = relInfo.getFirst().getText().getText();
        sentence = sentence.replaceAll("\\n", "");
        String type = relInfo.getSecond().getType();
        String actual = noRelSet.contains(type) ? "no-relation" : "connectivity";
        String predicted = "no-relation";
        if (prediction.getRelProb() >= 0.5) {
            predicted = "connectivity";
        }
        float score = prediction.getRelProb();
        if (predicted.equals("no-relation")) {
            score = 1.0f - prediction.getRelProb();
        }
        ErrorRecord er = new ErrorRecord(actual, predicted, score);
        BaseIEFrameInfo bfi = relInfo.getSecond();
        BaseSlotInfo subject = bfi.getSlots().get(0);
        BaseSlotInfo object = bfi.getSlots().get(1);
        if (subject.getStartIdx() > object.getStartIdx()) {
            subject = bfi.getSlots().get(1);
            object = bfi.getSlots().get(0);
        }
        er.addBlock(new TextBlock(sentence.substring(0, subject.getStartIdx())));
        er.addBlock(new TextBlock(sentence.substring(subject.getStartIdx(), subject.getEndIdx()), "structure"));
        er.addBlock(new TextBlock(sentence.substring(subject.getEndIdx(), object.getStartIdx())));
        er.addBlock(new TextBlock(sentence.substring(object.getStartIdx(), object.getEndIdx()), "structure"));
        er.addBlock(new TextBlock(sentence.substring(object.getEndIdx())));
        return er;
    }

    public static class ErrorRecord {
        String actual;
        String predicted;
        float score;
        List<TextBlock> blocks = new ArrayList<>(5);

        public ErrorRecord(String actual, String predicted, float score) {
            this.actual = actual;
            this.predicted = predicted;
            this.score = score;
        }

        public void addBlock(TextBlock textBlock) {
            blocks.add(textBlock);
        }

        public String getActual() {
            return actual;
        }

        public String getPredicted() {
            return predicted;
        }

        public float getScore() {
            return score;
        }

        public List<TextBlock> getBlocks() {
            return blocks;
        }

        public String toString() {
            StringBuilder sb = new StringBuilder();
            for (TextBlock block : blocks) {
                if (block.getType().equals("structure")) {
                    sb.append("**").append(block.getText()).append("**");
                } else {
                    sb.append(block.getText());
                }
            }
            sb.append("\nActual: ").append(actual);
            sb.append("\nPredicted: ").append(predicted).append(" score:").append(score);

            return sb.toString();
        }
    }

    public static class TextBlock {
        final String text;
        final String type;

        public TextBlock(String text, String type) {
            this.text = text;
            this.type = type;
        }

        public TextBlock(String text) {
            this(text, "none");
        }

        public String getText() {
            return text;
        }

        public String getType() {
            return type;
        }
    }


    public static void main(String[] args) throws Exception {
        ErrorReportGenerator generator = new ErrorReportGenerator(false);
        String predictionFile = BASE_DIR + "/active_learning/full_opt_test_out.txt";
        // generator.prepReport(predictionFile);
        predictionFile = BASE_DIR + "/active_learning/multi_rel_test_out.txt";
        generator.prepMultiRelReport(predictionFile);

    }
}
