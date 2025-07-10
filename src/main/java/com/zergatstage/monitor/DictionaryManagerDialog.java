package com.zergatstage.monitor;

import com.zergatstage.domain.dictionary.CommodityManager;

import javax.swing.*;
import java.awt.*;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * DictionaryDialog is a modal window used to manage application dictionaries.
 * These include station requirements, supported station types, and other static configuration.
 * <p>
 * The window mimics IntelliJ IDEA settings style using a split pane layout.
 */
public class DictionaryManagerDialog extends JDialog {

    private final JList<String> categoryList;
    private final CardLayout cardLayout;
    private final JPanel cardPanel;

    private final Map<String, JPanel> categoryViews = new LinkedHashMap<>();

    /**
     * Constructs the Dictionary dialog.1
     *
     * @param parent the parent frame that owns this dialog
     */
    public DictionaryManagerDialog(Frame parent) {
        super(parent, "Manage Dictionary", true); // modal dialog
        setSize(700, 500);
        setLocationRelativeTo(parent);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        cardLayout = new CardLayout();
        cardPanel = new JPanel(cardLayout);
        // Left panel (category list)
        categoryList = new JList<>();
        categoryList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        categoryList.addListSelectionListener(e -> {
            String selected = categoryList.getSelectedValue();
            if (selected != null) {
                cardLayout.show(cardPanel, selected);
            }
        });

        JScrollPane categoryScroll = new JScrollPane(categoryList);
        categoryScroll.setPreferredSize(new Dimension(200, 0));


        // Register all dictionary views
        addDictionaryPage("Station Requirements", createStationRequirementsPanel());
        addDictionaryPage("Supported Station Types", createStationTypesPanel());
        addDictionaryPage("Commodity Manager", createCommodityManagerPanel());

        // Setup main split pane
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, categoryScroll, cardPanel);
        splitPane.setDividerLocation(200);

        getContentPane().add(splitPane, BorderLayout.CENTER);

        // Load the first view
        if (!categoryViews.isEmpty()) {
            categoryList.setListData(categoryViews.keySet().toArray(new String[0]));
            categoryList.setSelectedIndex(0);
        }
    }

    private JPanel createCommodityManagerPanel() {
        return new CommodityManager();
    }

    /**
     * Adds a new dictionary section page.
     *
     * @param name  the name displayed in the category list
     * @param panel the corresponding detail panel
     */
    private void addDictionaryPage(String name, JPanel panel) {
        categoryViews.put(name, panel);
        cardPanel.add(panel, name);
    }

    /**
     * Creates the panel for managing station requirements.
     *
     * @return JPanel representing the editable station requirements.
     */
    private JPanel createStationRequirementsPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(new JLabel("Station Requirements Management (TODO)"), BorderLayout.NORTH);

        // Placeholder content: future table, form, etc.
        JTextArea textArea = new JTextArea("Define fuel, services, repairs, etc...");
        panel.add(new JScrollPane(textArea), BorderLayout.CENTER);

        return panel;
    }

    /**
     * Creates the panel for managing station types.
     *
     * @return JPanel representing the station types list.
     */
    private JPanel createStationTypesPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(new JLabel("Station Types Management (TODO)"), BorderLayout.NORTH);

        // Placeholder content
        DefaultListModel<String> model = new DefaultListModel<>();
        model.addElement("Outpost");
        model.addElement("Surface Port");
        model.addElement("Mega Ship");
        model.addElement("SpaceConstructionDepot");

        JList<String> list = new JList<>(model);
        panel.add(new JScrollPane(list), BorderLayout.CENTER);

        return panel;
    }
}
