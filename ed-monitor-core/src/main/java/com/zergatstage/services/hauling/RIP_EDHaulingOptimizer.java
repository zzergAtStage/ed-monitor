package com.zergatstage.services.hauling;

import com.zergatstage.services.CommodityService;
import com.zergatstage.domain.dictionary.Material;
import com.zergatstage.domain.dictionary.Trip;
import com.zergatstage.domain.makret.Market;
import com.zergatstage.domain.makret.MarketDataParser;
import com.zergatstage.domain.makret.MarketRepository;
import com.zergatstage.domain.makret.MarketDataUpdateEvent;
import java.util.*;

import lombok.RequiredArgsConstructor;
import org.json.simple.parser.ParseException;
import org.springframework.context.event.EventListener;

/**
 * Service responsible for optimizing hauling operations.
 * Listens for market data update events and runs the hauling optimization accordingly.
 */
@RequiredArgsConstructor
public class RIP_EDHaulingOptimizer {

    private final MarketRepository repository;
    private final MarketDataParser marketDataParser;
    private final CommodityService commodityService;
    // The RouteOptimizer is assumed to be a utility class with a static optimizeHauling method.
    // private final MarketDataIOService marketDataIOService; // No longer needed here

    /**
     * Handles market data update events by parsing market data, updating the repository,
     * and then executing the hauling optimization.
     *
     * @param event the market data update event containing new JSON data
     */
    @EventListener
    public void handleMarketDataUpdate(MarketDataUpdateEvent event) {
        try {
            String jsonData = event.getJsonData();

            // Parse the updated market data from the JSON content.
            List<Market> parsedMarkets = marketDataParser.parseMarketData(jsonData);
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
            System.out.println("Available markets (updated):");
            for (Market market : markets) {
                System.out.println("- " + market);
                System.out.println("  Items available: " + market.getItems().size());
            }

            // Define required materials for construction (example data).
            Map<String, Material> requiredMaterials = new HashMap<>();
//            Material aluminium = new Material(commodityService.getCommodityByName("Aluminum"),1117);
//            Material steel = new Material(commodityService.getCommodityByName("Steel"),1456);
//            Material biowaste = new Material(commodityService.getCommodityByName("Biowaste"),200);
//            Material stabilizers = new Material(commodityService.getCommodityByName("Surface Stabilizers"),256);
//            requiredMaterials.put("Aluminum", aluminium);
//            requiredMaterials.put("Steel", steel);
//            requiredMaterials.put("Biowaste", biowaste);
//            requiredMaterials.put("Surface Stabilizers", stabilizers);

            // Ship capacity (example value).
            int shipCapacity = 720;

            // Run the hauling optimization based on the updated market data.
            List<Trip> optimalTrips = RouteOptimizer.optimizeHauling(requiredMaterials, parsedMarkets, shipCapacity);

            // Output the hauling plan.
            System.out.println("\nOptimal hauling plan with " + optimalTrips.size() + " trips:");
            for (int i = 0; i < optimalTrips.size(); i++) {
                System.out.println("\nTrip " + (i + 1) + ":");
                System.out.println(optimalTrips.get(i));
            }

            // Calculate and output total materials delivered.
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
    }

}
