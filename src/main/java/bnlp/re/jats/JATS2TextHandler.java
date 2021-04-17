package bnlp.re.jats;

import bnlp.util.GenUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.util.*;

/**
 * @author bozyurt
 */
public class JATS2TextHandler extends DefaultHandler {
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
    private StringBuilder dateBuf = new StringBuilder();
    private StringBuilder ackBuf = new StringBuilder();
    private boolean inAcknowledgement = false;
    private StringBuilder fundingBuf = new StringBuilder();
    private boolean inFundingGroup = false;

    private OpType opType = OpType.URL;
    private boolean inEligibleSec = false;
    Stack<String> sectionStack = new Stack<String>();
    Stack<SectionInfo> globalSectionStack = new Stack<SectionInfo>();
    private Set<String> tagSet = new HashSet<String>();
    boolean inSup = false;
    AbstractInfo curAbstract;
    TableInfo curTable;
    TableInfo.RowInfo curRow;
    /**
     * indicates references to bibliography
     */
    boolean inXref = false;
    boolean inPubDate = false;
    String pubYear;
    String pubMonth;
    String pubDay;


    public JATS2TextHandler(String filePath, OpType opType) {
        ai = new ArticleInfo(filePath);
        this.opType = opType;
        String[] tags = {"ext-link", "article-id", "p", "author-notes", "permissions", "title",
                "journal-title", "article-title", "sec", "sec-type", "title-group", "comment"};
        Collections.addAll(tagSet, tags);
    }

    public JATS2TextHandler(String filePath) {
        this(filePath, OpType.URL);
    }


    public static String chars2String(char[] ch, int start, int length) {
        StringBuilder sb = new StringBuilder(length);
        sb.append(ch, start, length);
        return sb.toString();
    }

    @SuppressWarnings("Duplicates")
    @Override
    public void characters(char[] ch, int start, int length)
            throws SAXException {
        if (inArticleID) {
            tagContentBuf.append(ch, start, length);
        }
        if (inParagraph) {
            paraBuf.append(ch, start, length);
            if (!inSup && length > 1) {
                paraBuf.append(' ');
            }
        }
        if (!inAuthorNotes && !inPermissions) {
            if (opType != OpType.NER) {
                textBuf.append(ch, start, length).append(' ');
                if (!inTitle) {
                    if (length == 1) {
                        if (ch[start] == '′' || ch[start] == '-') {
                            // assume no space is needed between one character and the previous collected string
                            trimWSAtEnd4Buffers();
                        } else {
                            char lastCh = getLastNonWSChar(sectionBuf);
                            if (lastCh == '-') {
                                trimWSAtEnd4Buffers();
                            }
                        }
                    }
                    if (inTableWrap || inTable) {
                        addSpaceIfNotAtEnd(sectionBuf);
                    }
                    sectionBuf.append(ch, start, length);
                    if (!inSup && length > 1) {
                        sectionBuf.append(' ');
                    }
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
                        textBuf.append(ch, start, length);
                        if (!inSup) {
                            textBuf.append(' ');
                        }
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
        if (inPubDate) {
            dateBuf.append(ch, start, length);
        }
        if (inAcknowledgement) {
            ackBuf.append(ch, start, length).append(' ');
        }
        if (inFundingGroup) {
            fundingBuf.append(ch, start, length).append(' ');
        }
    }

    void addSpaceIfNotAtEnd(StringBuilder sb) {
        if (sb.length() > 0) {
            char c = sb.charAt(sb.length() - 1);
            if (!Character.isWhitespace(c)) {
                sb.append(' ');
            }
        }
    }

    @SuppressWarnings("Duplicates")
    @Override
    public void startElement(String uri, String localName, String qName,
                             Attributes attributes) throws SAXException {
        //System.out.println(">> " + localName);
        localName = localName.toLowerCase();
        textAdded = false;

        if (localName.equals("ext-link")) {
            String url = attributes.getValue("xlink:href");
            if (url != null) {
                if (!inAuthorNotes && !inPermissions & !inRef) {
                    urls.add(url);
                    paraUrlSet.add(url);
                }
            }
        } else if (localName.equals("article-id")) {
            String type = attributes.getValue("pub-id-type");
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
            if (!inAbstract) {
                inSec = true;
                // IBO 06/13/2019
                if (curSi != null && sectionBuf.length() > 0 && GenUtils.isEmpty(curSi.getContent())) {
                    curSi.setContent(normalize(sectionBuf.toString()));
                }
                curSi = new SectionInfo();
                String type = attributes.getValue("sec-type");
                if (type != null) {
                    curSi.type = type;
                }
                if (!globalSectionStack.isEmpty()) {
                    SectionInfo parentSi = globalSectionStack.peek();
                    if (!ai.containsSectionOrSubsection(parentSi)) {
                        ai.getSiList().add(parentSi);
                    }
                    parentSi.addSubsection(curSi);
                } else {
                    ai.siList.add(curSi);
                }
                globalSectionStack.push(curSi);
                sectionBuf.setLength(0);
                if (this.inEligibleSec) {
                    sectionStack.push("sec");
                }
            }
        } else if (localName.equals("ref")) {
            inRef = true;
        } else if (localName.equals("table-wrap")) {
            inTableWrap = true;
            curTable = new TableInfo();
            ai.addTable(curTable);
        } else if (localName.equals("table")) {
            inTable = true;
            if (curTable == null) {
                curTable = new TableInfo();
                ai.addTable(curTable);
            }
        } else if (localName.equals("caption")) {
            inCaption = true;
        } else if (localName.equals("thead")) {
            inThead = true;
        } else if (localName.equals("tbody")) {
            inTbody = true;
        } else if (localName.equals("tr")) {
            if (inTbody) {
                if (curTable != null) {
                    curRow = new TableInfo.RowInfo();
                    curTable.addRow(curRow);
                }
            }
            inTR = true;
        } else if (localName.equals("th")) {
            inTH = true;
        } else if (localName.equals("td")) {
            inTD = true;
        } else if (localName.equals("sup") || localName.equals("italic")) {
            inSup = true;
            trimWSAtEnd4Buffers();
        } else if (localName.equals("xref")) {
            inXref = true;
        } else if (localName.equals("abstract")) {
            String type = attributes.getValue("abstract-type");
            this.curAbstract = new AbstractInfo(type);
            inAbstract = true;
        } else if (localName.equals("pub-date")) {
            inPubDate = true;
            dateBuf.setLength(0);
        } else if (localName.equals("ack")) {
            inAcknowledgement = true;
            ackBuf.setLength(0);
        } else if (localName.equals("funding-group")) {
            inFundingGroup = true;
            fundingBuf.setLength(0);
        }
    }

    void trimWSAtEnd4Buffers() {
        if (inParagraph) {
            trimWSAtEnd(paraBuf);
        }
        if (inSec) {
            trimWSAtEnd(sectionBuf);
        }
    }

    public static char getLastNonWSChar(StringBuilder sb) {
        int i = sb.length() - 1;
        while (i >= 0) {
            if (Character.isWhitespace(sb.charAt(i))) {
                i--;
            } else {
                break;
            }
        }
        if (i < 0) {
            return (char) -1;
        }
        return sb.charAt(i);
    }

    @SuppressWarnings("Duplicates")
    public void endElement(String uri, String localName, String qName)
            throws SAXException {
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
                ai.setPMCID(tagContentBuf.toString().trim());
            } else if (articleType.equals("doi")) {
                ai.setDOI(tagContentBuf.toString().trim());
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
                    //String sectionFound = curSi != null && !GenUtils.isEmpty(curSi.getTitle()) ? GenUtils.trimIfLonger(curSi.getTitle(), 254) : null;
                    uliList.add(new URLLocInfo(url, para));
                    ai.getUrlLocInfos().add(new URLLocInfo(url, para));
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
                    //String sectionFound = curSi != null && !GenUtils.isEmpty(curSi.getTitle()) ? GenUtils.trimIfLonger(curSi.getTitle(), 254) : null;
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
            if (!inAbstract) {
                // Assertion.assertNotNull(curSi);
                if (curSi != null) {
                    curSi.setContent(normalize(sectionBuf.toString()));
                    globalSectionStack.pop();
                } else {
                    // IBO 06/13/2019
                    SectionInfo latestSI = globalSectionStack.pop();
                    if (!ai.containsSectionOrSubsection(latestSI)) {
                        ai.getSiList().add(latestSI);
                    }
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
            }
        } else if (localName.equals("title")) {
            if (inSec) {
                // IBO 06/13/2019
                if (curSi != null && !inTableWrap) {
                    curSi.title = buf.toString().trim();
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
            if (inTbody && curRow != null) {
                curRow.addCell(tableBuf.toString().trim());
                tableBuf.setLength(0);
            }
        } else if (localName.equals("sup") || localName.equals("italic")) {
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
        } else if (localName.equals("pub-date")) {
            inPubDate = false;
        } else if (inPubDate && localName.equals("year")) {
            pubYear = dateBuf.toString().trim();
            dateBuf.setLength(0);
        } else if (inPubDate && localName.equals("month")) {
            pubMonth = dateBuf.toString().trim();
            dateBuf.setLength(0);
        } else if (inPubDate && localName.equals("day")) {
            pubDay = dateBuf.toString().trim();
            dateBuf.setLength(0);
        } else if (localName.equals("ack")) {
            inAcknowledgement = false;
            SectionInfo si = new SectionInfo();
            si.setTitle("Acknowledgment");
            si.setContent(normalize(ackBuf.toString().trim()));
            ai.getSiList().add(si);
        } else if (localName.equals("funding-group")) {
            inFundingGroup = false;
            SectionInfo si = new SectionInfo();
            si.setTitle("Funding");
            si.setContent(normalize(fundingBuf.toString().trim()));
            ai.getSiList().add(si);
        }
        if (!inTitle && !inJournalTitle && !inArticleTitle) {
            buf.setLength(0);
        }
        tagContentBuf.setLength(0);
        if (textAdded && textBuf.length() > 0 && !wasInSupOrSub) {
            textBuf.append(' ');
        } else if (localName.equals("label") && inSec) {
            // to put a space between a label and title
            sectionBuf.append(' ');
        }
        textAdded = false;
    }

    public static String normalize(String s) {
        return s.trim().replaceAll("\\s\\s+", " ").replaceAll("[\\n\\r]+", " ");
    }

    @SuppressWarnings("Duplicates")
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

    public static void trimWSAtEnd(StringBuilder sb) {
        int i = sb.length() - 1;
        while (i >= 0) {
            if (Character.isWhitespace(sb.charAt(i))) {
                i--;
            } else {
                break;
            }
        }
        if (i < sb.length() - 1) {
            sb.setLength(i + 1);
        }
    }

    public String getText() {
        return textBuf.toString();
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
        // set publication date
        if (!GenUtils.isEmpty(pubYear) && NumberUtils.isDigits(pubYear) && pubYear.length() == 4) {
            StringBuilder buf = new StringBuilder();
            buf.append(pubYear).append('-');
            if (!GenUtils.isEmpty(pubMonth) && NumberUtils.isDigits(pubMonth)) {
                buf.append(String.format("%02d", NumberUtils.toInt(pubMonth))).append('-');
            } else {
                buf.append("01-");
            }
            if (!GenUtils.isEmpty(pubDay) && NumberUtils.isDigits(pubDay)) {
                buf.append(String.format("%02d", NumberUtils.toInt(pubDay)));
            } else {
                buf.append("01");
            }
            ai.setPubDate(buf.toString());
        }


        return ai;
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

}// ;