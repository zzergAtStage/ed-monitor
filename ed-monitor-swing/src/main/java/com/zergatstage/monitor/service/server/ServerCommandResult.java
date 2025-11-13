package com.zergatstage.monitor.service.server;

/**
 * Outcome of a lifecycle command (start/stop/restart).
 *
 * @param success indicates whether the command achieved its goal
 * @param message a short human-readable summary for UI log/notifications
 */
public record ServerCommandResult(boolean success, String message) {
    public static ServerCommandResult success(String message) {
        return new ServerCommandResult(true, message);
    }

    public static ServerCommandResult failure(String message) {
        return new ServerCommandResult(false, message);
    }
}
