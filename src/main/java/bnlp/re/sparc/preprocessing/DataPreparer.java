package bnlp.re.sparc.preprocessing;

import bnlp.common.CharSetEncoding;
import bnlp.common.index.*;
import bnlp.re.common.Span;
import bnlp.re.util.Assertion;
import bnlp.re.util.FileUtils;
import bnlp.re.util.SimpleSequentialIDGenerator;
import edu.stanford.nlp.pipeline.CoNLLOutputter;
import edu.stanford.nlp.pipeline.CoreDocument;
import edu.stanford.nlp.pipeline.CoreSentence;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Created by bozyurt on 10/6/20.
 */
@SuppressWarnings("Duplicates")
public class DataPreparer {
    public final static String HOME_DIR = System.getProperty("user.home");
    StanfordCoreNLP pipeline;

    public DataPreparer(StanfordCoreNLP pipeline) {
        this.pipeline = pipeline;
    }

    public JSONArray prepGCNInputData(List<String> idxXMLFiles) throws Exception {
        JSONArray jsArr = new JSONArray();
        SimpleSequentialIDGenerator idGen = new SimpleSequentialIDGenerator();
        for (String idxXMLFile : idxXMLFiles) {
            FileInfo fi = new FileInfo(idxXMLFile, CharSetEncoding.UTF8);
            for (DocumentInfo di : fi.getDiList()) {
                for (SentenceInfo si : di.getSiList()) {
                    if (si.hasAnyBaseFrames()) {
                        List<JSONObject> recList = toGCNInputJSON(si, idGen);
                        recList.forEach(jsArr::put);
                    }
                }
            }
        }
        return jsArr;
    }

    private void save(JSONArray jsArr, String outJsonFile) throws IOException {
        FileUtils.saveText(jsArr.toString(2), outJsonFile, CharSetEncoding.UTF8);
        System.out.println("saved " + outJsonFile);
    }


    public List<JSONObject> toGCNInputJSON(SentenceInfo si, SimpleSequentialIDGenerator idGen) throws Exception {
        String sentence = si.getText().getText();
        CoreDocument document = new CoreDocument(sentence);
        pipeline.annotate(document);
        ByteArrayOutputStream bout = new ByteArrayOutputStream(1000);
        CoNLLOutputter.conllPrint(document.annotation(), bout);
        String coNLLOut = bout.toString("UTF8");
        String[] rows = coNLLOut.split("\n");
        List<JSONObject> recList = new ArrayList<>(5);
        JSONArray tokens = new JSONArray();
        JSONArray posTags = new JSONArray();
        JSONArray nerTags = new JSONArray();
        JSONArray heads = new JSONArray();
        JSONArray deprel = new JSONArray();
        for (String row : rows) {
            String[] columns = row.split("[\\s\t]+");
            if (columns.length != 7) {
                System.out.println();
                System.out.println(coNLLOut);
                System.out.println("------------");
                return Collections.emptyList();
            }
            Assertion.assertEquals(7, columns.length, row);
            tokens.put(getCharniakTokValue(columns[1]));
            posTags.put(columns[3]);
            nerTags.put(columns[4]);
            heads.put(columns[5]);
            deprel.put(columns[6]);
        }
        for (BaseIEFrameInfo pairInfo : si.getBaseFrameList()) {

            JSONObject record = new JSONObject();
            int id = idGen.nextID();
            record.put("id", id);
            String relation;
            relation = pairInfo.getType();

            // map relation types to binary
            if (relation.startsWith("anatomical") || relation.startsWith("functional")) {
                relation = "connectivity";
            } else {
                relation = "no-relation";
            }

            record.put("relation", relation);
            record.put("token", tokens);
            BaseSlotInfo subject = pairInfo.getSlots().get(0);
            BaseSlotInfo object = pairInfo.getSlots().get(1);
            //EntityInfo subject = pairInfo.getEntity1();
            //EntityInfo object = pairInfo.getEntity2();
            // subject is before the object entity
            Span subjectSpan = null;
            Span objectSpan = null;
            if (subject.getMatchingText(sentence).equals(object.getMatchingText(sentence))) {
                System.out.println("Subj and object same: " + subject.getMatchingText(sentence));
            }
            if (subject.getStartIdx() < object.getStartIdx()) {
                String entity = subject.getMatchingText(sentence);
                subjectSpan = findLocation(tokens, entity, false, -1);
                if (subjectSpan != null) {
                    String objectEntity = object.getMatchingText(sentence);
                    objectSpan = findLocation(tokens, objectEntity,
                            false, subjectSpan.getEnd() + 1);
                }
            } else {
                String objectEntity = object.getMatchingText(sentence);
                objectSpan = findLocation(tokens, objectEntity, false, -1);
                if (objectSpan != null) {
                    String subjectEntity = subject.getMatchingText(sentence);
                    subjectSpan = findLocation(tokens, subjectEntity, false,
                            objectSpan.getEnd() + 1);
                }
            }
            if (subjectSpan == null || objectSpan == null) {
                System.out.println("\nSKIPPING...");
                System.out.println("subjectSpan: " + subjectSpan);
                System.out.println("objectSpan: " + objectSpan);
                continue;
            }
            Assertion.assertNotNull(subjectSpan);
            Assertion.assertNotNull(objectSpan);
            record.put("subj_start", subjectSpan.getStart());
            record.put("subj_end", subjectSpan.getEnd());
            record.put("obj_start", objectSpan.getStart());
            record.put("obj_end", objectSpan.getEnd());
            record.put("subj_type", subject.getType());
            record.put("obj_type", object.getType());
            record.put("stanford_pos", posTags);
            record.put("stanford_ner", nerTags);
            record.put("stanford_head", heads);
            record.put("stanford_deprel", deprel);
            recList.add(record);
        }
        return recList;
    }

    public Span findLocation(JSONArray tokens, String referenceText, boolean split, int startFromIdx) {
        if (startFromIdx < 0) {
            startFromIdx = 0;
        }
        CoreDocument document = new CoreDocument(referenceText);
        pipeline.annotate(document);
        CoreSentence cs = document.sentences().get(0);
        String[] refTokens = new String[cs.tokens().size()];
        for (int i = 0; i < cs.tokens().size(); i++) {
            refTokens[i] = cs.tokens().get(i).originalText();
        }
        // String[] refTokens = referenceText.split("\\s+");
        int startIdx = -1;
        int refOffset = 0;
        Span longestSpan = null;
        for (int i = startFromIdx; i < tokens.length(); i++) {
            String token = tokens.getString(i);
            boolean containsDash = token.indexOf('-') != -1 || token.indexOf('/') != -1;
            // second condition for the unrecognized period at the end of the sentence
            // third condition for hyphenated or slash words
            if (token.contains(refTokens[refOffset]) ||
                    (i + 1 == tokens.length() && token.startsWith(refTokens[refOffset]))
                    || (containsDash && token.contains(refTokens[refOffset]))) {
                if (refOffset == 0) {
                    startIdx = i;
                }
                refOffset++;
                if (refOffset == refTokens.length) {
                    return new Span(startIdx, i);
                } else if (split) {
                    longestSpan = new Span(startIdx, i);
                }
            } else {
                startIdx = -1;
                refOffset = 0;
            }
        }
        if (split && longestSpan != null) {
            return longestSpan;
        }
        return null;
    }

    public static String getCharniakTokValue(String tokValue) {
        if (tokValue.equals("-LRB-")) {
            return "(";
        }
        if (tokValue.equals("-RRB-")) {
            return ")";
        }
        if (tokValue.equals("-LSB-")) {
            return "[";
        }
        if (tokValue.equals("-RSB-")) {
            return "]";
        }
        return tokValue;
    }


    public void prepTrainTestSets(JSONArray recArr, double testFrac) throws Exception {
        List<Integer> indices = IntStream.range(0, recArr.length()).boxed().collect(Collectors.toList());
        Random rnd = new Random(374643758L);
        Collections.shuffle(indices, rnd);
        int testSize = (int) (testFrac * recArr.length());
        List<Integer> testIndices = new ArrayList<>(indices.subList(0, testSize));
        List<Integer> trainIndices = new ArrayList<>(indices.subList(testSize, indices.size()));
        JSONArray testArr = new JSONArray();
        JSONArray trainArr = new JSONArray();

        for (Integer idx : testIndices) {
            testArr.put(recArr.get(idx));
        }
        for (Integer idx : trainIndices) {
            trainArr.put(recArr.get(idx));
        }
        save(trainArr, "/tmp/train.json");
        save(testArr, "/tmp/test.json");
    }


    public static void main(String[] args) throws Exception {
        Properties props = new Properties();
        props.setProperty("annotators", "tokenize, ssplit, pos, lemma, ner, depparse");
        StanfordCoreNLP pipeline = new StanfordCoreNLP(props);
        DataPreparer dataPreparer = new DataPreparer(pipeline);
        List<String> allIdxXmlFiles = new ArrayList<>(1);
        allIdxXmlFiles.add(HOME_DIR +
                "/dev/java/bnlp-re/data/sparc/base/sparc_connectivity_nerve_ganglia_sampled_idx_joe_full.xml");
        JSONArray jsArr = dataPreparer.prepGCNInputData(allIdxXmlFiles);

        dataPreparer.prepTrainTestSets(jsArr, 0.2);

    }

}
