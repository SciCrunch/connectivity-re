package bnlp.re.sparc;

import bnlp.common.CharSetEncoding;
import bnlp.common.index.*;
import bnlp.nlp.sbt.SentenceBoundaryClassifierFactory;
import bnlp.nlp.sbt.SentenceBoundaryDetector;
import bnlp.re.jats.ArticleInfo;
import bnlp.re.jats.SectionInfo;
import bnlp.re.jats.Utils;
import bnlp.re.util.Assertion;
import bnlp.re.util.FileUtils;
import bnlp.util.GenUtils;
import org.apache.commons.cli.*;
import org.jdom2.Comment;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;


/**
 * Created by bozyurt on 9/18/20.
 */
public class CorpusBuilder {
    String rootDir;
    File[] journalDirs;
    Set<String> entitySet;
    SentenceBoundaryDetector sbd;


    public CorpusBuilder(String rootDir, Set<String> entitySet) {
        this.rootDir = rootDir;
        this.entitySet = entitySet;
        Assertion.assertExistingPath(rootDir, rootDir);
        journalDirs = new File(rootDir).listFiles();
        SentenceBoundaryDetector.Config config = new SentenceBoundaryDetector.Config();
        this.sbd = new SentenceBoundaryDetector(config,
                SentenceBoundaryClassifierFactory.SVM_CLASSIFIER,
                CharSetEncoding.UTF8);
    }

    public void handle(String outIdxXmlFile) throws Exception {
        FileInfo fi = new FileInfo("", "", "");
        int docIdx = 1;
        for (File journalDir : journalDirs) {
            if (!journalDir.isDirectory()) {
                continue;
            }
            List<File> paperList = new LinkedList<File>();
            Utils.getPapers(journalDir, paperList);
            for (File paperPath : paperList) {

                if (isPaperEligible(paperPath)) {
                    DocumentInfo di = null;
                    try {
                        ArticleInfo ai = Utils.extractArticleContent(paperPath.getAbsolutePath());
                        for (SectionInfo sectionInfo : ai.getSiList()) {
                            String content = sectionInfo.getFullContent();
                            List<String> sentences = sbd.tagSentenceBoundariesStreaming(content, false);
                            int sentIdx = 0;
                            for (String sentence : sentences) {
                                if (isSentenceEligible(sentence)) {
                                    if (di == null) {
                                        di = new DocumentInfo(docIdx, ai.getPMID());
                                        docIdx++;
                                        System.out.println(">> Adding new document...");
                                    }
                                    TextInfo ti = new TextInfo(sentence, sentIdx);
                                    TextInfo pt = new TextInfo("", sentIdx);

                                    SentenceInfo si = new SentenceInfo(di.getDocIdx(), sentIdx, ti, pt);
                                    addNamedEntities(si);
                                    CleanupUtils.filterBadNamedEntities(si);
                                    if (si.hasNamedEntities() && si.getNeList().size() > 1) {
                                        di.addSentenceInfo(si);
                                        sentIdx++;
                                    }
                                }
                            }
                        }
                        if (di != null && !di.getSiList().isEmpty()) {
                            fi.appendDocument(di);
                        }
                    } catch (Throwable t) {
                        t.printStackTrace();
                    }
                    if (fi.getDiList().size() > 0 && fi.getDiList().size() % 10 == 0) {
                        Comment comment = new Comment(
                                GenUtils.prepCreatorComment(CorpusBuilder.class
                                        .getName()));
                        fi.saveAsXML(outIdxXmlFile, comment, CharSetEncoding.UTF8);
                        System.out.println("saved " + outIdxXmlFile);
                    }
                }
            }
        }
        Comment comment = new Comment(
                GenUtils.prepCreatorComment(CorpusBuilder.class
                        .getName()));
        fi.saveAsXML(outIdxXmlFile, comment, CharSetEncoding.UTF8);
        System.out.println("saved final " + outIdxXmlFile);

    }

    void addNamedEntities(SentenceInfo si) {
        String sentence = si.getText().getText().toLowerCase();
        for (String entity : entitySet) {
            int idx = sentence.indexOf(entity);
            if (idx != -1) {
                do {
                    int startIdx = idx;
                    int endIdx = startIdx + entity.length();
                    NEInfo nei = new NEInfo("structure", String.valueOf(startIdx),
                            String.valueOf(endIdx), "machine");
                    si.addNEInfo(nei);
                    idx = sentence.indexOf(entity, idx + 1);
                } while (idx != -1);
            }
        }
    }

    public boolean isSentenceEligible(String sentence) throws IOException {
        Set<String> uniqueSet = new HashSet<>();
        for (String entity : entitySet) {
            if (sentence.contains(entity)) {
                uniqueSet.add(entity);
                if (uniqueSet.size() >= 2) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean isPaperEligible(File paperPath) throws IOException {
        String content = FileUtils.loadAsString(paperPath.getAbsolutePath(),
                CharSetEncoding.UTF8);
        content = content.toLowerCase();
        Set<String> uniqueSet = new HashSet<>();
        for (String entity : entitySet) {
            if (content.indexOf(entity) != -1) {
                uniqueSet.add(entity);
                if (uniqueSet.size() >= 2) {
                    return true;
                }
            }
        }
        return false;
    }

    public static void usage(Options options) {
        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp("CorpusBuilder", options);
        System.exit(1);
    }

    public static void main(String[] args) throws Exception {
        Option help = new Option("h", "print this message");
        Option corpusDirOpt = Option.builder("d").required().hasArg().argName("corpusDir")
                .desc("the full path to the PMC OAI corpus root dir").build();
        Option outIdxXmlFileOpt = Option.builder("o").required().hasArg().argName("outIdxXmlFile")
                .desc("the full path for the index XML file to be written").build();
        Options options = new Options();
        options.addOption(help);
        options.addOption(corpusDirOpt);
        options.addOption(outIdxXmlFileOpt);

        CommandLineParser cli = new DefaultParser();
        CommandLine line = null;
        try {
            line = cli.parse(options, args);
        } catch (Exception x) {
            System.err.println(x.getMessage());
            usage(options);
        }
        if (line.hasOption("h")) {
            usage(options);
        }
        String corpusDir = line.getOptionValue("d");
        String outIdxXmlFile = line.getOptionValue("o");


        String HOME_DIR = System.getProperty("user.home");
        String csvFile = HOME_DIR + "/dev/java/bnlp-re/data/sparc/keast_flatmap_refs.csv";

        List<ConnectivityAnnotationRecord> carList = CSVDataExtractor.loadCSV(csvFile);
        Set<String> structureSet = new HashSet<>();
        for (ConnectivityAnnotationRecord car : carList) {
            structureSet.add(car.getStructure1().toLowerCase());
            structureSet.add(car.getStructure2().toLowerCase());
        }
        structureSet.remove("");

        CorpusBuilder builder = new CorpusBuilder(corpusDir, structureSet);

        builder.handle(outIdxXmlFile);

    }

}
