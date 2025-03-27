package com.zergatstage.services.hauling;

import com.zergatstage.domain.makret.Market;
import lombok.Getter;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Represents a stop in a hauling trip.
 */
class TripStop {
    @Getter
    private final Market market;
    private final Map<String, Integer> materialsLoaded;

    /**
     * Constructs a TripStop.
     *
     * @param market          the market at this stop
     * @param materialsLoaded a map of materials loaded at this stop
     */
    public TripStop(Market market, Map<String, Integer> materialsLoaded) {
        this.market = market;
        this.materialsLoaded = new HashMap<>(materialsLoaded);
    }

    /**
     * Returns an unmodifiable map of materials loaded.
     *
     * @return the materials loaded
     */
    public Map<String, Integer> getMaterialsLoaded() {
        return Collections.unmodifiableMap(materialsLoaded);
    }

    /**
     * Returns the total cargo loaded at this stop.
     *
     * @return the total loaded cargo
     */
    public int getTotalLoaded() {
        return materialsLoaded.values().stream().mapToInt(Integer::intValue).sum();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(market);
        sb.append("\n Loading: ");
        for (Map.Entry<String, Integer> entry : materialsLoaded.entrySet()) {
            sb.append("\n  ").append(entry.getKey())
                    .append(": ").append(entry.getValue()).append(" tons");
        }
        return sb.toString();
    }
}
