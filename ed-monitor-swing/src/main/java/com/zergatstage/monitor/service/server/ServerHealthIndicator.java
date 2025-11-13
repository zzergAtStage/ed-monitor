package com.zergatstage.monitor.service.server;

import java.awt.Color;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * Maps {@link ServerHealthState} values to UI friendly colors/labels.
 */
public class ServerHealthIndicator {
    private static final Color COLOR_ONLINE = new Color(0x2E7D32);
    private static final Color COLOR_UNHEALTHY = new Color(0xF9A825);
    private static final Color COLOR_DOWN = new Color(0x9E9E9E);
    private static final Color COLOR_ERROR = new Color(0xC62828);

    private final Map<ServerHealthState, Indicator> indicatorByState;

    public ServerHealthIndicator() {
        indicatorByState = new EnumMap<>(ServerHealthState.class);
        indicatorByState.put(ServerHealthState.ONLINE, new Indicator(ServerHealthState.ONLINE, COLOR_ONLINE, "Online"));
        indicatorByState.put(ServerHealthState.UNHEALTHY, new Indicator(ServerHealthState.UNHEALTHY, COLOR_UNHEALTHY, "Unhealthy"));
        indicatorByState.put(ServerHealthState.DOWN, new Indicator(ServerHealthState.DOWN, COLOR_DOWN, "Down"));
        indicatorByState.put(ServerHealthState.ERROR, new Indicator(ServerHealthState.ERROR, COLOR_ERROR, "Has errors"));
    }

    public Indicator describe(ServerHealthState state) {
        ServerHealthState resolved = state == null ? ServerHealthState.DOWN : state;
        return indicatorByState.getOrDefault(resolved, indicatorByState.get(ServerHealthState.DOWN));
    }

    public Color colorFor(ServerHealthState state) {
        return describe(state).color();
    }

    public String labelFor(ServerHealthState state) {
        return describe(state).label();
    }

    public record Indicator(ServerHealthState state, Color color, String label) {
        public Indicator {
            Objects.requireNonNull(state, "state");
            Objects.requireNonNull(color, "color");
            Objects.requireNonNull(label, "label");
        }
    }
}
