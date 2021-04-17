package bnlp.re.jats;

/**
 * Created by bozyurt on 11/1/16.
 */

public class URLLocInfo {
    final String url;
    final String textOccurred;

    public URLLocInfo(String url, String textOccured) {
        super();
        this.url = url;
        this.textOccurred = textOccured;
    }

    public String getUrl() {
        return url;
    }

    public String getTextOccurred() {
        return textOccurred;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("URLLocInfo [");
        if (url != null) {
            builder.append("url=");
            builder.append(url);
            builder.append(", ");
        }
        if (textOccurred != null) {
            builder.append("textOccurred=");
            builder.append(textOccurred);
        }
        builder.append("]");
        return builder.toString();
    }

}

