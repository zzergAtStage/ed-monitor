package com.zergatstage.services;

import java.util.*;

public class EDHaulingOptimizer {

    // Classes to represent our domain model
    static class Material {
        String name;
        int requiredAmount;
        List<SourceLocation> availableAt;

        Material(String name, int requiredAmount) {
            this.name = name;
            this.requiredAmount = requiredAmount;
            this.availableAt = new ArrayList<>();
        }

        void addSource(SourceLocation source) {
            availableAt.add(source);
        }
    }

    static class SourceLocation {
        String systemName;
        String stationName;
        Map<String, Integer> availableMaterials; // Material name -> available quantity

        SourceLocation(String systemName, String stationName) {
            this.systemName = systemName;
            this.stationName = stationName;
            this.availableMaterials = new HashMap<>();
        }

        void addMaterial(String materialName, int quantity) {
            availableMaterials.put(materialName, quantity);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            SourceLocation that = (SourceLocation) o;
            return Objects.equals(systemName, that.systemName) &&
                    Objects.equals(stationName, that.stationName);
        }

        @Override
        public int hashCode() {
            return Objects.hash(systemName, stationName);
        }

        @Override
        public String toString() {
            return systemName + " - " + stationName;
        }
    }

    static class Trip {
        List<TripStop> stops;
        Map<String, Integer> cargo; // Material name -> quantity
        int totalCargo;

        Trip() {
            this.stops = new ArrayList<>();
            this.cargo = new HashMap<>();
            this.totalCargo = 0;
        }

        void addStop(SourceLocation source, Map<String, Integer> materials) {
            stops.add(new TripStop(source, new HashMap<>(materials)));
            for (Map.Entry<String, Integer> entry : materials.entrySet()) {
                cargo.put(entry.getKey(), cargo.getOrDefault(entry.getKey(), 0) + entry.getValue());
                totalCargo += entry.getValue();
            }
        }

        boolean canAddCargo(int quantity, int shipCapacity) {
            return totalCargo + quantity <= shipCapacity;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("Trip with ").append(stops.size()).append(" stops:");
            for (int i = 0; i < stops.size(); i++) {
                TripStop stop = stops.get(i);
                sb.append("\n Stop ").append(i+1).append(": ").append(stop.source);
                sb.append("\n  Loading: ");
                for (Map.Entry<String, Integer> entry : stop.materialsLoaded.entrySet()) {
                    sb.append("\n   ").append(entry.getKey()).append(": ").append(entry.getValue()).append(" tons");
                }
            }
            sb.append("\n Total cargo: ").append(totalCargo).append(" tons");
            return sb.toString();
        }
    }

    static class TripStop {
        SourceLocation source;
        Map<String, Integer> materialsLoaded;

        TripStop(SourceLocation source, Map<String, Integer> materialsLoaded) {
            this.source = source;
            this.materialsLoaded = materialsLoaded;
        }
    }

    // Main optimization algorithm
    public static List<Trip> optimizeHauling(Map<String, Material> requiredMaterials,
                                             List<SourceLocation> sources,
                                             int shipCapacity) {
        List<Trip> trips = new ArrayList<>();
        Map<String, Integer> remaining = new HashMap<>();

        // Initialize remaining materials
        for (Material material : requiredMaterials.values()) {
            remaining.put(material.name, material.requiredAmount);
        }

        // Continue until all materials are delivered
        while (!allMaterialsDelivered(remaining)) {
            Trip currentTrip = new Trip();
            boolean tripModified = true;

            // Keep adding to the current trip until we can't add more
            while (tripModified && !allMaterialsDelivered(remaining)) {
                tripModified = false;

                // Try each source
                for (SourceLocation source : sources) {
                    // Skip if we already visited this source in this trip
                    boolean alreadyVisited = false;
                    for (TripStop stop : currentTrip.stops) {
                        if (stop.source.equals(source)) {
                            alreadyVisited = true;
                            break;
                        }
                    }
                    if (alreadyVisited) continue;

                    // Check what we can load from this source
                    Map<String, Integer> potentialLoad = new HashMap<>();
                    int potentialCargoAmount = 0;

                    for (String materialName : source.availableMaterials.keySet()) {
                        if (remaining.containsKey(materialName) && remaining.get(materialName) > 0) {
                            int neededAmount = remaining.get(materialName);
                            int availableAmount = source.availableMaterials.get(materialName);
                            int toLoad = Math.min(neededAmount, availableAmount);

                            // Check if we can add to current trip respecting ship capacity
                            if (currentTrip.canAddCargo(toLoad, shipCapacity - potentialCargoAmount)) {
                                potentialLoad.put(materialName, toLoad);
                                potentialCargoAmount += toLoad;
                            } else {
                                // Try to fit as much as possible
                                int remainingCapacity = shipCapacity - currentTrip.totalCargo - potentialCargoAmount;
                                if (remainingCapacity > 0) {
                                    potentialLoad.put(materialName, remainingCapacity);
                                    potentialCargoAmount += remainingCapacity;
                                }
                            }
                        }
                    }

                    // If we can load something, add this stop
                    if (!potentialLoad.isEmpty()) {
                        // Update the source's available materials
                        for (Map.Entry<String, Integer> entry : potentialLoad.entrySet()) {
                            String materialName = entry.getKey();
                            int loadAmount = entry.getValue();

                            source.availableMaterials.put(materialName,
                                    source.availableMaterials.get(materialName) - loadAmount);
                            remaining.put(materialName, remaining.get(materialName) - loadAmount);
                        }

                        currentTrip.addStop(source, potentialLoad);
                        tripModified = true;

                        // If we're full, break
                        if (currentTrip.totalCargo >= shipCapacity) {
                            break;
                        }
                    }
                }
            }

            // Add the trip if it has any stops
            if (!currentTrip.stops.isEmpty()) {
                trips.add(currentTrip);
            } else {
                // If we couldn't create a trip, something went wrong
                System.out.println("Error: Cannot find a source for remaining materials!");
                break;
            }
        }

        // Final optimization: Try to combine the last two trips if they're not full
        if (trips.size() >= 2) {
            Trip lastTrip = trips.get(trips.size() - 1);
            Trip secondLastTrip = trips.get(trips.size() - 2);

            if (lastTrip.totalCargo + secondLastTrip.totalCargo <= shipCapacity) {
                // We can combine these trips
                Trip combinedTrip = new Trip();

                // Add stops from second last trip
                for (TripStop stop : secondLastTrip.stops) {
                    combinedTrip.addStop(stop.source, stop.materialsLoaded);
                }

                // Add stops from last trip
                for (TripStop stop : lastTrip.stops) {
                    // Check if this source is already in the combined trip
                    boolean sourceExists = false;
                    for (TripStop existingStop : combinedTrip.stops) {
                        if (existingStop.source.equals(stop.source)) {
                            // Merge the materials
                            for (Map.Entry<String, Integer> entry : stop.materialsLoaded.entrySet()) {
                                String material = entry.getKey();
                                int amount = entry.getValue();
                                existingStop.materialsLoaded.put(material,
                                        existingStop.materialsLoaded.getOrDefault(material, 0) + amount);
                            }
                            sourceExists = true;
                            break;
                        }
                    }

                    if (!sourceExists) {
                        combinedTrip.addStop(stop.source, stop.materialsLoaded);
                    }
                }

                // Replace the last two trips with the combined one
                trips.remove(trips.size() - 1);
                trips.remove(trips.size() - 1);
                trips.add(combinedTrip);
            }
        }

        return trips;
    }

    private static boolean allMaterialsDelivered(Map<String, Integer> remaining) {
        for (int value : remaining.values()) {
            if (value > 0) {
                return false;
            }
        }
        return true;
    }

    // Example usage
    public static void main(String[] args) {
// Define required materials
        Map<String, Material> requiredMaterials = new HashMap<>();
        requiredMaterials.put("Aluminum", new Material("Aluminum", 409));
        requiredMaterials.put("Steel", new Material("Steel", 1462));
        requiredMaterials.put("Liquid oxygen", new Material("Liquid oxygen", 436));
        requiredMaterials.put("Surface Stabilizers", new Material("Surface Stabilizers", 367));

        // Define sources
        SourceLocation stationA = new SourceLocation("System A1", "Station A");
        stationA.addMaterial("Aluminum", 2000);
        stationA.addMaterial("Steel", 2000);
        stationA.addMaterial("Liquid oxygen", 1000);

        SourceLocation stationB = new SourceLocation("System B1", "Station B");
        stationB.addMaterial("Surface Stabilizers", 1000);
        stationB.addMaterial("Biowaste", 1000);

        List<SourceLocation> sources = Arrays.asList(stationA, stationB);

        // Ship capacity
        int shipCapacity = 720;

        // Run optimization
        List<Trip> optimalTrips = optimizeHauling(requiredMaterials, sources, shipCapacity);

        // Output results
        System.out.println("Optimal hauling plan with " + optimalTrips.size() + " trips:");
        for (int i = 0; i < optimalTrips.size(); i++) {
            System.out.println("\nTrip " + (i+1) + ":");
            System.out.println(optimalTrips.get(i));
        }

        // Calculate total materials delivered
        Map<String, Integer> totalDelivered = new HashMap<>();
        for (Trip trip : optimalTrips) {
            for (Map.Entry<String, Integer> entry : trip.cargo.entrySet()) {
                totalDelivered.put(entry.getKey(),
                        totalDelivered.getOrDefault(entry.getKey(), 0) + entry.getValue());
            }
        }

        System.out.println("\nTotal materials delivered:");
        for (Map.Entry<String, Integer> entry : totalDelivered.entrySet()) {
            System.out.println("  " + entry.getKey() + ": " + entry.getValue() + " tons");
        }
    }
}

