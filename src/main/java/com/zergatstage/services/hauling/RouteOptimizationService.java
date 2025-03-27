package com.zergatstage.services.hauling;

import com.zergatstage.domain.dictionary.CommodityService;
import com.zergatstage.domain.makret.Market;
import com.zergatstage.domain.makret.MarketRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Service responsible for calculating optimized hauling routes.
 *
 * <p>Its single responsibility is to produce a route plan given
 * some required materials and the latest market data.</p>
 */
@Service
@RequiredArgsConstructor
public class RouteOptimizationService {

    private final MarketRepository marketRepository;
    private final CommodityService commodityService;

    /**
     * Optimizes hauling operations based on required materials,
     * total ship capacity, and the latest market data.
     *
     * @param requiredMaterials the map of material name -> required quantity
     * @param shipCapacity      the maximum capacity of the ship
     * @return a list of {@link Trip} objects representing the optimal route plan
     */
    public List<Trip> optimizeRoute(Map<String, Integer> requiredMaterials, int shipCapacity) {
        // 1. Retrieve the latest market data from the repository
        List<Market> markets = marketRepository.findAll();

        // 2. Convert required materials into domain objects (if needed)
        Map<String, Material> materialsMap = convertToMaterialObjects(requiredMaterials);

        // 3. Delegate actual "route calculation" to a dedicated utility
        // or private method that does the route planning
        // For demonstration, we reuse the same static approach or
        // refactor your existing RouteOptimizer.
        List<Trip> trips = RouteOptimizer.optimizeHauling(materialsMap, markets, shipCapacity);

        // 4. Return the trips for the caller (UI, or a REST endpoint) to display or store
        return trips;
    }

    /**
     * Converts a map of required materials into {@link Material} domain objects.
     *
     * @param requiredMaterials the map of material name -> quantity
     * @return a map of material name -> {@link Material} objects
     */
    private Map<String, Material> convertToMaterialObjects(Map<String, Integer> requiredMaterials) {
        Map<String, Material> result = new HashMap<>();
        for (Map.Entry<String, Integer> entry : requiredMaterials.entrySet()) {
            String commodityName = entry.getKey();
            int requiredAmount = entry.getValue();
            result.put(
                    commodityName,
                    new Material(commodityService.getCommodityByName(commodityName), requiredAmount)
            );
        }
        return result;
    }
}
