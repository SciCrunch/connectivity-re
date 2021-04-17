package bnlp.re.sparc;

import bnlp.common.CharSetEncoding;
import bnlp.common.index.DocumentInfo;
import bnlp.common.index.FileInfo;
import bnlp.common.index.SentenceInfo;
import bnlp.common.index.TextInfo;
import bnlp.re.common.PhraseLookupManager;
import bnlp.re.common.Tokenizer;
import bnlp.re.util.Assertion;
import bnlp.re.util.FileUtils;
import bnlp.util.GenUtils;
import org.apache.commons.cli.*;
import org.jdom2.Comment;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Created by bozyurt on 1/15/21.
 */
public class TextbookCorpusBuilder extends BaseCorpusBuilder {
    String rootDir;
    File[] bookDirs;

    public TextbookCorpusBuilder(String rootDir, PhraseLookupManager plm) {
        super(plm);
        this.rootDir = rootDir;
        Assertion.assertExistingPath(rootDir, rootDir);
        bookDirs = new File(rootDir).listFiles();
    }

    public void handle(String outIdxXmlFile) throws Exception {
        FileInfo fi = new FileInfo("", "", "");
        int docIdx = 1;
        for (File bookDir : bookDirs) {
            if (!bookDir.isDirectory()) {
                continue;
            }
            String bookID = bookDir.getName().replace("_text", "");
            DocumentInfo di = new DocumentInfo(docIdx, bookID);
            fi.appendDocument(di);
            docIdx++;
            List<File> chapters = getChapters(bookDir);
            for (File chapter : chapters) {
                String content = FileUtils.loadAsString(chapter.getAbsolutePath(), CharSetEncoding.UTF8);
                List<String> sentences = sbd.tagSentenceBoundariesStreaming(content, false);
                int sentIdx = 0;
                for (String sentence : sentences) {
                    if (isSentenceEligible(sentence)) {
                        sentence = sentence.replaceAll("\n", " ");
                        if (sentence.length() > 300) {
                            continue;
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
        }
        Comment comment = new Comment(
                GenUtils.prepCreatorComment(TextbookCorpusBuilder.class
                        .getName()));
        fi.saveAsXML(outIdxXmlFile, comment, CharSetEncoding.UTF8);
        System.out.println("saved final " + outIdxXmlFile);
    }

    public static List<File> getChapters(File bookDir) {
        File[] files = bookDir.listFiles();
        List<File> chapters = new ArrayList<>(files.length);
        for (File f : files) {
            if (f.isFile() && f.getName().endsWith(".txt")) {
                chapters.add(f);
            }
        }
        return chapters;
    }


    public static void usage(Options options) {
        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp("TextbookCorpusBuilder", options);
        System.exit(1);
    }

    public static void main(String[] args) throws Exception {
        Option help = new Option("h", "print this message");
        Option corpusDirOpt = Option.builder("d").required().hasArg().argName("corpusDir")
                .desc("the full path to preprocessed textbooks' root dir").build();
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
        String vocabFile = HOME_DIR + "/dev/java/bnlp-re/scripts/nerve_ganglia_vocab.txt";
        String[] vocabulary = FileUtils.readLines(vocabFile, true, CharSetEncoding.UTF8);
        PhraseLookupManager plm = new PhraseLookupManager(Arrays.asList(vocabulary), true,
                new Tokenizer());

        TextbookCorpusBuilder builder = new TextbookCorpusBuilder(corpusDir, plm);
        builder.handle(outIdxXmlFile);
    }
}
