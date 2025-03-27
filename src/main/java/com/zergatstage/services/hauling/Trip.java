package com.zergatstage.services.hauling;

import com.zergatstage.domain.makret.Market;
import lombok.Getter;

import java.util.*;

/**
 * Represents a hauling trip composed of several stops.
 */
class Trip {
    private final List<TripStop> stops;
    @Getter
    private int totalCargo;

    /**
     * Constructs an empty Trip.
     */
    public Trip() {
        this.stops = new ArrayList<>();
        this.totalCargo = 0;
    }

    /**
     * Adds a stop to the trip with the given market and materials.
     *
     * @param market    the market for the stop
     * @param materials a map of materials to load at this stop
     */
    public void addStop(Market market, Map<String, Integer> materials) {
        stops.add(new TripStop(market, materials));
        totalCargo += materials.values().stream().mapToInt(Integer::intValue).sum();
    }

    /**
     * Returns an unmodifiable list of stops in this trip.
     *
     * @return the list of stops
     */
    public List<TripStop> getStops() {
        return Collections.unmodifiableList(stops);
    }

    /**
     * Checks if additional cargo can be added without exceeding ship capacity.
     *
     * @param quantity     the quantity of cargo to add
     * @param shipCapacity the total ship capacity
     * @return true if cargo can be added; false otherwise
     */
    public boolean canAddCargo(int quantity, int shipCapacity) {
        return totalCargo + quantity <= shipCapacity;
    }

    /**
     * Aggregates and returns the total materials by type across all stops.
     *
     * @return a map of material names to total loaded amounts
     */
    public Map<String, Integer> getTotalMaterialsByType() {
        Map<String, Integer> result = new HashMap<>();
        for (TripStop stop : stops) {
            for (Map.Entry<String, Integer> entry : stop.getMaterialsLoaded().entrySet()) {
                result.put(entry.getKey(), result.getOrDefault(entry.getKey(), 0) + entry.getValue());
            }
        }
        return result;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Trip with ").append(stops.size()).append(" stops:");
        for (int i = 0; i < stops.size(); i++) {
            sb.append("\n Stop ").append(i + 1).append(": ").append(stops.get(i));
        }
        sb.append("\n Total cargo: ").append(totalCargo).append(" tons");
        return sb.toString();
    }
}
