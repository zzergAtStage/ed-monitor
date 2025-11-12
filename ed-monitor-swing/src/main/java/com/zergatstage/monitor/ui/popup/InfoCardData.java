package com.zergatstage.monitor.ui.popup;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Simple UI model used by {@link HoverInfoPopup} to describe what should be rendered inside the card.
 */
public final class InfoCardData {

    public String title;
    public List<Row> rows;

    public InfoCardData() {
        this(null, null);
    }

    public InfoCardData(String title, List<Row> rows) {
        this.title = title;
        this.rows = rows != null ? new ArrayList<>(rows) : new ArrayList<>();
    }

    public InfoCardData addRow(String label, String value) {
        ensureRows();
        this.rows.add(new Row(label, value));
        return this;
    }

    public List<Row> rowsView() {
        ensureRows();
        return Collections.unmodifiableList(rows);
    }

    public record Row(String label, String value) {
        public Row {
            Objects.requireNonNull(label, "label");
            Objects.requireNonNull(value, "value");
        }
    }

    private void ensureRows() {
        if (this.rows == null) {
            this.rows = new ArrayList<>();
        }
    }
}
