package com.zergatstage.monitor.service.server;

/**
 * Canonical backend health states exposed to the Swing UI.
 */
public enum ServerHealthState {
    ONLINE,
    UNHEALTHY,
    DOWN,
    ERROR
}
