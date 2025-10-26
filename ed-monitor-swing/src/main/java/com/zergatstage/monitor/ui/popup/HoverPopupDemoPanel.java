package com.zergatstage.monitor.ui.popup;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.util.List;

/**
 * Minimal playground panel that demonstrates HoverInfoPopup usage without wiring it into any production screen.
 */
public final class HoverPopupDemoPanel extends JPanel {

    public HoverPopupDemoPanel() {
        setLayout(new FlowLayout(FlowLayout.LEFT, 16, 16));
        setPreferredSize(new Dimension(640, 360));
        setBorder(BorderFactory.createEmptyBorder(24, 24, 24, 24));

        HoverInfoPopup popup = new HoverInfoPopup();

        add(buildSampleLabel(popup, "Painite", "Plentiful at Jameson Memorial", "4,231,890 CR/t", "Avg demand 8,500 t"));
        add(buildSampleLabel(popup, "Tritium", "Fuel for Fleet Carriers", "54,210 CR/t", "Supply 12,200 t"));
        add(buildSampleLabel(popup, "Meta-Alloys", "Barnacle grown", "102,555 CR/t", "Shortage"));

        JButton button = new JButton("Hover me too");
        popup.attach(button, e -> createInfoCard("Hover button", List.of(
                new InfoCardData.Row("Purpose", "Shows the popup works with any component"),
                new InfoCardData.Row("Delay", "Default ~250 ms debounce")
        )));
        add(button);
    }

    private JLabel buildSampleLabel(HoverInfoPopup popup, String title, String sub, String price, String status) {
        JLabel label = new JLabel(title);
        label.setOpaque(true);
        label.setBackground(new Color(0xE5, 0xE9, 0xF2));
        label.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
        label.setFont(label.getFont().deriveFont(Font.BOLD));
        popup.attach(label, e -> createInfoCard(title, List.of(
                new InfoCardData.Row("Details", sub),
                new InfoCardData.Row("Price", price),
                new InfoCardData.Row("Status", status)
        )));
        return label;
    }

    private InfoCardData createInfoCard(String title, List<InfoCardData.Row> rows) {
        return new InfoCardData(title, rows);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Hover popup demo");
            frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            frame.setContentPane(new HoverPopupDemoPanel());
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }
}
