package com.zergatstage.services.hauling;

import java.util.*;

import com.zergatstage.domain.makret.Market;
import com.zergatstage.domain.makret.MarketDataParser;
import com.zergatstage.domain.makret.MarketRepository;
import com.zergatstage.services.MarketDataIOService;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.json.simple.parser.*;
import org.springframework.stereotype.Component;


@Component
@RequiredArgsConstructor
public class EDHaulingOptimizer {
    private final MarketRepository repository;
    // Domain models
    static class Material {
        private final String name;
        private final int requiredAmount;

        public Material(String name, int requiredAmount) {
            this.name = name;
            this.requiredAmount = requiredAmount;
        }

        public String getName() {
            return name;
        }

        public int getRequiredAmount() {
            return requiredAmount;
        }
    }

    static class TripStop {
        @Getter
        private final Market market;
        private final Map<String, Integer> materialsLoaded;

        public TripStop(Market market, Map<String, Integer> materialsLoaded) {
            this.market = market;
            this.materialsLoaded = new HashMap<>(materialsLoaded);
        }

        public Map<String, Integer> getMaterialsLoaded() {
            return Collections.unmodifiableMap(materialsLoaded);
        }

        public int getTotalLoaded() {
            return materialsLoaded.values().stream().mapToInt(Integer::intValue).sum();
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append(market);
            sb.append("\n Loading: ");
            for (Map.Entry<String, Integer> entry : materialsLoaded.entrySet()) {
                sb.append("\n  ").append(entry.getKey()).append(": ").append(entry.getValue()).append(" tons");
            }
            return sb.toString();
        }
    }

    static class Trip {
        private final List<TripStop> stops;
        @Getter
        private int totalCargo;

        public Trip() {
            this.stops = new ArrayList<>();
            this.totalCargo = 0;
        }

        public void addStop(Market market, Map<String, Integer> materials) {
            stops.add(new TripStop(market, materials));
            totalCargo += materials.values().stream().mapToInt(Integer::intValue).sum();
        }

        public List<TripStop> getStops() {
            return Collections.unmodifiableList(stops);
        }

        public boolean canAddCargo(int quantity, int shipCapacity) {
            return totalCargo + quantity <= shipCapacity;
        }

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

    // Main application: integration of market IO with hauling optimization.
    void optimize() {
        try {

            // Create a listener to receive market data updates.
            MarketDataIOService.MarketDataListener listener = jsonData -> {
                try {
                    // Parse the market data from the updated JSON content.
                    List<Market> parsedMarkets = MarketDataParser.parseMarketData(jsonData);
                    if (parsedMarkets.isEmpty()) {
                        System.out.println("No market data found from file update.");
                        return;
                    }
                    // Save or update each parsed market in the repository.
                    for (Market market : parsedMarkets) {
                        repository.save(market);
                    }
                    // Retrieve updated market data from the repository.
                    List<Market> markets = repository.findAll();
                    // Print available markets
                    System.out.println("Available markets (updated):");
                    for (Market market : markets) {
                        System.out.println("- " + market);
                        System.out.println("  Items available: " + market.getItems().size());
                    }

                    // Define required materials for construction (example data)
                    Map<String, Material> requiredMaterials = new HashMap<>();
                    requiredMaterials.put("Aluminum", new Material("Aluminum", 1117));
                    requiredMaterials.put("Steel", new Material("Steel", 1456));
                    requiredMaterials.put("Biowaste", new Material("Biowaste", 450));
                    requiredMaterials.put("Surface Stabilizers", new Material("Surface Stabilizers", 367));

                    // Ship capacity (example value)
                    int shipCapacity = 720;

                    // Run the hauling optimization based on the updated market data.
                    List<Trip> optimalTrips = RouteOptimizer.optimizeHauling(requiredMaterials, parsedMarkets, shipCapacity);

                    // Output the hauling plan
                    System.out.println("\nOptimal hauling plan with " + optimalTrips.size() + " trips:");
                    for (int i = 0; i < optimalTrips.size(); i++) {
                        System.out.println("\nTrip " + (i + 1) + ":");
                        System.out.println(optimalTrips.get(i));
                    }

                    // Calculate and output total materials delivered
                    Map<String, Integer> totalDelivered = new HashMap<>();
                    for (Trip trip : optimalTrips) {
                        Map<String, Integer> tripMaterials = trip.getTotalMaterialsByType();
                        for (Map.Entry<String, Integer> entry : tripMaterials.entrySet()) {
                            totalDelivered.put(entry.getKey(),
                                    totalDelivered.getOrDefault(entry.getKey(), 0) + entry.getValue());
                        }
                    }
                    System.out.println("\nTotal materials delivered:");
                    for (Map.Entry<String, Integer> entry : totalDelivered.entrySet()) {
                        System.out.println("  " + entry.getKey() + ": " + entry.getValue() + " tons");
                    }
                } catch (ParseException e) {
                    System.err.println("Error parsing market data: " + e.getMessage());
                }
            };

            // Create and start the MarketDataIOService.
            MarketDataIOService marketDataService = new MarketDataIOService(listener);
            marketDataService.start();

            // Keep the application running to listen for file updates.
            // (In a real application, this might be integrated into your UI or service lifecycle.)
            System.out.println("EDHaulingOptimizer is running. Press Enter to exit.");
            System.in.read();

            // Stop the market data service before exiting.
            marketDataService.stop();

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}