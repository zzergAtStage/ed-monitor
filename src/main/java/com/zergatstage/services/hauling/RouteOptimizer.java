package com.zergatstage.services.hauling;

import com.zergatstage.domain.makret.Market;
import com.zergatstage.domain.makret.MarketItem;

import java.util.*;

// Route optimization service
class RouteOptimizer {
    public static List<Trip> optimizeHauling(Map<String, Material> requiredMaterials,
                                             List<Market> markets,
                                             int shipCapacity) {
        List<Trip> trips = new ArrayList<>();
        Map<String, Integer> remaining = new HashMap<>();

        // Initialize remaining materials
        for (Material material : requiredMaterials.values()) {
            remaining.put(material.getName(), material.getRequiredAmount());
        }

        // Find best markets for each material based on price and availability
        Map<String, List<Market>> bestMarketsForMaterials = findBestMarketsForMaterials(requiredMaterials, markets);

        // Continue until all materials are delivered
        while (!allMaterialsDelivered(remaining)) {
            Trip currentTrip = new Trip();
            boolean tripModified = true;

            // Keep adding to the current trip until we can't add more
            while (tripModified && !allMaterialsDelivered(remaining)) {
                tripModified = false;

                // Find the next best market to visit
                Market bestMarket = null;
                Map<String, Integer> bestLoad = null;
                int bestLoadValue = 0;

                for (Market market : markets) {
                    // Skip if we already visited this market in this trip
                    boolean alreadyVisited = false;
                    for (TripStop stop : currentTrip.getStops()) {
                        if (stop.getMarket().equals(market)) {
                            alreadyVisited = true;
                            break;
                        }
                    }
                    if (alreadyVisited) continue;

                    // Calculate potential load from this market
                    Map<String, Integer> potentialLoad = calculatePotentialLoad(market, remaining,
                            shipCapacity - currentTrip.getTotalCargo());

                    int loadValue = potentialLoad.values().stream().mapToInt(Integer::intValue).sum();

                    // Check if this is the best market so far
                    if (loadValue > bestLoadValue) {
                        bestLoadValue = loadValue;
                        bestMarket = market;
                        bestLoad = potentialLoad;
                    }
                }

                // If we found a market with something to load
                if (bestMarket != null && bestLoadValue > 0) {
                    // Update remaining materials
                    for (Map.Entry<String, Integer> entry : bestLoad.entrySet()) {
                        String materialName = entry.getKey();
                        int loadAmount = entry.getValue();

                        remaining.put(materialName, remaining.get(materialName) - loadAmount);
                    }

                    currentTrip.addStop(bestMarket, bestLoad);
                    tripModified = true;
                }
            }

            // Add the trip if it has any stops
            if (!currentTrip.getStops().isEmpty()) {
                trips.add(currentTrip);
            } else {
                // If we couldn't create a trip, something went wrong
                System.err.println("Error: Cannot find a source for remaining materials!");
                break;
            }
        }

        // Final optimization: Combine trips if possible
        return optimizeTrips(trips, shipCapacity);
    }

    private static Map<String, List<Market>> findBestMarketsForMaterials(Map<String, Material> requiredMaterials,
                                                                         List<Market> markets) {
        Map<String, List<Market>> result = new HashMap<>();

        for (String materialName : requiredMaterials.keySet()) {
            List<Market> marketsWithMaterial = new ArrayList<>();

            for (Market market : markets) {
                MarketItem item = market.getItem(materialName);
                if (item != null && item.getStock() > 0) {
                    marketsWithMaterial.add(market);
                }
            }

            // Sort markets by buy price (lowest first)
            marketsWithMaterial.sort(Comparator.comparingInt(
                    market -> market.getItem(materialName).getBuyPrice()
            ));

            result.put(materialName, marketsWithMaterial);
        }

        return result;
    }

    private static Map<String, Integer> calculatePotentialLoad(Market market,
                                                               Map<String, Integer> remaining,
                                                               int availableCapacity) {
        Map<String, Integer> potentialLoad = new HashMap<>();
        int capacityLeft = availableCapacity;

        // Try to load materials based on what we need
        for (Map.Entry<String, Integer> entry : remaining.entrySet()) {
            String materialName = entry.getKey();
            int neededAmount = entry.getValue();

            if (neededAmount <= 0) continue;

            MarketItem item = market.getItem(materialName);
            if (item != null && item.getStock() > 0) {
                // Calculate how much we can load
                int availableAmount = item.getStock();
                int toLoad = Math.min(neededAmount, availableAmount);
                toLoad = Math.min(toLoad, capacityLeft);

                if (toLoad > 0) {
                    potentialLoad.put(materialName, toLoad);
                    capacityLeft -= toLoad;
                }
            }

            if (capacityLeft <= 0) break;
        }

        return potentialLoad;
    }

    private static boolean allMaterialsDelivered(Map<String, Integer> remaining) {
        return remaining.values().stream().allMatch(v -> v <= 0);
    }

    private static List<Trip> optimizeTrips(List<Trip> trips, int shipCapacity) {
        if (trips.size() <= 1) return trips;

        List<Trip> optimizedTrips = new ArrayList<>();
        int i = 0;

        while (i < trips.size()) {
            Trip currentTrip = trips.get(i);

            // Try to combine with next trip if possible
            if (i + 1 < trips.size()) {
                Trip nextTrip = trips.get(i + 1);

                if (currentTrip.getTotalCargo() + nextTrip.getTotalCargo() <= shipCapacity) {
                    // Create combined trip
                    Trip combinedTrip = new Trip();

                    // Add stops from current trip
                    for (TripStop stop : currentTrip.getStops()) {
                        combinedTrip.addStop(stop.getMarket(), stop.getMaterialsLoaded());
                    }

                    // Add stops from next trip (checking for duplicates)
                    for (TripStop stop : nextTrip.getStops()) {
                        boolean marketExists = false;

                        for (TripStop existingStop : combinedTrip.getStops()) {
                            if (existingStop.getMarket().equals(stop.getMarket())) {
                                marketExists = true;
                                break;
                            }
                        }

                        if (!marketExists) {
                            combinedTrip.addStop(stop.getMarket(), stop.getMaterialsLoaded());
                        }
                    }

                    optimizedTrips.add(combinedTrip);
                    i += 2;  // Skip next trip since we combined it
                } else {
                    // Can't combine, add current trip as is
                    optimizedTrips.add(currentTrip);
                    i++;
                }
            } else {
                // Last trip, add as is
                optimizedTrips.add(currentTrip);
                i++;
            }
        }

        // If we managed to combine any trips, try optimizing again
        if (optimizedTrips.size() < trips.size()) {
            return optimizeTrips(optimizedTrips, shipCapacity);
        }

        return optimizedTrips;
    }
}
