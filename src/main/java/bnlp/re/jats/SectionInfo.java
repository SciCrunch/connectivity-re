package bnlp.re.jats;

import java.util.LinkedList;
import java.util.List;

/**
 * Created by bozyurt on 11/28/16.
 */
public class SectionInfo {
    String type;
    String title;
    String content;
    List<SectionInfo> subsections = new LinkedList<>();

    public SectionInfo() {
    }

    public void setTitle(String title) {
        this.title = title;
    }

    void setContent(String content) {
        this.content = content;
    }

    void addSubsection(SectionInfo si) {
        subsections.add(si);
    }

    public String getContent() {
        return content;
    }


    public List<SectionInfo> getSubsections() {
        return subsections;
    }

    public String getFullContent() {
        StringBuilder sb = new StringBuilder(10000);
        collectContent(this, sb);
        return sb.toString().trim();
    }

    void collectContent(SectionInfo si, StringBuilder sb) {
        if (si == null) {
            return;
        }
        if (si.getTitle() != null) {
            sb.append(si.getTitle());
            if (!si.getTitle().endsWith(".")) {
                sb.append('.');
            }
            sb.append("\n ");
        }
        if (si.getContent() != null) {
            sb.append(si.getContent()).append("\n ");
        }
        for (SectionInfo ssi : si.subsections) {
            collectContent(ssi, sb);
        }
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("SectionInfo [");
        if (title != null) {
            builder.append("title=");
            builder.append(title);
            builder.append(", ");
        }
        if (type != null) {
            builder.append("type=");
            builder.append(type);
        }
        if (content != null) {
            builder.append(", content=").append(content);
        }
        for (SectionInfo si : subsections) {
            builder.append("\n\t\t" + si.toString());
        }

        builder.append("]");
        return builder.toString();
    }

    public String getType() {
        return type;
    }

    public String getTitle() {
        return title;
    }
}// ;
