package bnlp.re.jats;


import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * Created by bozyurt on 11/28/16.
 */
public class ArticleInfo {
    String filePath;
    String journalTitle;
    String title;
    private String PMID;
    private String PMCID;
    private String DOI;
    private String pubDate;
    List<SectionInfo> siList = new ArrayList<SectionInfo>(10);
    List<AbstractInfo> abstractList = new ArrayList<AbstractInfo>(2);
    List<TableInfo> tableList = new LinkedList<>();
    List<String> paraList = new ArrayList<String>();
    List<URLLocInfo> urlLocInfos = new ArrayList<>(1);

    public ArticleInfo(String filePath) {
        this.filePath = filePath;
    }

    public List<URLLocInfo> getUrlLocInfos() {
        return urlLocInfos;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("ArticleInfo [");
        if (filePath != null) {
            builder.append("filePath=");
            builder.append(filePath);
            builder.append(", ");
        }
        if (journalTitle != null) {
            builder.append("journalTitle=");
            builder.append(journalTitle);
            builder.append(", ");
        }
        if (title != null) {
            builder.append("title=");
            builder.append(title);
            builder.append(", ");
        }
        builder.append("PMID=").append(PMID).append(",");
        for (AbstractInfo ai : abstractList) {
            builder.append("\n\t").append(ai);
        }

        if (siList != null) {
            for (SectionInfo si : siList) {
                builder.append("\n\t").append(si);
            }
        }
        for (TableInfo ti : tableList) {
            builder.append("\n").append(ti);
        }
        builder.append("]");
        return builder.toString();
    }

    public String getFilePath() {
        return filePath;
    }

    public String getJournalTitle() {
        return journalTitle;
    }

    public String getTitle() {
        return title;
    }

    public String getPMID() {
        return PMID;
    }

    public String getPMCID() {
        return PMCID;
    }

    public String getDOI() {
        return DOI;
    }

    public void setDOI(String DOI) {
        this.DOI = DOI;
    }

    public List<SectionInfo> getSiList() {
        return siList;
    }

    public boolean containsSectionOrSubsection(SectionInfo si) {
        if (siList.contains(si)) {
            return true;
        }
        for (SectionInfo asi : siList) {
            if (asi.getSubsections().contains(si)) {
                return true;
            }
        }
        return false;
    }

    public List<String> getParaList() {
        return paraList;
    }

    void setPMID(String PMID) {
        this.PMID = PMID;
    }

    void setPMCID(String PMCID) {
        this.PMCID = PMCID;
    }

    void addAbstractInfo(AbstractInfo ai) {
        this.abstractList.add(ai);
    }

    public List<AbstractInfo> getAbstractList() {
        return abstractList;
    }

    void addTable(TableInfo ti) {
        this.tableList.add(ti);
    }

    public List<TableInfo> getTableList() {
        return tableList;
    }

    public String getPubDate() {
        return pubDate;
    }

    public void setPubDate(String pubDate) {
        this.pubDate = pubDate;
    }

    public SectionInfo getMethodsSection() {
        if (siList == null) {
            return null;
        }
        for (SectionInfo si : siList) {
            if (si.getTitle() != null) {
                String sectionTitle = si.getTitle().toLowerCase();
                if (sectionTitle.equals("techniques")
                        || sectionTitle.contains("methods")
                        || sectionTitle.contains("procedures")
                        || sectionTitle.contains("method")
                        || sectionTitle.contains("procedure")
                        || sectionTitle.contains("experiment")) {
                    return si;
                }
            }
        }
        return null;
    }
}// ;
