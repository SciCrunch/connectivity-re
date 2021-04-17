package bnlp.re.jats;

/**
 * Created by bozyurt on 11/28/16.
 */
public class AbstractInfo {
    String type;
    String content;
    String title;

    public AbstractInfo(String type) {
        this.type = type;
    }

    public String getType() {
        return type;
    }

    public String getContent() {
        return content;
    }

    public String getTitle() {
        return title;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("AbstractInfo{");
        sb.append("type='").append(type).append('\'');
        sb.append(", content='").append(content).append('\'');
        sb.append(", title='").append(title).append('\'');
        sb.append('}');
        return sb.toString();
    }
}
