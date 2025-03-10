package com.zergatstage.domain;

import com.zergatstage.services.EDHaulingOptimizer;
import lombok.Getter;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@Getter
public class Market {
    private final String marketId;
    private final String stationName;
    private final String stationType;
    private final String systemName;
    private final Map<String, MarketItem> items;

    public Market(String marketId, String stationName, String stationType, String systemName) {
        this.marketId = marketId;
        this.stationName = stationName;
        this.stationType = stationType;
        this.systemName = systemName;
        this.items = new HashMap<>();
    }

    public void addItem(MarketItem item) {
        items.put(item.getName(), item);
    }

    public Map<String, MarketItem> getItems() {
        return Collections.unmodifiableMap(items);
    }

    public MarketItem getItem(String itemName) {
        return items.get(itemName);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Market that = (Market) o;
        return Objects.equals(marketId, that.marketId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(marketId);
    }

    @Override
    public String toString() {
        return systemName + " - " + stationName;
    }
}
