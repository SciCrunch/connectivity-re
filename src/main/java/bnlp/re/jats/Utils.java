package bnlp.re.jats;

import bnlp.re.util.FileUtils;
import org.xml.sax.XMLReader;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.File;
import java.util.List;

/**
 * Created by bozyurt on 7/6/17.
 */
public class Utils {
    public static void getPapers(File journalDir, List<File> paperList) {
        File[] files = journalDir.listFiles();
        if (files == null) {
            return;
        }
        for (File f : files) {
            if (f.isDirectory()) {
                getPapers(f, paperList);
            } else {
                if (f.getName().endsWith(".nxml")) {
                    paperList.add(f);
                }
            }
        }
    }

    public static ArticleInfo extractArticleContent(String paperPath) throws Exception {
        SAXParserFactory factory = SAXParserFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setValidating(false);
        factory.setFeature(
                "http://apache.org/xml/features/nonvalidating/load-external-dtd",
                false);
        factory.setFeature("http://xml.org/sax/features/validation", false);

        SAXParser parser = factory.newSAXParser();

        XMLReader xmlReader = parser.getXMLReader();
        Paper2TextHandler handler = new Paper2TextHandler(
                paperPath, Paper2TextHandler.OpType.URL);
        xmlReader.setContentHandler(handler);
        xmlReader.parse(FileUtils.convertToFileURL(paperPath));
        return handler.getArticleInfo();
    }
}
