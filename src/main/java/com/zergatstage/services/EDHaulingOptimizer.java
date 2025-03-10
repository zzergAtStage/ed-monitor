package com.zergatstage.services;

import java.util.*;
import java.nio.file.*;

import com.zergatstage.domain.Market;
import com.zergatstage.domain.MarketItem;
import com.zergatstage.domain.MarketRepository;
import lombok.Getter;
import org.json.simple.*;
import org.json.simple.parser.*;

public class EDHaulingOptimizer {

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
                sb.append("\n Stop ").append(i+1).append(": ").append(stops.get(i));
            }
            sb.append("\n Total cargo: ").append(totalCargo).append(" tons");
            return sb.toString();
        }
    }

    // Service for parsing market data
    static class MarketDataParser {
        public static List<Market> parseMarketData(String jsonData) throws ParseException {
            List<Market> markets = new ArrayList<>();

            JSONParser parser = new JSONParser();
            JSONObject jsonObject = (JSONObject) parser.parse(jsonData);

            String marketId = jsonObject.get("MarketID").toString();
            String stationName = (String) jsonObject.get("StationName");
            String stationType = (String) jsonObject.get("StationType");
            String systemName = (String) jsonObject.get("StarSystem");

            Market market = new Market(marketId, stationName, stationType, systemName);

            JSONArray items = (JSONArray) jsonObject.get("Items");
            for (Object itemObj : items) {
                JSONObject item = (JSONObject) itemObj;

                String id = item.get("id").toString();
                String name = (String) item.get("Name_Localised");
                String category = (String) item.get("Category_Localised");

                long buyPrice = (long) item.get("BuyPrice");
                long sellPrice = (long) item.get("SellPrice");
                long stock = (long) item.get("Stock");
                long demand = (long) item.get("Demand");

                MarketItem marketItem = new MarketItem(
                        id, name, category,
                        (int) buyPrice, (int) sellPrice,
                        (int) stock, (int) demand
                );

                market.addItem(marketItem);
            }

            markets.add(market);
            return markets;
        }

        public static List<Market> parseMarketDataFromFile(String filePath) {
            try {
                String content = new String(Files.readAllBytes(Paths.get(filePath)));
                return parseMarketData(content);
            } catch (Exception e) {
                System.err.println("Error parsing market data: " + e.getMessage());
                e.printStackTrace();
                return new ArrayList<>();
            }
        }
    }

    // Route optimization service
    static class RouteOptimizer {
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

    // Main application: integration of market IO with hauling optimization.
    public static void main(String[] args) {
        try {
            // Define the file path for Market.json.
            // (Adjust the path as needed, e.g., relative to your log directory)
            Path marketFile = Paths.get(System.getProperty("user.home"), "Saved Games", "Frontier Developments", "Elite Dangerous", "Market.json");

            // Create a MarketRepository instance (using production defaults).
            MarketRepository repository = new MarketRepository();
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
                        repository.saveOrUpdateMarket(market);
                    }
                    // Retrieve updated market data from the repository.
                    List<Market> markets = repository.getAllMarkets();
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
            MarketDataIOService marketDataService = new MarketDataIOService(marketFile, listener);
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

    // Helper method to create example markets if file parsing fails
    private static Market createExampleMarketA() {
        Market market = new Market("1", "Station A", "Orbital", "System A1");

        market.addItem(new MarketItem("1", "Aluminum", "Metals", 100, 90, 2000, 0));
        market.addItem(new MarketItem("2", "Steel", "Metals", 200, 180, 2000, 0));
        market.addItem(new MarketItem("3", "Biowaste", "Waste", 50, 30, 1000, 0));

        return market;
    }

    private static Market createExampleMarketB() {
        Market market = new Market("2", "Station B", "Outpost", "System B1");

        market.addItem(new MarketItem("4", "Surface Stabilizers", "Chemicals", 500, 450, 1000, 0));
        market.addItem(new MarketItem("3", "Biowaste", "Waste", 55, 35, 1000, 0));

        return market;
    }
}