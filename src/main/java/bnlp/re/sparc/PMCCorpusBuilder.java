package bnlp.re.sparc;

import bnlp.common.CharSetEncoding;
import bnlp.common.index.DocumentInfo;
import bnlp.common.index.FileInfo;
import bnlp.common.index.SentenceInfo;
import bnlp.common.index.TextInfo;
import bnlp.re.common.PhraseLookupManager;
import bnlp.re.common.Tokenizer;
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
import java.util.*;

/**
 * Created by bozyurt on 10/22/20.
 */
public class PMCCorpusBuilder extends BaseCorpusBuilder {
    String rootDir;
    File[] journalDirs;

    public PMCCorpusBuilder(String rootDir, PhraseLookupManager plm) {
        super(plm);
        this.rootDir = rootDir;
        Assertion.assertExistingPath(rootDir, rootDir);
        journalDirs = new File(rootDir).listFiles();
    }

    @SuppressWarnings("Duplicates")
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
                                    CleanupUtils.filterSameBinaryEntities(si);
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
                                GenUtils.prepCreatorComment(PMCCorpusBuilder.class
                                        .getName()));
                        fi.saveAsXML(outIdxXmlFile, comment, CharSetEncoding.UTF8);
                        System.out.println("saved " + outIdxXmlFile);
                    }
                }
            }
        }
        Comment comment = new Comment(
                GenUtils.prepCreatorComment(PMCCorpusBuilder.class
                        .getName()));
        fi.saveAsXML(outIdxXmlFile, comment, CharSetEncoding.UTF8);
        System.out.println("saved final " + outIdxXmlFile);

    }

    public boolean isPaperEligible(File paperPath) throws IOException {
        String content = FileUtils.loadAsString(paperPath.getAbsolutePath(),
                CharSetEncoding.UTF8);
        content = content.toLowerCase();
        Set<String> uniqueSet = new HashSet<>();
        for (String entity : plm.getFirstWordLT()) {
            int idx = content.indexOf(entity);
            if (idx != -1) {
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
        formatter.printHelp("PMCCorpusBuilder", options);
        System.exit(1);
    }

    public static void main(String[] args) throws Exception {
        Option help = new Option("h", "print this message");
        Option corpusDirOpt = Option.builder("d").required().hasArg().argName("corpusDir")
                .desc("the full path to the PMC OAI corpus root dir").build();
        Option outIdxXmlFileOpt = Option.builder("o").required().hasArg().argName("outIdxXmlFile")
                .desc("the full path for the index XML file to be written").build();
        Option vocabFileOpt = Option.builder("v").required().hasArg().argName("vocabFilePath")
                .desc("the full path fdr the vocabulary file").build();
        Options options = new Options();
        options.addOption(help);
        options.addOption(corpusDirOpt);
        options.addOption(outIdxXmlFileOpt);
        options.addOption(vocabFileOpt);

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
        // String HOME_DIR = System.getProperty("user.home");
        String vocabFile = line.getOptionValue("v");
        // String vocabFile = HOME_DIR + "/dev/java/bnlp-re/data/sparc/sparc_vocabulary_combined.txt";

        String[] vocabulary = FileUtils.readLines(vocabFile, true, CharSetEncoding.UTF8);
        PhraseLookupManager plm = new PhraseLookupManager(Arrays.asList(vocabulary), true,
                new Tokenizer());

        PMCCorpusBuilder builder = new PMCCorpusBuilder(corpusDir, plm);

        builder.handle(outIdxXmlFile);
    }


}
