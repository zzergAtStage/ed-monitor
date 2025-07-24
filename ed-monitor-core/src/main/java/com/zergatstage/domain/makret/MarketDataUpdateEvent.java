package com.zergatstage.domain.makret;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * Event representing an update in market data.
 */
@Getter
public class MarketDataUpdateEvent extends ApplicationEvent {

    private final String jsonData;

    /**
     * Constructs a MarketDataUpdateEvent.
     *
     * @param source   the source of the event
     * @param jsonData the JSON data from the market file
     */
    public MarketDataUpdateEvent(Object source, String jsonData) {
        super(source);
        this.jsonData = jsonData;
    }

}
