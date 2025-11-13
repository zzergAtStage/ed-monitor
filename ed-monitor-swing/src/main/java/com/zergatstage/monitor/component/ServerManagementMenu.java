package com.zergatstage.monitor.component;

import com.zergatstage.monitor.config.UiConstants;
import com.zergatstage.monitor.service.server.ServerCommandResult;
import com.zergatstage.monitor.service.server.ServerHealthState;
import com.zergatstage.monitor.service.server.ServerLifecycleService;

import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * Builds the "Server management" menu and wires it to asynchronous lifecycle commands.
 */
public class ServerManagementMenu {
    private final Component parent;
    private final ServerLifecycleService lifecycleService;

    public ServerManagementMenu(Component parent, ServerLifecycleService lifecycleService) {
        this.parent = Objects.requireNonNull(parent, "parent");
        this.lifecycleService = Objects.requireNonNull(lifecycleService, "lifecycleService");
    }

    public JMenu build() {
        JMenu serverMenu = new JMenu(UiConstants.SERVER_MENU);

        JMenuItem start = new JMenuItem(UiConstants.SERVER_START_ITEM);
        start.addActionListener(e -> handleResult(UiConstants.SERVER_START_ITEM, lifecycleService.startBackend()));

        JMenuItem stop = new JMenuItem(UiConstants.SERVER_STOP_ITEM);
        stop.addActionListener(e -> handleResult(UiConstants.SERVER_STOP_ITEM, lifecycleService.stopBackend()));

        JMenuItem restart = new JMenuItem(UiConstants.SERVER_RESTART_ITEM);
        restart.addActionListener(e -> handleResult(UiConstants.SERVER_RESTART_ITEM, lifecycleService.restartBackend()));

        JMenuItem health = new JMenuItem(UiConstants.SERVER_CHECK_HEALTH_ITEM);
        health.addActionListener(e -> handleHealth(UiConstants.SERVER_CHECK_HEALTH_ITEM, lifecycleService.checkHealth()));

        serverMenu.add(start);
        serverMenu.add(stop);
        serverMenu.add(restart);
        serverMenu.addSeparator();
        serverMenu.add(health);
        return serverMenu;
    }

    private void handleResult(String title, CompletableFuture<ServerCommandResult> future) {
        future.whenComplete((result, throwable) -> {
            if (throwable != null) {
                showMessage(title, "Operation failed: " + throwable.getMessage(), JOptionPane.ERROR_MESSAGE);
                return;
            }
            int type = result.success() ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.WARNING_MESSAGE;
            showMessage(title, result.message(), type);
        });
    }

    private void handleHealth(String title, CompletableFuture<ServerHealthState> future) {
        future.whenComplete((state, throwable) -> {
            if (throwable != null) {
                showMessage(title, "Health check failed: " + throwable.getMessage(), JOptionPane.ERROR_MESSAGE);
                return;
            }
            String message = switch (state) {
                case ONLINE -> "Backend is online";
                case UNHEALTHY -> "Backend responded but reported unhealthy state";
                case DOWN -> "Backend is down or unreachable";
                case ERROR -> "Backend responded with errors";
            };
            int type = switch (state) {
                case ONLINE -> JOptionPane.INFORMATION_MESSAGE;
                case UNHEALTHY -> JOptionPane.WARNING_MESSAGE;
                case DOWN, ERROR -> JOptionPane.ERROR_MESSAGE;
            };
            showMessage(title, message, type);
        });
    }

    private void showMessage(String title, String message, int type) {
        SwingUtilities.invokeLater(() ->
                JOptionPane.showMessageDialog(parent, message, title, type));
    }
}
