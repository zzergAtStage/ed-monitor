package com.zergatstage.monitor.ui.popup;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.IllegalComponentStateException;
import java.awt.Insets;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.HierarchyEvent;
import java.awt.event.HierarchyListener;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.Popup;
import javax.swing.PopupFactory;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import javax.swing.event.MouseInputAdapter;

import com.zergatstage.monitor.ui.popup.internal.HoverPopupPainter;

/**
 * Framework-style helper that can display lightweight hover cards for arbitrary Swing components.
 */
public final class HoverInfoPopup {

    private static final int CARD_ARC = 14;
    private static final int SHADOW_SIZE = 8;
    private static final int CONTENT_VERTICAL_GAP = 8;
    private static final int CONTENT_HORIZONTAL_PADDING = 14;
    private static final int CONTENT_VERTICAL_PADDING = 12;

    private final PopupFactory popupFactory = PopupFactory.getSharedInstance();
    private final Map<JComponent, Attachment> attachments = new WeakHashMap<>();

    private volatile int debounceMillis = 250;
    private volatile int maxWidth = 320;
    private volatile int offsetX = 16;
    private volatile int offsetY = 16;

    private Popup activePopup;

    public void attach(JComponent target, java.util.function.Function<MouseEvent, InfoCardData> contextResolver) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(contextResolver, "contextResolver");
        runOnEdt(() -> {
            Attachment existing = attachments.remove(target);
            if (existing != null) {
                existing.dispose();
            }
            Attachment attachment = new Attachment(target, contextResolver);
            attachments.put(target, attachment);
        });
    }

    public void setDebounceMillis(int ms) {
        if (ms < 0) {
            throw new IllegalArgumentException("Debounce delay must be >= 0");
        }
        debounceMillis = ms;
    }

    public void setMaxWidth(int px) {
        if (px < 120) {
            throw new IllegalArgumentException("Max width must be at least 120px");
        }
        maxWidth = px;
    }

    public void setPreferredCornerOffset(int dx, int dy) {
        offsetX = dx;
        offsetY = dy;
    }

    public void showAt(Point screenPt, InfoCardData data) {
        runOnEdt(() -> showInternal(null, screenPt, data, false));
    }

    public void hideNow() {
        runOnEdt(this::hideActivePopup);
    }

    private void showFromAttachment(JComponent owner, Point anchorPoint, InfoCardData data) {
        showInternal(owner, anchorPoint, data, true);
    }

    private void showInternal(Component owner, Point screenPt, InfoCardData data, boolean applyOffset) {
        if (screenPt == null || data == null) {
            hideActivePopup();
            return;
        }
        Point desired = new Point(screenPt);
        if (applyOffset) {
            desired.translate(offsetX, offsetY);
        }

        JComponent card = buildCardComponent(data);
        Dimension pref = card.getPreferredSize();
        if (pref.width > maxWidth) {
            pref.width = maxWidth;
        }
        card.setPreferredSize(pref);
        card.setSize(pref);

        Point placement = constrainToScreen(desired, pref, owner);

        hideActivePopup();
        Popup popup = popupFactory.getPopup(owner, card, placement.x, placement.y);
        activePopup = popup;
        popup.show();
    }

    private void hideActivePopup() {
        if (activePopup != null) {
            activePopup.hide();
            activePopup = null;
        }
    }

    private void runOnEdt(Runnable task) {
        if (SwingUtilities.isEventDispatchThread()) {
            task.run();
        } else {
            SwingUtilities.invokeLater(task);
        }
    }

    private JComponent buildCardComponent(InfoCardData data) {
        Color background = valueOrDefault(javax.swing.UIManager.getColor("ToolTip.background"), new Color(250, 250, 250));
        Color border = valueOrDefault(javax.swing.UIManager.getColor("Separator.foreground"), new Color(210, 210, 210));
        Color titleColor = valueOrDefault(javax.swing.UIManager.getColor("Label.foreground"), Color.DARK_GRAY);
        Color labelColor = titleColor.darker();
        Color valueColor = titleColor;

        Font baseFont = valueOrDefault(javax.swing.UIManager.getFont("Label.font"), new JLabel().getFont());
        Font titleFont = baseFont.deriveFont(Font.BOLD, baseFont.getSize2D() + 1f);
        Font labelFont = baseFont.deriveFont(Font.BOLD);
        Font valueFont = baseFont.deriveFont(Font.PLAIN);

        HoverCardPanel card = new HoverCardPanel(background, border);
        JPanel shadowPadding = new JPanel(new BorderLayout());
        shadowPadding.setOpaque(false);
        shadowPadding.setBorder(new EmptyBorder(SHADOW_SIZE, SHADOW_SIZE, SHADOW_SIZE, SHADOW_SIZE));

        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(new EmptyBorder(CONTENT_VERTICAL_PADDING, CONTENT_HORIZONTAL_PADDING,
                CONTENT_VERTICAL_PADDING, CONTENT_HORIZONTAL_PADDING));
        content.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.setMaximumSize(new Dimension(maxWidth, Integer.MAX_VALUE));

        List<InfoCardData.Row> rows = data.rows != null ? new ArrayList<>(data.rows) : Collections.emptyList();

        if (data.title != null && !data.title.isBlank()) {
            JLabel titleLabel = new JLabel(data.title.trim());
            titleLabel.setFont(titleFont);
            titleLabel.setForeground(titleColor);
            titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            content.add(titleLabel);
            if (!rows.isEmpty()) {
                content.add(Box.createVerticalStrut(CONTENT_VERTICAL_GAP));
            }
        }

        if (!rows.isEmpty()) {
            JPanel rowsPanel = buildRowsPanel(rows, labelFont, valueFont, labelColor, valueColor);
            rowsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
            content.add(rowsPanel);
        }

        shadowPadding.add(content, BorderLayout.CENTER);
        card.setLayout(new BorderLayout());
        card.add(shadowPadding, BorderLayout.CENTER);
        card.setMaximumSize(new Dimension(maxWidth, Integer.MAX_VALUE));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        return card;
    }

    private JPanel buildRowsPanel(List<InfoCardData.Row> rows, Font labelFont, Font valueFont, Color labelColor, Color valueColor) {
        JPanel rowsPanel = new JPanel(new java.awt.GridBagLayout());
        rowsPanel.setOpaque(false);
        rowsPanel.setMaximumSize(new Dimension(maxWidth, Integer.MAX_VALUE));

        int availableValueWidth = Math.max(80, maxWidth - (CONTENT_HORIZONTAL_PADDING * 2) - 110);

        java.awt.GridBagConstraints gbcLabel = new java.awt.GridBagConstraints();
        gbcLabel.gridx = 0;
        gbcLabel.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gbcLabel.insets = new java.awt.Insets(0, 0, 6, 8);

        java.awt.GridBagConstraints gbcValue = new java.awt.GridBagConstraints();
        gbcValue.gridx = 1;
        gbcValue.weightx = 1;
        gbcValue.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gbcValue.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gbcValue.insets = new java.awt.Insets(0, 0, 6, 0);

        for (int i = 0; i < rows.size(); i++) {
            InfoCardData.Row row = rows.get(i);
            gbcLabel.gridy = i;
            gbcValue.gridy = i;

            JLabel label = new JLabel(safe(row.label()));
            label.setFont(labelFont);
            label.setForeground(labelColor);
            label.setHorizontalAlignment(SwingConstants.LEFT);
            rowsPanel.add(label, gbcLabel);

            JLabel value = new JLabel(wrapHtml(row.value(), availableValueWidth));
            value.setFont(valueFont);
            value.setForeground(valueColor);
            value.setVerticalAlignment(SwingConstants.TOP);
            rowsPanel.add(value, gbcValue);
        }

        java.awt.GridBagConstraints filler = new java.awt.GridBagConstraints();
        filler.gridx = 0;
        filler.gridy = rows.size();
        filler.weighty = 1;
        filler.gridwidth = 2;
        rowsPanel.add(javax.swing.Box.createVerticalGlue(), filler);
        return rowsPanel;
    }

    private String wrapHtml(String text, int widthPx) {
        String payload = safe(text);
        if (payload.isEmpty()) {
            return payload;
        }
        return "<html><div style='width:" + widthPx + "px;white-space:normal;'>" + escapeHtml(payload) + "</div></html>";
    }

    private Point constrainToScreen(Point desired, Dimension popupSize, Component owner) {
        Rectangle screenBounds = resolveScreenBounds(desired, owner);
        int x = Math.max(screenBounds.x, Math.min(desired.x, screenBounds.x + screenBounds.width - popupSize.width));
        int y = Math.max(screenBounds.y, Math.min(desired.y, screenBounds.y + screenBounds.height - popupSize.height));
        return new Point(x, y);
    }

    private Rectangle resolveScreenBounds(Point anchor, Component owner) {
        GraphicsConfiguration gc = owner != null ? owner.getGraphicsConfiguration() : null;
        if (gc == null) {
            GraphicsDevice targetDevice = findDeviceForPoint(anchor);
            if (targetDevice != null) {
                gc = targetDevice.getDefaultConfiguration();
            }
        }
        if (gc == null) {
            gc = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration();
        }
        Rectangle bounds = new Rectangle(gc.getBounds());
        Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(gc);
        bounds.x += insets.left;
        bounds.y += insets.top;
        bounds.width -= insets.left + insets.right;
        bounds.height -= insets.top + insets.bottom;
        return bounds;
    }

    private GraphicsDevice findDeviceForPoint(Point point) {
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        for (GraphicsDevice device : ge.getScreenDevices()) {
            for (GraphicsConfiguration conf : device.getConfigurations()) {
                if (conf.getBounds().contains(point)) {
                    return device;
                }
            }
        }
        return ge.getDefaultScreenDevice();
    }

    private static String safe(String value) {
        return value != null ? value.trim() : "";
    }

    private static <T> T valueOrDefault(T value, T fallback) {
        return value != null ? value : fallback;
    }

    private static String escapeHtml(String text) {
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private class Attachment {
        private final JComponent target;
        private final java.util.function.Function<MouseEvent, InfoCardData> resolver;
        private final Timer timer;
        private MouseEvent lastMouseEvent;
        private final MouseInputAdapter mouseHandler = new MouseInputAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                schedule(e);
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                lastMouseEvent = e;
            }

            @Override
            public void mouseExited(MouseEvent e) {
                cancel();
                hideActivePopup();
            }
        };

        private final HierarchyListener hierarchyListener = new HierarchyListener() {
            @Override
            public void hierarchyChanged(HierarchyEvent e) {
                if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0 && !target.isShowing()) {
                    cancel();
                    hideActivePopup();
                }
                if ((e.getChangeFlags() & HierarchyEvent.DISPLAYABILITY_CHANGED) != 0 && !target.isDisplayable()) {
                    dispose();
                }
                attachWindowListenerIfNeeded();
            }
        };

        private final WindowAdapter windowListener = new WindowAdapter() {
            @Override
            public void windowDeactivated(WindowEvent e) {
                hideActivePopup();
            }

            @Override
            public void windowIconified(WindowEvent e) {
                hideActivePopup();
            }

            @Override
            public void windowClosing(WindowEvent e) {
                hideActivePopup();
            }
        };

        private Window attachedWindow;

        Attachment(JComponent target, java.util.function.Function<MouseEvent, InfoCardData> resolver) {
            this.target = target;
            this.resolver = resolver;
            this.timer = new Timer(debounceMillis, e -> resolveAndShow());
            this.timer.setRepeats(false);
            target.addMouseListener(mouseHandler);
            target.addMouseMotionListener(mouseHandler);
            target.addHierarchyListener(hierarchyListener);
            attachWindowListenerIfNeeded();
        }

        private void schedule(MouseEvent e) {
            lastMouseEvent = e;
            timer.setInitialDelay(debounceMillis);
            timer.setDelay(debounceMillis);
            timer.restart();
        }

        private void cancel() {
            timer.stop();
        }

        private void resolveAndShow() {
            if (!target.isShowing()) {
                return;
            }
            MouseEvent event = lastMouseEvent;
            if (event == null) {
                event = new MouseEvent(target, MouseEvent.MOUSE_MOVED, System.currentTimeMillis(), 0, target.getWidth() / 2,
                        target.getHeight() / 2, 0, false);
            }
            InfoCardData data;
            try {
                data = resolver.apply(event);
            } catch (RuntimeException ex) {
                return;
            }
            if (data == null) {
                hideActivePopup();
                return;
            }
            Point screenPoint = computeScreenPoint(event);
            if (screenPoint == null) {
                hideActivePopup();
                return;
            }
            showFromAttachment(target, screenPoint, data);
        }

        private Point computeScreenPoint(MouseEvent event) {
            try {
                return event.getLocationOnScreen();
            } catch (IllegalComponentStateException ex) {
                try {
                    Point p = new Point(event.getX(), event.getY());
                    SwingUtilities.convertPointToScreen(p, target);
                    return p;
                } catch (IllegalComponentStateException ignored) {
                    Point pointer = MouseInfo.getPointerInfo() != null ? MouseInfo.getPointerInfo().getLocation() : null;
                    return pointer;
                }
            }
        }

        private void attachWindowListenerIfNeeded() {
            Window window = SwingUtilities.getWindowAncestor(target);
            if (window != null && window != attachedWindow) {
                detachWindowListener();
                window.addWindowListener(windowListener);
                attachedWindow = window;
            }
            if (window == null) {
                detachWindowListener();
            }
        }

        private void detachWindowListener() {
            if (attachedWindow != null) {
                attachedWindow.removeWindowListener(windowListener);
                attachedWindow = null;
            }
        }

        void dispose() {
            cancel();
            target.removeMouseListener(mouseHandler);
            target.removeMouseMotionListener(mouseHandler);
            target.removeHierarchyListener(hierarchyListener);
            detachWindowListener();
        }
    }

    private static final class HoverCardPanel extends JPanel {
        private final Color background;
        private final Color border;

        HoverCardPanel(Color background, Color border) {
            super(true);
            this.background = background;
            this.border = border;
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            HoverPopupPainter.paintCard(g2, new Rectangle(0, 0, getWidth(), getHeight()), background, border, CARD_ARC, SHADOW_SIZE);
            g2.dispose();
            super.paintComponent(g);
        }
    }
}
