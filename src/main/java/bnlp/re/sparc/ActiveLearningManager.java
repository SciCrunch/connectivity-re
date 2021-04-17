package bnlp.re.sparc;

import bnlp.common.CharSetEncoding;
import bnlp.common.Pair;
import bnlp.common.index.DocumentInfo;
import bnlp.common.index.FileInfo;
import bnlp.common.index.SentenceInfo;
import bnlp.re.sparc.PredictionResultPreparer.Prediction;
import bnlp.re.sparc.preprocessing.LRModelDataPreparer;
import bnlp.re.util.Assertion;
import bnlp.re.util.FileUtils;
import bnlp.util.GenUtils;
import org.jdom2.Comment;

import java.util.*;
import java.util.stream.Collectors;

import static bnlp.re.sparc.CleanupUtils.addIEFrames;

/**
 * Created by bozyurt on 2/11/21.
 */
@SuppressWarnings("Duplicates")
public class ActiveLearningManager {
    public static String HOME_DIR = System.getProperty("user.home");
    public static String AL_DIR = HOME_DIR + "/dev/java/bnlp-re/data/sparc/base/active_learning/iterations";
    public static String BASE_DIR = HOME_DIR + "/dev/java/bnlp-re/data/sparc/base/active_learning";


    public static void merge(List<String> curatedList,
                             String outIdxXmlFile) throws Exception {
        FileInfo newFI = new FileInfo("", "", "");
        Map<String, Wrapper> map = new HashMap<>();
        for (String curatedFile : curatedList) {
            System.out.println(">> " + curatedFile);
            Map<String, Wrapper> localMap = toSentenceMap(curatedFile);
            for (String key : localMap.keySet()) {
                if (map.containsKey(key)) {
                    System.out.println(">> map already contains key:" + key);
                }
                map.put(key, localMap.get(key));
            }
            System.out.println(">> map.size:" + map.size());
        }
        System.out.println("map.size:" + map.size());
        Map<Integer, List<Wrapper>> mergeMap = new HashMap<>();
        for (Wrapper wrapper : map.values()) {
            List<Wrapper> list = mergeMap.get(wrapper.docIdx);
            if (list == null) {
                list = new ArrayList<>(1);
                mergeMap.put(wrapper.docIdx, list);
            }
            list.add(wrapper);
        }
        List<Integer> docIdxList = new ArrayList<>(mergeMap.keySet());
        Collections.sort(docIdxList);
        for (Integer docIdx : docIdxList) {
            List<Wrapper> list = mergeMap.get(docIdx);
            DocumentInfo ndi = new DocumentInfo(docIdx, list.get(0).pmid);
            newFI.appendDocument(ndi);
            for (Wrapper wrapper : list) {
                SentenceInfo nsi = new SentenceInfo(wrapper.si);
                ndi.addSentenceInfo(nsi);
                addIEFrames(wrapper.si, nsi);
            }
        }

        Comment comment = new Comment(
                GenUtils.prepCreatorComment(ActiveLearningManager.class
                        .getName()));
        newFI.saveAsXML(outIdxXmlFile, comment, CharSetEncoding.UTF8);
        System.out.println("saved " + outIdxXmlFile);
    }


    public static void merge(String inIdxXmlFile, List<String> curatedList,
                             String outIdxXmlFile) throws Exception {
        FileInfo fi = new FileInfo(inIdxXmlFile, CharSetEncoding.UTF8);
        FileInfo newFI = new FileInfo("", "", "");
        Map<String, Wrapper> map = new HashMap<>();
        for (String curatedFile : curatedList) {
            map.putAll(toSentenceMap(curatedFile));
        }
        Map<Integer, List<Wrapper>> mergeMap = new HashMap<>();
        for (Wrapper wrapper : map.values()) {
            List<Wrapper> list = mergeMap.get(wrapper.docIdx);
            if (list == null) {
                list = new ArrayList<>(1);
                mergeMap.put(wrapper.docIdx, list);
            }
            list.add(wrapper);
        }
        for (DocumentInfo di : fi.getDiList()) {
            DocumentInfo ndi = new DocumentInfo(di.getDocIdx(), di.getPMID());
            newFI.appendDocument(ndi);

            for (SentenceInfo si : di.getSiList()) {
                SentenceInfo nsi = new SentenceInfo(si);
                ndi.addSentenceInfo(nsi);
                addIEFrames(si, nsi);
            }
            if (mergeMap.containsKey(di.getDocIdx())) {
                List<Wrapper> list = mergeMap.get(di.getDocIdx());
                for (Wrapper wrapper : list) {
                    SentenceInfo nsi = new SentenceInfo(wrapper.si);
                    ndi.addSentenceInfo(nsi);
                    addIEFrames(wrapper.si, nsi);
                }
                mergeMap.remove(di.getDocIdx());
            }
        }
        if (!mergeMap.isEmpty()) {
            List<Integer> docIdxList = new ArrayList<>(mergeMap.keySet());
            Collections.sort(docIdxList);
            for (Integer docIdx : docIdxList) {
                List<Wrapper> list = mergeMap.get(docIdx);
                DocumentInfo ndi = new DocumentInfo(docIdx, list.get(0).pmid);
                newFI.appendDocument(ndi);
                for (Wrapper wrapper : list) {
                    SentenceInfo nsi = new SentenceInfo(wrapper.si);
                    ndi.addSentenceInfo(nsi);
                    addIEFrames(wrapper.si, nsi);
                }
            }
        }
        Comment comment = new Comment(
                GenUtils.prepCreatorComment(ActiveLearningManager.class
                        .getName()));
        newFI.saveAsXML(outIdxXmlFile, comment, CharSetEncoding.UTF8);
        System.out.println("saved " + outIdxXmlFile);
    }


    public static Map<String, Wrapper> toSentenceMap(String idxXmlFile) throws Exception {
        FileInfo fi = new FileInfo(idxXmlFile, CharSetEncoding.UTF8);
        Map<String, Wrapper> map = new HashMap<>();
        int numSentences = 0;
        for (DocumentInfo di : fi.getDiList()) {
            numSentences += di.getSiList().size();
        }
        System.out.println("# of sentences:" + numSentences);
        for (DocumentInfo di : fi.getDiList()) {
            for (SentenceInfo si : di.getSiList()) {
                StringBuilder sb = new StringBuilder();
                sb.append(di.getDocIdx()).append(':').append(si.getSentIdx());
                String key = sb.toString();
                if (map.containsKey(key)) {
                    System.out.println("already contains key: " + key);
                }
                map.put(key, new Wrapper(si, di.getDocIdx(), di.getPMID()));
            }
        }
        return map;
    }

    public static class Wrapper {
        final SentenceInfo si;
        final int docIdx;
        final String pmid;

        public Wrapper(SentenceInfo si, int docIdx, String pmid) {
            this.si = si;
            this.docIdx = docIdx;
            this.pmid = pmid;
        }

    }

    /**
     * given an idx XML file and a list of idx XML files to be excluded from the original,
     * generates a new idx xml file with the excluded sentences
     *
     * @param initCorpusIdxXmlFile
     * @param excludeIdxXmlFiles
     * @throws Exception
     */
    public static void prepareFilteredCorpusIdxXmlFile(String initCorpusIdxXmlFile,
                                                       List<String> excludeIdxXmlFiles,
                                                       String outIdxXmlFile) throws Exception {
        FileInfo fi = new FileInfo(initCorpusIdxXmlFile, CharSetEncoding.UTF8);
        FileInfo newFI = new FileInfo("", "", "");
        Set<String> excludeSet = new HashSet<>();
        for (String excludeIdxXmlFile : excludeIdxXmlFiles) {
            excludeSet.addAll(prepSentenceSet(excludeIdxXmlFile));
        }
        for (DocumentInfo di : fi.getDiList()) {
            DocumentInfo ndi = null;
            for (SentenceInfo si : di.getSiList()) {
                String sentence = si.getText().getText();
                if (!excludeSet.contains(sentence)) {
                    if (ndi == null) {
                        ndi = new DocumentInfo(di.getDocIdx(), di.getPMID());
                        newFI.appendDocument(ndi);
                    }
                    SentenceInfo nsi = new SentenceInfo(si);
                    ndi.addSentenceInfo(nsi);
                    addIEFrames(si, nsi);
                }
            }
        }
        Comment comment = new Comment(
                GenUtils.prepCreatorComment(ActiveLearningManager.class
                        .getName()));
        newFI.saveAsXML(outIdxXmlFile, comment, CharSetEncoding.UTF8);
        System.out.println("saved " + outIdxXmlFile);
    }


    public static Set<String> prepSentenceSet(String idxXmlFile) throws Exception {
        Set<String> set = new HashSet<>();
        FileInfo fi = new FileInfo(idxXmlFile, CharSetEncoding.UTF8);
        for (DocumentInfo di : fi.getDiList()) {
            set.addAll(di.getSiList().stream().
                    filter(si -> si.hasNamedEntities()).
                    map(si -> si.getText().getText()).collect(Collectors.toList()));
        }
        return set;
    }


    /**
     * prepare TSV file for ELECTRA/BERT based connectivity prediction
     *
     * @param idxXMLFile
     * @param outTSVFile
     * @throws Exception
     */
    public static void prepareTSVFile4Prediction(String idxXMLFile, String outTSVFile) throws Exception {
        LRModelDataPreparer.prepPredictionSet(idxXMLFile, outTSVFile);
    }

    public static void prepNoRelExamples(String idxXmlFile,
                                         String predictionFile, String outIdxXmlFile,
                                         int numSamples) throws Exception {
        List<Prediction> predictions = PredictionResultPreparer.filterPredictions(predictionFile, 0.3, true);
        Random rnd = new Random(4242);
        Collections.shuffle(predictions, rnd);
        List<Prediction> candidates = new ArrayList<>(predictions.subList(0, numSamples));
        PredictionResultPreparer.prep4Annotation(idxXmlFile, candidates, outIdxXmlFile);
    }

    public static void prepAnnotSetByActiveLearning(String idxXmlFile,
                                                    String predictionFile, String outIdxXmlFile,
                                                    int numSamples) throws Exception {
        List<Prediction> predictions = PredictionResultPreparer.filterPredictions(predictionFile, 0.4);
        List<Prediction> candidates = extractLeastConfident(predictions, numSamples);
        Assertion.assertNotNull(candidates);
        PredictionResultPreparer.prep4Annotation(idxXmlFile, candidates, outIdxXmlFile);
    }

    public static List<Prediction> extractLeastConfident(List<Prediction> predictions, int numSamples) {
        double spread = 0.005;
        do {
            List<Prediction> candidates = PredictionResultPreparer.extractLeastConfident(predictions, spread);
            if (candidates.size() == numSamples) {
                return candidates;
            } else if (candidates.size() > numSamples) {
                Collections.sort(candidates, (o1, o2) ->
                        Double.compare(Math.abs(o1.relProb - 0.5), Math.abs(o2.relProb - 0.5)));
                return new ArrayList<>(candidates.subList(0, numSamples));
            } else {
                spread += 0.005;
            }
        } while (spread < 0.1);
        return null;
    }


    public static void prepareActiveLearningInitialCorpus() throws Exception {
        String rootDir = HOME_DIR + "/dev/java/bnlp-re/data/sparc";
        String inCorpusIdxXmlFile = rootDir + "/sparc_connectivity_nerve_ganglia_corpus_idx.xml";
        List<String> excludeList = new ArrayList<>();
        excludeList.add(rootDir + "/base/sparc_connectivity_nerve_ganglia_sampled_idx_joe_full.xml");
        excludeList.add(rootDir + "/base/active_learning/sparc_connectivity_nerve_ganglia_random_set_idx.xml");
        String startCorpusIdxXmlFile = rootDir + "/base/active_learning/start_corpus_idx.xml";
        prepareFilteredCorpusIdxXmlFile(inCorpusIdxXmlFile, excludeList, startCorpusIdxXmlFile);

    }

    public static void prepIter1() throws Exception {
        String rootDir = HOME_DIR + "/dev/java/bnlp-re/data/sparc";
        String startCorpusIdxXmlFile = rootDir + "/base/active_learning/start_corpus_idx.xml";
        String predInTSVFile = rootDir + "/base/active_learning/iterations/iter_1_pred_in.tsv";
        prepareTSVFile4Prediction(startCorpusIdxXmlFile, predInTSVFile);
    }

    public static void prep4Annotation(String corpusIdxXmlFile, int iter) throws Exception {
        String predOutFile = AL_DIR + "/iter_" + iter + "_pred_out.txt";
        String outIdxXmlFile = AL_DIR + "/sparc_base_al_iter_" + iter + "_4curation_idx.xml";
        prepAnnotSetByActiveLearning(corpusIdxXmlFile, predOutFile, outIdxXmlFile, 25);
    }

    public static void prepCurationFileForIter(int iter) throws Exception {
        String corpusIdxXmlFile;
        if (iter == 1) {
            corpusIdxXmlFile = BASE_DIR + "/start_corpus_idx.xml";
        } else {
            corpusIdxXmlFile = AL_DIR + "/corpus_iter_" + iter + "_idx.xml";
        }
        prep4Annotation(corpusIdxXmlFile, iter);
    }


    public static void prepNoRelCurationFile() throws Exception {
        String corpusIdxXmlFile = AL_DIR + "/corpus_iter_10_idx.xml";
        String predOutFile = AL_DIR + "/iter_10_pred_out.txt";
        String outIdxXmlFile = AL_DIR + "/sparc_no_rel_4curation_idx.xml";
        prepNoRelExamples(corpusIdxXmlFile, predOutFile, outIdxXmlFile, 20);
    }

    public static void prepPredictionFile4iter(int iter) throws Exception {
        String corpusIdxXmlFile = BASE_DIR + "/start_corpus_idx.xml";
        String outCorpusXmlFile = AL_DIR + "/corpus_iter_" + iter + "_idx.xml";
        List<String> excludeList = new ArrayList<>();
        for (int i = 1; i < iter; i++) {
            String path = AL_DIR + "/sparc_base_al_iter_" + i + "_curated_idx.xml";
            excludeList.add(path);
        }
        prepareFilteredCorpusIdxXmlFile(corpusIdxXmlFile, excludeList, outCorpusXmlFile);
        String trainTSVFile = AL_DIR + "/iter_" + iter + "_pred_in.tsv";
        prepareTSVFile4Prediction(outCorpusXmlFile, trainTSVFile);
    }

    public static void prepNoRelPredictionFile() throws Exception {
        String outCorpusXmlFile = AL_DIR + "/sparc_no_rel_curated_idx.xml";
        String predTSVFile = AL_DIR + "/no_rel_pred_in.tsv";
        prepareTSVFile4Prediction(outCorpusXmlFile, predTSVFile);
    }

    public static void prepRetrainFile4Iter(int iter) throws Exception {
        String startTrainTSVFile = BASE_DIR + "/train.tsv";
        List<String> curatedList = new ArrayList<>();
        for (int i = 1; i <= iter; i++) {
            String path = AL_DIR + "/sparc_base_al_iter_" + i + "_curated_idx.xml";
            curatedList.add(path);
        }
        String outIdxXmlFile = "/tmp/active_learning_idx.xml";
        merge(curatedList, outIdxXmlFile);
        List<Pair<String, String>> pairs = LRModelDataPreparer.prepInputData(Arrays.asList(outIdxXmlFile), false);
        String addedTSVFile = "/tmp/added_al.tsv";
        LRModelDataPreparer.save(pairs, addedTSVFile, false);
        String outTSVFile = AL_DIR + "/iter_" + iter + "_retrain.tsv";
        FileUtils.append(startTrainTSVFile, addedTSVFile, outTSVFile);
        System.out.println("saved " + outTSVFile);
    }


    public static void prepRetrainFile4RandomSet() throws Exception {
        String startTrainTSVFile = BASE_DIR + "/train.tsv";
        List<String> curatedList = new ArrayList<>();
        String path = BASE_DIR + "/sparc_connectivity_nerve_ganglia_random_set_annotated_idx.xml";
        curatedList.add(path);

        String outIdxXmlFile = "/tmp/random_active_learning_idx.xml";
        merge(curatedList, outIdxXmlFile);

        if (false) {
            List<Pair<String, String>> pairs = LRModelDataPreparer.prepInputData(Arrays.asList(outIdxXmlFile), false);
            String addedTSVFile = "/tmp/random_added.tsv";
            LRModelDataPreparer.save(pairs, addedTSVFile, false);
            String outTSVFile = AL_DIR + "/random_full_retrain.tsv";
            FileUtils.append(startTrainTSVFile, addedTSVFile, outTSVFile);
            System.out.println("saved " + outTSVFile);
        }
    }

    public static void prepCombinedTrainedFile() throws Exception {
        String startTrainTSVFile = BASE_DIR + "/train.tsv";
        List<String> curatedList = new ArrayList<>();
        String path = BASE_DIR + "/sparc_connectivity_nerve_ganglia_random_set_annotated_idx.xml";
        curatedList.add(path);
        for (int i = 1; i <= 10; i++) {
            path = AL_DIR + "/sparc_base_al_iter_" + i + "_curated_idx.xml";
            curatedList.add(path);
        }

        String outIdxXmlFile = "/tmp/full_train_idx.xml";
        merge(curatedList, outIdxXmlFile);
        List<Pair<String, String>> pairs = LRModelDataPreparer.prepInputData(Arrays.asList(outIdxXmlFile), false);
        String addedTSVFile = "/tmp/full_added.tsv";
        LRModelDataPreparer.save(pairs, addedTSVFile, false);
        String outTSVFile = AL_DIR + "/train_full.tsv";
        FileUtils.append(startTrainTSVFile, addedTSVFile, outTSVFile);
        System.out.println("saved " + outTSVFile);
    }


    public static void prepFullTrainDevSplit(double devFrac) throws Exception {
        String inTSVFile = AL_DIR + "/train_full.tsv";

        List<Pair<String, String>> pairs = LRModelDataPreparer.loadTSV(inTSVFile);
        Random rnd = new Random(4242);
        Collections.shuffle(pairs, rnd);
        int devSize = (int) (pairs.size() * devFrac);
        List<Pair<String, String>> devList = new ArrayList<>(pairs.subList(0, devSize));
        List<Pair<String, String>> trainList = new ArrayList<>(pairs.subList(devSize, pairs.size()));

        LRModelDataPreparer.save(devList, "/tmp/full_dev.tsv");
        LRModelDataPreparer.save(trainList, "/tmp/train_opt.tsv");
    }

    public static void main(String[] args) throws Exception {
        //prepareActiveLearningInitialCorpus();
        // prepIter1();
        // prepCurationFileForIter(1);
        //prepPredictionFile4iter(1);
        // prepRetrainFile4Iter(1);
        //prepPredictionFile4iter(2);
        //prepCurationFileForIter(2);

        // prepRetrainFile4Iter(2);
        // prepPredictionFile4iter(3);
        //  prepCurationFileForIter(3);

        // prepRetrainFile4Iter(3);
        // prepPredictionFile4iter(4);
        // prepCurationFileForIter(4);

        // prepRetrainFile4Iter(4);
        // prepPredictionFile4iter(5);
        // prepCurationFileForIter(5);

        // prepRetrainFile4Iter(5);
        // prepPredictionFile4iter(6);
        // prepCurationFileForIter(6);

        // prepRetrainFile4Iter(6);
        // prepPredictionFile4iter(7);
        // prepCurationFileForIter(7);

        //prepRetrainFile4Iter(7);
        // prepPredictionFile4iter(8);
        //prepCurationFileForIter(8);

        // prepRetrainFile4Iter(8);
        // prepPredictionFile4iter(9);
        // prepCurationFileForIter(9);

        // prepRetrainFile4Iter(9);
        // prepPredictionFile4iter(10);
        // prepCurationFileForIter(10);

        // prepRetrainFile4Iter(10);
        // prepRetrainFile4RandomSet();

        //prepCombinedTrainedFile();

        //prepFullTrainDevSplit(0.2);
        // prepNoRelCurationFile();

        prepNoRelPredictionFile();

    }
}
