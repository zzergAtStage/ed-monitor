package com.zergatstage.monitor.util;

import java.awt.Component;
import java.awt.Frame;
import java.awt.KeyboardFocusManager;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.util.concurrent.atomic.AtomicLong;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.KeyStroke;

/**
 * Centralizes reusable Swing key bindings for the app.
 */
public final class KeyBindingUtil {

    private static final int DOUBLE_ESC_WINDOW_MS = 600;

    private KeyBindingUtil() {}

    /**
     * Minimizes the given frame when ESC is pressed twice in quick succession.
     */
    public static void installDoubleEscapeToMinimize(JFrame frame) {
        AtomicLong lastEsc = new AtomicLong(0);
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(event -> {
            if (event.getID() != KeyEvent.KEY_PRESSED || event.getKeyCode() != KeyEvent.VK_ESCAPE) {
                return false;
            }
            Window active = KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();
            if (active == null) {
                return false;
            }
            // Only react when the main frame (or one of its owned components) is active
            if (!isFrameAncestor(active, frame)) {
                return false;
            }
            long now = System.currentTimeMillis();
            long last = lastEsc.get();
            if (now - last <= DOUBLE_ESC_WINDOW_MS) {
                frame.setState(Frame.ICONIFIED);
                lastEsc.set(0);
            } else {
                lastEsc.set(now);
            }
            return false;
        });
    }

    private static boolean isFrameAncestor(Window window, JFrame frame) {
        if (window == frame) {
            return true;
        }
        Window owner = window.getOwner();
        while (owner != null) {
            if (owner == frame) {
                return true;
            }
            owner = owner.getOwner();
        }
        return false;
    }

    /**
     * Binds ESC to dispose the given dialog.
     */
    public static void installEscapeToClose(JDialog dialog) {
        KeyStroke esc = KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0);
        dialog.getRootPane().registerKeyboardAction(
                e -> dialog.dispose(),
                esc,
                JComponent.WHEN_IN_FOCUSED_WINDOW);
    }
}
