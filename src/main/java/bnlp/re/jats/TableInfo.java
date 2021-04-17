package bnlp.re.jats;


import bnlp.util.GenUtils;

import java.util.LinkedList;
import java.util.List;

/**
 * Created by bozyurt on 11/28/16.
 */
public class TableInfo {
    String caption;
    HeaderInfo header = new HeaderInfo();
    List<RowInfo> rows = new LinkedList<RowInfo>();

    public TableInfo() {
    }

    public String getCaption() {
        return caption;
    }

    public void addHeaderColumn(String headerColumn) {
        header.addColHeader(headerColumn);
    }

    public void setCaption(String caption) {
        this.caption = caption;
    }
    public void addRow(RowInfo rowInfo) {
        rows.add(rowInfo);
    }

    public static class RowInfo {
        List<String> cells = new LinkedList<String>();
        public void addCell(String content) {
            cells.add(content);
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder("RowInfo{");
            sb.append(GenUtils.join(cells,"|"));
            sb.append('}');
            return sb.toString();
        }
    }

    public static class HeaderInfo {
        List<String> columns = new LinkedList<String>();
        public void addColHeader(String columnHeader) {
            this.columns.add(columnHeader);
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder("HeaderInfo{");
            sb.append( GenUtils.join(columns,"||"));
            sb.append('}');
            return sb.toString();
        }
    }


    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("TableInfo{");
        sb.append("caption='").append(caption).append('\'');
        sb.append("\nheader=").append(header);
        for(RowInfo ri : rows) {
            sb.append("\n").append(ri);
        }
        sb.append('}');
        return sb.toString();
    }
}
