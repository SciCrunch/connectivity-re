package bnlp.re.jats;


import bnlp.common.CharSetEncoding;
import bnlp.re.util.FileUtils;
import bnlp.util.GenUtils;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import java.util.*;

/**
 * Created by bozyurt on 10/2/17.
 */
public class StAXPaperReader {
    private StringBuilder textBuf = new StringBuilder(4096);
    private List<String> urls = new ArrayList<String>();
    private List<URLLocInfo> uliList = new ArrayList<URLLocInfo>();
    boolean inArticleID = false;
    boolean inParagraph = false;
    boolean inTitleGroup = false;
    private String articleType;
    private StringBuilder tagContentBuf = new StringBuilder(128);
    private StringBuilder paraBuf = new StringBuilder(256);
    private int urlStartIdx = -1;
    private Set<String> paraUrlSet = new HashSet<String>(7);
    private boolean inAuthorNotes = false;
    private boolean inPermissions = false;
    private boolean inRef = false;
    private boolean inTable = false;
    private boolean inTD = false;
    private boolean inTH = false;
    private boolean inTR = false;
    private boolean inThead = false;
    private boolean inTbody = false;
    private boolean inTableWrap = false;
    private boolean inCaption = false;
    private boolean inAbstract = false;

    /**
     * true if some text is added inside a tag
     */
    private boolean textAdded = false;
    private ArticleInfo ai = null;
    private SectionInfo curSi;
    boolean inSec = false, inTitle = false, inJournalTitle = false;
    boolean inArticleTitle = false;
    private StringBuilder buf = new StringBuilder(256);
    private StringBuilder sectionBuf = new StringBuilder(1024);
    private StringBuilder absBuf = new StringBuilder(1024);
    private StringBuilder tableBuf = new StringBuilder(128);

    private OpType opType = OpType.URL;
    private boolean inEligibleSec = false;
    Stack<String> sectionStack = new Stack<String>();
    Stack<SectionInfo> globalSectionStack = new Stack<SectionInfo>();
    private Set<String> tagSet = new HashSet<String>();
    boolean inSup = false;
    AbstractInfo curAbstract;
    TableInfo curTable;
    TableInfo.RowInfo curRow;
    private boolean inEntity = false;
    /**
     * indicates references to bibliography
     */
    boolean inXref = false;

    public static enum OpType {
        URL, NER
    }

    public StAXPaperReader(String filePath, OpType opType) {
        this.ai = new ArticleInfo(filePath);
        this.opType = opType;
    }

    public StAXPaperReader(String filePath) {
        this(filePath, OpType.URL);
    }


    public ArticleInfo handle() throws Exception {
        XMLInputFactory factory = XMLInputFactory.newInstance();
        XMLStreamReader reader = factory.createXMLStreamReader(FileUtils.getBufferedReader(ai.getFilePath(), CharSetEncoding.UTF8));
        while (reader.hasNext()) {
            handleEvent(reader);
            reader.next();
        }
        return ai;
    }

    public List<String> getUrls() {
        return urls;
    }

    public String getPMID() {
        return ai.getPMID();
    }

    public String getPMCID() {
        return ai.getPMCID();
    }

    public List<URLLocInfo> getUliList() {
        return uliList;
    }

    public List<String> getParaList() {
        return ai.getParaList();
    }

    public ArticleInfo getArticleInfo() {
        return ai;
    }

    void handleEvent(XMLStreamReader reader) {
        switch (reader.getEventType()) {
            case XMLStreamConstants.START_ELEMENT:
                handleStartElement(reader);
                break;
            case XMLStreamConstants.END_ELEMENT:
                handleEndElement(reader);
                break;
            case XMLStreamConstants.CHARACTERS:
                handleCharacters(reader);
                break;
        }
    }

    void handleStartElement(XMLStreamReader reader) {
        String localName = reader.getLocalName();
        // String uri = reader.getNamespaceURI();
        localName = localName.toLowerCase();
        textAdded = false;
        if (localName.equals("ext-link")) {
            String url = getAttrValue(reader, "xlink:href");
            if (url != null) {
                if (!inAuthorNotes && !inPermissions & !inRef) {
                    urls.add(url);
                    paraUrlSet.add(url);
                }
            }
        } else if (localName.equals("article-id")) {
            String type = getAttrValue(reader, "pub-id-type");
            if (type != null) {
                articleType = type;
            }
            inArticleID = true;
        } else if (localName.equals("title-group")) {
            inTitleGroup = true;
        } else if (localName.equals("p")) {
            urlStartIdx = urls.size() - 1;
            inParagraph = true;
        } else if (localName.equals("author-notes")) {
            inAuthorNotes = true;
        } else if (localName.equals("permissions")) {
            inPermissions = true;
        } else if (localName.equals("title")) {
            inTitle = true;
        } else if (localName.equals("journal-title")) {
            inJournalTitle = true;
        } else if (localName.equals("article-title")) {
            inArticleTitle = true;
        } else if (localName.equals("sec")) {
            inSec = true;
            curSi = new SectionInfo();
            String type = getAttrValue(reader, "sec-type");
            if (type != null) {
                curSi.type = type;
            }
            globalSectionStack.push(curSi);
            sectionBuf.setLength(0);
            if (this.inEligibleSec) {
                sectionStack.push("sec");
            }
        } else if (localName.equals("ref")) {
            inRef = true;
        } else if (localName.equals("table-wrap")) {
            inTableWrap = true;
            curTable = new TableInfo();
            ai.addTable(curTable);
        } else if (localName.equals("table")) {
            inTable = true;
        } else if (localName.equals("caption")) {
            inCaption = true;
        } else if (localName.equals("thead")) {
            inThead = true;
        } else if (localName.equals("tbody")) {
            inTbody = true;
        } else if (localName.equals("tr")) {
            if (inTbody) {
                curRow = new TableInfo.RowInfo();
                if (curTable == null) {
                    System.out.println("No table wrapper in " + ai.getFilePath());
                    curTable = new TableInfo();
                }
                curTable.addRow(curRow);
            }
            inTR = true;
        } else if (localName.equals("th")) {
            inTH = true;
        } else if (localName.equals("td")) {
            inTD = true;
        } else if (localName.equals("sup")) {
            inSup = true;
        } else if (localName.equals("xref")) {
            inXref = true;
        } else if (localName.equals("abstract")) {
            String type = getAttrValue(reader, "abstract-type");
            this.curAbstract = new AbstractInfo(type);
            inAbstract = true;
        }
    }

    String getAttrValue(XMLStreamReader reader, String attrName) {
        int idx = attrName.indexOf(':');
        String prefix = null;
        if (idx != -1) {
            prefix = attrName.substring(0, idx);
            attrName = attrName.substring(idx + 1);
        }
        for (int i = 0; i < reader.getAttributeCount(); i++) {
            String localAttrName = reader.getAttributeLocalName(i);
            if (attrName.equals(localAttrName)) {
                if (prefix == null || prefix.equals(reader.getAttributePrefix(i))) {
                    return reader.getAttributeValue(i);
                }
            }
        }
        return null;
    }

    void handleEndElement(XMLStreamReader reader) {
        String localName = reader.getLocalName();
        localName = localName.toLowerCase();

        boolean wasInSupOrSub = false;
        if (localName.equals("article-id")) {
            if (opType != OpType.NER) {
                addEOS();
            }
            inArticleID = false;
            if (articleType.equals("pmid")) {
                ai.setPMID(tagContentBuf.toString().trim());
            } else if (articleType.equals("pmc")) {
                ai.setPMID(tagContentBuf.toString().trim());
            }
        } else if (localName.equals("p")) {
            if (opType != OpType.NER) {
                addEOS();
            }
            inParagraph = false;
            String para = paraBuf.toString().trim();
            ai.paraList.add(para);
            if (!urls.isEmpty() && !paraUrlSet.isEmpty()) {
                int startOffset = Math.max(0, urlStartIdx);
                for (int i = startOffset; i < urls.size(); i++) {
                    String url = urls.get(i);
                    uliList.add(new URLLocInfo(url, para));
                }
            }
            paraBuf.setLength(0);
            paraUrlSet.clear();
        } else if (localName.equals("ext-link") && inTable) {
            String para = paraBuf.toString().trim();
            if (!GenUtils.isEmpty(para)) {
                ai.paraList.add(para);
            }
            if (!urls.isEmpty() && !paraUrlSet.isEmpty()) {
                int startOffset = Math.max(0, urlStartIdx);
                for (int i = startOffset; i < urls.size(); i++) {
                    String url = urls.get(i);
                    uliList.add(new URLLocInfo(url, !GenUtils.isEmpty(para) ? para : url));
                }
            }
            paraBuf.setLength(0);
            paraUrlSet.clear();

        } else if (localName.equals("author-notes")) {
            inAuthorNotes = false;
            if (opType != OpType.NER) {
                addEOS();
            }
        } else if (localName.equals("permissions")) {
            inPermissions = false;
            if (opType != OpType.NER) {
                addEOS();
            }
        } else if (localName.equals("sec")) {
            if (opType != OpType.NER) {
                addEOS();
            }
            // Assertion.assertNotNull(curSi);
            if (curSi != null) {
                curSi.setContent(normalize(sectionBuf.toString()));
                globalSectionStack.pop();
                if (!globalSectionStack.isEmpty()) {
                    SectionInfo parentSi = globalSectionStack.peek();
                    if (!ai.getSiList().contains(parentSi)) {
                        ai.getSiList().add(parentSi);
                    }
                    parentSi.addSubsection(curSi);
                } else {
                    ai.siList.add(curSi);
                }
            } else {
                globalSectionStack.pop();
            }
            sectionBuf.setLength(0);
            inSec = false;
            if (inEligibleSec) {
                sectionStack.pop();
                if (sectionStack.empty()) {
                    this.inEligibleSec = false;
                }
            }

            curSi = null;
        } else if (localName.equals("title")) {
            if (inSec) {
                if (curSi != null) {
                    curSi.title = buf.toString().trim();
                    //ai.siList.add(curSi);
                    if (opType == OpType.NER && isSectionEligible(curSi)) {
                        this.inEligibleSec = true;
                        sectionStack.push("sec");
                    }
                }
            }
            addEOS();
            inTitle = false;
            if (inAbstract) {
                curAbstract.title = buf.toString().trim();
            }
        } else if (localName.equals("journal-title")) {
            ai.journalTitle = buf.toString().trim();
            inJournalTitle = false;
            if (opType != OpType.NER) {
                addEOS();
            }
        } else if (localName.equals("article-title")) {
            if (inTitleGroup) {
                ai.title = buf.toString().trim();
            }
            inArticleTitle = false;
            if (opType != OpType.NER) {
                addEOS();
            }
        } else if (localName.equals("title-group")) {
            inTitleGroup = false;
            if (opType != OpType.NER) {
                addEOS();
            }
        } else if (localName.equals("ref")) {
            inRef = false;
        } else if (localName.equals("table-wrap")) {
            inTableWrap = false;
            curTable = null;
        } else if (localName.equals("caption")) {
            if (inTableWrap) {
                curTable.setCaption(tableBuf.toString().trim());
                tableBuf.setLength(0);
            }
            inCaption = false;
        } else if (localName.equals("table")) {
            inTable = false;
        } else if (localName.equals("thead")) {
            inThead = false;
        } else if (localName.equals("tbody")) {
            inTbody = false;
        } else if (localName.equals("tr")) {
            inTR = false;
            curRow = null;
        } else if (localName.equals("th")) {
            inTH = false;
            curTable.addHeaderColumn(tableBuf.toString().trim());
            tableBuf.setLength(0);
        } else if (localName.equals("td")) {
            inTD = false;
            if (opType != OpType.NER) {
                addEOS();
            }
            if (inTbody) {
                curRow.addCell(tableBuf.toString().trim());
                tableBuf.setLength(0);
            }
        } else if (localName.equals("sup")) {
            inSup = false;
            wasInSupOrSub = true;
        } else if (localName.equals("xref")) {
            inXref = false;
        } else if (localName.equals("abstract")) {
            inAbstract = false;
            curAbstract.content = normalize(absBuf.toString());
            ai.addAbstractInfo(curAbstract);
            curAbstract = null;
            absBuf.setLength(0);
        }
        if (!inTitle && !inJournalTitle && !inArticleTitle) {
            buf.setLength(0);
        }
        tagContentBuf.setLength(0);
        if (textAdded && textBuf.length() > 0 && !wasInSupOrSub) {
            textBuf.append(' ');
        }
        textAdded = false;
    }


    void handleCharacters(XMLStreamReader reader) {
        int start = reader.getTextStart();
        int length = reader.getTextLength();
        char[] ch = reader.getTextCharacters();
        if (inArticleID) {
            tagContentBuf.append(ch, start, length);
        }
        if (inParagraph) {
            paraBuf.append(ch, start, length);
        }
        if (!inAuthorNotes && !inPermissions) {
            if (opType != OpType.NER) {
                textBuf.append(ch, start, length).append(' ');
                if (!inTitle) {
                    sectionBuf.append(ch, start, length); // append(' ');
                }
                if (inTableWrap && inCaption) {
                    tableBuf.append(ch, start, length);
                }
                if (inTable && (inTD || inTH)) {
                    tableBuf.append(ch, start, length);
                }
            } else {
                if (inEligibleSec) {
                    if (inTable || inTD) {
                        int idx = textBuf.length() - 2;
                        if (idx >= 0 && textBuf.charAt(idx) != '.' && textBuf.charAt(idx + 1) != '.') {
                            textBuf.append(". ");
                        }
                        textBuf.append(ch, start, length).append(' ');
                    } else {
                        if (!inXref) {

                            if (ch[start] == '/' || ch[start] == ';') {
                                if (Character.isWhitespace(textBuf.charAt(textBuf.length() - 1))) {
                                    textBuf.setLength(textBuf.length() - 1);
                                }
                            }
                            if (textBuf.length() >= 2 && Character.isWhitespace(textBuf.charAt(textBuf.length() - 1))
                                    && textBuf.charAt(textBuf.length() - 2) == ';') {
                                textBuf.setLength(textBuf.length() - 1);
                            } else if (inEntity && Character.isWhitespace(textBuf.charAt(textBuf.length() - 1))) {
                                textBuf.setLength(textBuf.length() - 1);
                            }

                            textBuf.append(ch, start, length);
                            textAdded = true;
                        }
                    }
                }
            }
        }
        if (inTitle || inJournalTitle || inArticleTitle) {
            buf.append(ch, start, length);
        }
        if (inAbstract && !inTitle) {
            absBuf.append(ch, start, length);
        }
    }

    public static String normalize(String s) {
        return s.trim().replaceAll("\\s\\s+", " ").replaceAll("[\\n\\r]+", " ");
    }

    void addEOS() {
        int idx = textBuf.length() - 1;
        boolean foundPeriod = false;
        while (idx >= 0) {
            char c = textBuf.charAt(idx);
            if (!Character.isWhitespace(c) && c != '.') {
                break;
            }
            if (c == '.') {
                foundPeriod = true;
                break;
            }
            idx--;
        }
        if (!foundPeriod && textBuf.length() > 0) {
            char c = textBuf.charAt(textBuf.length() - 1);
            if (!Character.isWhitespace(c)) {
                textBuf.append(' ');
            }
            textBuf.append(". \n");
        }
    }

    private boolean isSectionEligible(SectionInfo si) {
        String sectionTitle = si.getTitle().toLowerCase();
        return sectionTitle.equals("techniques")
                || sectionTitle.contains("methods")
                || sectionTitle.contains("procedures")
                || sectionTitle.contains("method")
                || sectionTitle.contains("procedure")
                || sectionTitle.contains("experiment");
    }

    public static void printEvent(XMLStreamReader xmlr) {
        // System.out.print("EVENT:[" + xmlr.getLocation().getLineNumber() + "][" +
        //         xmlr.getLocation().getColumnNumber() + "] ");

        switch (xmlr.getEventType()) {
            case XMLStreamConstants.START_ELEMENT:
                break;
            case XMLStreamConstants.END_ELEMENT:
                break;
            case XMLStreamConstants.CHARACTERS:
                int start = xmlr.getTextStart();
                int length = xmlr.getTextLength();
                System.out.print(new String(xmlr.getTextCharacters(),
                        start,
                        length));
                break;
            case XMLStreamConstants.ENTITY_REFERENCE:
                System.out.print(xmlr.getLocalName() + "=");
                if (xmlr.hasText())
                    System.out.print("[" + xmlr.getText() + "]");
                break;
        }
    }


}
