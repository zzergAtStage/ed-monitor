package com.zergatstage.monitor.http.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MarketDto {
    private Long marketId;
    private String stationName;
    private String stationType;
    private String systemName;
    private List<MarketItemDto> items = new ArrayList<>();

    /**
     * Player-set flag marking this market as a bulk supply hub (e.g. a Brewer Corporation
     * station) that should be preferred by the route optimizer when scores are otherwise close.
     */
    private boolean supplyHub;

    /**
     * Timestamp of the last update to this market's pricing/stock data, as reported by the
     * server. May be {@code null} if the server response predates this field or the market has
     * never been refreshed; UI code must render a null value as "unknown" rather than fail.
     */
    private Instant lastUpdated;
}

