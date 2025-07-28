package com.zergatstage.monitor.service.managers;

import lombok.Getter;

/**
 * Event representing an update in market data.
 */
@Getter
public class MarketDataUpdateEvent {

    private final String jsonData;

    /**
     * Constructs a MarketDataUpdateEvent.
     *
     * @param source   the source of the event
     * @param jsonData the JSON data from the market file
     */
    public MarketDataUpdateEvent(Object source, String jsonData) {
        this.jsonData = jsonData;
    }
}
