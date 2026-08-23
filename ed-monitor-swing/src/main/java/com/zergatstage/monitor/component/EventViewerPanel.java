package com.zergatstage.monitor.component;

import com.zergatstage.monitor.factory.DefaultManagerFactory;
import com.zergatstage.monitor.service.managers.EventLogManager;
import com.zergatstage.monitor.service.managers.EventLogManager.JournalEventRecord;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.awt.event.ActionListener;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Semi-realtime viewer of every journal event seen by {@link EventLogManager}, including events
 * that have no dedicated {@link com.zergatstage.monitor.handlers.LogEventHandler}. Selecting a row
 * expands its full JSON payload in a detail pane within the same tab.
 */
public class EventViewerPanel extends JPanel {

    private static final String ALL_TIME = "All time";
    private static final String LAST_5_MIN = "Last 5 minutes";
    private static final String LAST_HOUR = "Last hour";
    private static final String ALL_TYPES = "All event types";

    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

    private final EventLogManager eventLogManager;
    private final EventTableModel tableModel;
    private final JTable table;
    private final JTextArea detailArea;
    private final JComboBox<String> timeFilterCombo;
    private final JComboBox<String> typeFilterCombo;
    private final ActionListener typeFilterListener;
    private final JLabel countLabel;
    private final Timer refreshCoalesceTimer;

    public EventViewerPanel() {
        this.eventLogManager = DefaultManagerFactory.getInstance().getEventLogManager();

        setLayout(new BorderLayout(6, 6));
        setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));

        // ----- Filter bar -----
        JPanel filterBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        timeFilterCombo = new JComboBox<>(new String[]{ALL_TIME, LAST_5_MIN, LAST_HOUR});
        typeFilterCombo = new JComboBox<>(new String[]{ALL_TYPES});
        countLabel = new JLabel();

        filterBar.add(new JLabel("Time:"));
        filterBar.add(timeFilterCombo);
        filterBar.add(new JLabel("Event type:"));
        filterBar.add(typeFilterCombo);
        filterBar.add(Box.createHorizontalStrut(12));
        filterBar.add(countLabel);

        timeFilterCombo.addActionListener(e -> refreshTable());
        typeFilterListener = e -> refreshTable();
        typeFilterCombo.addActionListener(typeFilterListener);

        add(filterBar, BorderLayout.NORTH);

        // ----- Event table -----
        tableModel = new EventTableModel();
        table = new JTable(tableModel);
        table.setAutoCreateRowSorter(true);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getColumnModel().getColumn(0).setPreferredWidth(90);
        table.getColumnModel().getColumn(1).setPreferredWidth(200);
        table.getColumnModel().getColumn(2).setPreferredWidth(500);

        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                showSelectedDetail();
            }
        });

        JScrollPane tableScroll = new JScrollPane(table);

        // ----- Detail area: expands the selected event in place, within the same tab -----
        detailArea = new JTextArea();
        detailArea.setEditable(false);
        detailArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        detailArea.setText("Select an event above to view its full JSON message.");
        JScrollPane detailScroll = new JScrollPane(detailArea);
        detailScroll.setPreferredSize(new Dimension(100, 220));

        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, tableScroll, detailScroll);
        splitPane.setResizeWeight(0.65);

        add(splitPane, BorderLayout.CENTER);

        // Coalesce bursts of events (e.g. full journal replay at startup) into a single refresh.
        refreshCoalesceTimer = new Timer(300, e -> refreshTable());
        refreshCoalesceTimer.setRepeats(false);

        eventLogManager.addListener(this::scheduleRefresh);

        // Age out time-windowed filters even when no new events are arriving.
        Timer agingTimer = new Timer(15_000, e -> refreshTable());
        agingTimer.start();

        refreshTable();
    }

    private void scheduleRefresh() {
        if (!refreshCoalesceTimer.isRunning()) {
            refreshCoalesceTimer.restart();
        }
    }

    private void refreshTable() {
        updateTypeFilterOptions();

        String timeFilter = (String) timeFilterCombo.getSelectedItem();
        String typeFilter = (String) typeFilterCombo.getSelectedItem();

        List<JournalEventRecord> snapshot = eventLogManager.snapshot();
        Instant cutoff = cutoffFor(timeFilter);

        List<JournalEventRecord> filtered = new ArrayList<>();
        for (JournalEventRecord record : snapshot) {
            if (cutoff != null && record.timestamp().isBefore(cutoff)) {
                continue;
            }
            if (typeFilter != null && !ALL_TYPES.equals(typeFilter) && !typeFilter.equals(record.eventType())) {
                continue;
            }
            filtered.add(record);
        }
        filtered.sort(Comparator.comparing(JournalEventRecord::timestamp).reversed());

        JournalEventRecord selectedKey = getSelectedRecord();
        tableModel.setRecords(filtered);
        countLabel.setText(filtered.size() + " event(s)");
        restoreSelection(selectedKey);
    }

    private void updateTypeFilterOptions() {
        List<String> types = eventLogManager.knownEventTypesSorted();
        if (typeFilterCombo.getItemCount() - 1 == types.size()) {
            boolean unchanged = true;
            for (int i = 0; i < types.size(); i++) {
                if (!types.get(i).equals(typeFilterCombo.getItemAt(i + 1))) {
                    unchanged = false;
                    break;
                }
            }
            if (unchanged) {
                return;
            }
        }

        String currentSelection = (String) typeFilterCombo.getSelectedItem();
        typeFilterCombo.removeActionListener(typeFilterListener);
        typeFilterCombo.removeAllItems();
        typeFilterCombo.addItem(ALL_TYPES);
        for (String type : types) {
            typeFilterCombo.addItem(type);
        }
        if (currentSelection != null && (ALL_TYPES.equals(currentSelection) || types.contains(currentSelection))) {
            typeFilterCombo.setSelectedItem(currentSelection);
        } else {
            typeFilterCombo.setSelectedItem(ALL_TYPES);
        }
        typeFilterCombo.addActionListener(typeFilterListener);
    }

    private Instant cutoffFor(String timeFilter) {
        if (LAST_5_MIN.equals(timeFilter)) {
            return Instant.now().minusSeconds(5 * 60);
        }
        if (LAST_HOUR.equals(timeFilter)) {
            return Instant.now().minusSeconds(60 * 60);
        }
        return null;
    }

    private JournalEventRecord getSelectedRecord() {
        int viewRow = table.getSelectedRow();
        if (viewRow < 0) {
            return null;
        }
        int modelRow = table.convertRowIndexToModel(viewRow);
        return tableModel.getRecordAt(modelRow);
    }

    private void restoreSelection(JournalEventRecord key) {
        if (key != null) {
            for (int modelRow = 0; modelRow < tableModel.getRowCount(); modelRow++) {
                if (key.equals(tableModel.getRecordAt(modelRow))) {
                    int viewRow = table.convertRowIndexToView(modelRow);
                    if (viewRow >= 0) {
                        table.setRowSelectionInterval(viewRow, viewRow);
                    }
                    break;
                }
            }
        }
        showSelectedDetail();
    }

    private void showSelectedDetail() {
        JournalEventRecord record = getSelectedRecord();
        if (record == null) {
            detailArea.setText("Select an event above to view its full JSON message.");
            return;
        }
        detailArea.setText(record.prettyJson());
        detailArea.setCaretPosition(0);
    }

    private static final class EventTableModel extends AbstractTableModel {
        private static final String[] COLUMNS = {"Time", "Event", "Summary"};
        private List<JournalEventRecord> records = List.of();

        void setRecords(List<JournalEventRecord> records) {
            this.records = records;
            fireTableDataChanged();
        }

        JournalEventRecord getRecordAt(int row) {
            return (row >= 0 && row < records.size()) ? records.get(row) : null;
        }

        @Override
        public int getRowCount() {
            return records.size();
        }

        @Override
        public int getColumnCount() {
            return COLUMNS.length;
        }

        @Override
        public String getColumnName(int col) {
            return COLUMNS[col];
        }

        @Override
        public boolean isCellEditable(int row, int col) {
            return false;
        }

        @Override
        public Object getValueAt(int row, int col) {
            JournalEventRecord r = records.get(row);
            return switch (col) {
                case 0 -> TIME_FMT.format(r.timestamp());
                case 1 -> r.eventType();
                case 2 -> summaryOf(r.rawJson());
                default -> "";
            };
        }

        private static String summaryOf(String rawJson) {
            String oneLine = rawJson.replace('\n', ' ');
            return oneLine.length() > 160 ? oneLine.substring(0, 160) + "…" : oneLine;
        }
    }
}
