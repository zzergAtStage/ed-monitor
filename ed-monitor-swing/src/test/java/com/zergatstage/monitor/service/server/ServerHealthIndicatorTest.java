package com.zergatstage.monitor.service.server;

import org.junit.jupiter.api.Test;

import java.awt.Color;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ServerHealthIndicatorTest {

    private final ServerHealthIndicator indicator = new ServerHealthIndicator();

    @Test
    void onlineStateUsesGreen() {
        assertEquals(new Color(0x2E7D32), indicator.colorFor(ServerHealthState.ONLINE));
        assertEquals("Online", indicator.labelFor(ServerHealthState.ONLINE));
    }

    @Test
    void unhealthyStateUsesYellow() {
        assertEquals(new Color(0xF9A825), indicator.colorFor(ServerHealthState.UNHEALTHY));
        assertEquals("Unhealthy", indicator.labelFor(ServerHealthState.UNHEALTHY));
    }

    @Test
    void downStateUsesGray() {
        assertEquals(new Color(0x9E9E9E), indicator.colorFor(ServerHealthState.DOWN));
        assertEquals("Down", indicator.labelFor(ServerHealthState.DOWN));
    }

    @Test
    void errorStateUsesRed() {
        assertEquals(new Color(0xC62828), indicator.colorFor(ServerHealthState.ERROR));
        assertEquals("Has errors", indicator.labelFor(ServerHealthState.ERROR));
    }

    @Test
    void nullDefaultsToDownIndicator() {
        ServerHealthIndicator.Indicator described = indicator.describe(null);
        assertEquals(ServerHealthState.DOWN, described.state());
        assertEquals(new Color(0x9E9E9E), described.color());
    }
}
