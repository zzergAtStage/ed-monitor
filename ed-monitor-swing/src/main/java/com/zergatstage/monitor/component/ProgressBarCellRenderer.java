package com.zergatstage.monitor.component;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import java.awt.*;

/**
 * A cell renderer that displays an integer value as a progress bar (0–100).
 */
public class ProgressBarCellRenderer extends JProgressBar implements TableCellRenderer {

    public ProgressBarCellRenderer() {
        super(0, 100);
        setStringPainted(true); // Show numeric percentage on the bar
    }

    @Override
    public Component getTableCellRendererComponent(
            JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {

        if (value == null) {
            setValue(0);
            setString("0%");
        } else {
            int progress = (int) value;
            setValue(progress);
            setString(progress + "%");
        }

        // Optionally change color if selected
        if (isSelected) {
            setBackground(table.getSelectionBackground());
        } else {
            setBackground(Color.LIGHT_GRAY);
        }
        return this;
    }
}
