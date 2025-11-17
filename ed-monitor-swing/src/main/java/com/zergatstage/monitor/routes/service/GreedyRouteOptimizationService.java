package com.zergatstage.monitor.routes.service;

import com.zergatstage.monitor.http.dto.CommodityDto;
import com.zergatstage.monitor.http.dto.ConstructionSiteDto;
import com.zergatstage.monitor.http.dto.MarketDto;
import com.zergatstage.monitor.http.dto.MarketItemDto;
import com.zergatstage.monitor.http.dto.MaterialRequirementDto;
import com.zergatstage.monitor.routes.dto.DeliveryRunDto;
import com.zergatstage.monitor.routes.dto.PurchaseDto;
import com.zergatstage.monitor.routes.dto.RouteOptimizationRequest;
import com.zergatstage.monitor.routes.dto.RoutePlanDto;
import com.zergatstage.monitor.routes.dto.RunLegDto;
import com.zergatstage.monitor.routes.spi.RouteOptimizerDataProvider;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Greedy {@link RouteOptimizationService} implementation described in ADR-ROUTE-001.
 * Focuses on a single construction site, prioritizes scarce materials, and greedily fills
 * each run until cargo capacity or candidate stock is exhausted. The heuristic aims to
 * minimize the number of runs while remaining explainable and fast for small data sets.
 *
 * <p>Limitations:</p>
 * <ul>
 *     <li>Leg cost is uniform; distance / time are ignored.</li>
 *     <li>Heuristic is not globally optimal but produces human-like plans.</li>
 *     <li>Relies on offline market data provided via {@link RouteOptimizerDataProvider}.</li>
 * </ul>
 *
 * <p>Expected input sizes: tens of markets, up to ~20 materials per site.</p>
 */
public class GreedyRouteOptimizationService implements RouteOptimizationService {

    private static final double EPSILON = 1.0e-6;
    private static final double SCARCITY_WEIGHT_FACTOR = 0.25;
    private static final double SAME_SYSTEM_MULTIPLIER = 1.2;
    private static final double DIFFERENT_SYSTEM_MULTIPLIER = 0.7;

    private final RouteOptimizerDataProvider dataProvider;

    /**
     * Creates the greedy service with the mandatory data provider dependency.
     *
     * @param dataProvider abstraction that supplies construction site and market data
     */
    public GreedyRouteOptimizationService(RouteOptimizerDataProvider dataProvider) {
        this.dataProvider = Objects.requireNonNull(dataProvider, "dataProvider");
    }

    @Override
    public RoutePlanDto buildRoutePlan(RouteOptimizationRequest request) {
        Objects.requireNonNull(request, "request");
        if (request.getConstructionSiteId() == null) {
            throw new IllegalArgumentException("constructionSiteId must be provided");
        }
        if (request.getCargoCapacityTons() <= 0) {
            RoutePlanDto emptyPlan = new RoutePlanDto();
            emptyPlan.setConstructionSiteId(request.getConstructionSiteId());
            emptyPlan.setCoverageFraction(0);
            emptyPlan.setRuns(Collections.emptyList());
            return emptyPlan;
        }

        ConstructionSiteDto site = loadConstructionSite(request.getConstructionSiteId());
        if (site == null) {
            return emptyPlan(request.getConstructionSiteId());
        }

        Map<String, MaterialDemand> demands = buildMaterialDemands(site);
        double initialDemand = initialDemand(demands.values());
        if (initialDemand <= EPSILON) {
            RoutePlanDto completed = new RoutePlanDto();
            completed.setConstructionSiteId(request.getConstructionSiteId());
            completed.setCoverageFraction(1.0);
            completed.setRuns(Collections.emptyList());
            return completed;
        }

        List<MarketDto> candidateMarkets = loadCandidateMarkets(request.getConstructionSiteId());
        String siteSystemName = resolveConstructionSiteSystem(site.getMarketId());
        enrichSellerCounts(demands, candidateMarkets);
        List<MarketInventory> inventories = buildMarketInventories(candidateMarkets, demands.keySet());

        List<DeliveryRunDto> runs = new ArrayList<>();
        int runIndex = 1;
        while (hasRemainingDemand(demands) && hasUsefulMarkets(inventories, demands)) {
            RunComputationResult runResult = buildSingleRun(runIndex, inventories, demands, request, siteSystemName);
            if (runResult == null || runResult.deliveredTonnage <= EPSILON) {
                break;
            }
            runs.add(runResult.runDto);
            runIndex++;
        }

        RoutePlanDto plan = new RoutePlanDto();
        plan.setConstructionSiteId(request.getConstructionSiteId());
        plan.setRuns(runs);
        double remainingDemand = remainingDemand(demands.values());
        double coverage = initialDemand <= EPSILON ? 1.0 : (initialDemand - remainingDemand) / initialDemand;
        plan.setCoverageFraction(Math.max(0, Math.min(1, coverage)));
        return plan;
    }

    private ConstructionSiteDto loadConstructionSite(long constructionSiteId) {
        try {
            return dataProvider.loadConstructionSite(constructionSiteId);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load construction site " + constructionSiteId, e);
        }
    }

    private List<MarketDto> loadCandidateMarkets(long constructionSiteId) {
        try {
            return dataProvider.loadCandidateMarkets(constructionSiteId);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load candidate markets for site " + constructionSiteId, e);
        }
    }

    private String resolveConstructionSiteSystem(Long marketId) {
        if (marketId == null) {
            return null;
        }
        try {
            MarketDto market = dataProvider.loadMarket(marketId);
            return market != null ? market.getSystemName() : null;
        } catch (IOException ignored) {
            return null;
        }
    }

    private Map<String, MaterialDemand> buildMaterialDemands(ConstructionSiteDto site) {
        Map<String, MaterialDemand> demands = new HashMap<>();
        if (site.getRequirements() == null) {
            return demands;
        }
        for (MaterialRequirementDto requirement : site.getRequirements()) {
            if (requirement == null) {
                continue;
            }
            CommodityDto commodity = requirement.getCommodity();
            String key = buildCommodityKey(commodity);
            if (key == null) {
                continue;
            }
            double remaining = requirement.getRequiredQuantity() - requirement.getDeliveredQuantity();
            if (remaining <= 0) {
                continue;
            }
            MaterialDemand demand = demands.computeIfAbsent(key, ignored ->
                new MaterialDemand(key, displayName(commodity)));
            demand.remaining += remaining;
            demand.initialRequired += remaining;
        }
        return demands;
    }

    private void enrichSellerCounts(Map<String, MaterialDemand> demands, List<MarketDto> markets) {
        for (MarketDto market : markets) {
            if (market == null || market.getItems() == null) {
                continue;
            }
            Set<String> counted = new HashSet<>();
            for (MarketItemDto item : market.getItems()) {
                if (item == null) {
                    continue;
                }
                String key = buildCommodityKey(item.getCommodity());
                MaterialDemand demand = demands.get(key);
                if (demand == null || !counted.add(key)) {
                    continue;
                }
                demand.sellerCount++;
            }
        }
        for (MaterialDemand demand : demands.values()) {
            demand.finalizeScarcityWeight();
        }
    }

    private List<MarketInventory> buildMarketInventories(List<MarketDto> markets, Set<String> relevantKeys) {
        List<MarketInventory> inventories = new ArrayList<>();
        for (MarketDto market : markets) {
            if (market == null || market.getItems() == null) {
                continue;
            }
            MarketInventory inventory = new MarketInventory(market);
            for (MarketItemDto item : market.getItems()) {
                if (item == null || item.getStock() <= 0) {
                    continue;
                }
                String key = buildCommodityKey(item.getCommodity());
                if (key == null || !relevantKeys.contains(key)) {
                    continue;
                }
                inventory.addStock(key, item.getStock());
            }
            if (inventory.hasStock()) {
                inventories.add(inventory);
            }
        }
        return inventories;
    }

    private RunComputationResult buildSingleRun(int runIndex,
                                                List<MarketInventory> inventories,
                                                Map<String, MaterialDemand> demands,
                                                RouteOptimizationRequest request,
                                                String siteSystemName) {
        double capacity = request.getCargoCapacityTons();
        int maxMarkets = Math.max(1, request.getMaxMarketsPerRun());
        Set<MarketInventory> visited = new HashSet<>();
        List<RunLegDto> legs = new ArrayList<>();
        Map<String, Double> materialsSummary = new HashMap<>();
        double delivered = 0;

        MarketInventory primary = selectBestMarket(inventories, demands, capacity, visited, true, siteSystemName);
        if (primary == null) {
            return null;
        }
        visited.add(primary);
        LegPlan primaryLeg = planLeg(primary, demands, capacity);
        if (primaryLeg != null) {
            delivered += primaryLeg.loadedTons;
            legs.add(primaryLeg.legDto);
            mergeSummary(materialsSummary, primaryLeg.legDto.getPurchases());
        }
        double remainingCapacity = capacity - delivered;

        while (legs.size() < maxMarkets && remainingCapacity > EPSILON) {
            MarketInventory secondary = selectBestMarket(inventories, demands, remainingCapacity, visited, false, siteSystemName);
            if (secondary == null) {
                break;
            }
            visited.add(secondary);
            LegPlan legPlan = planLeg(secondary, demands, remainingCapacity);
            if (legPlan == null) {
                break;
            }
            delivered += legPlan.loadedTons;
            remainingCapacity -= legPlan.loadedTons;
            legs.add(legPlan.legDto);
            mergeSummary(materialsSummary, legPlan.legDto.getPurchases());
        }

        if (legs.isEmpty()) {
            return null;
        }

        DeliveryRunDto runDto = new DeliveryRunDto();
        runDto.setRunIndex(runIndex);
        runDto.setLegs(legs);
        runDto.setTotalTonnage(delivered);
        runDto.setMaterialsSummaryTons(materialsSummary);
        return new RunComputationResult(runDto, delivered);
    }

    private MarketInventory selectBestMarket(List<MarketInventory> inventories,
                                             Map<String, MaterialDemand> demands,
                                             double capacityLimit,
                                             Set<MarketInventory> visited,
                                             boolean primary,
                                             String siteSystemName) {
        double bestScore = 0;
        MarketInventory best = null;
        for (MarketInventory inventory : inventories) {
            if (visited.contains(inventory)) {
                continue;
            }
            double potentialLoad = computePotentialLoad(inventory, demands, capacityLimit);
            if (potentialLoad <= EPSILON) {
                continue;
            }
            double scarcityBonus = computeScarcityBonus(inventory, demands);
            double score = potentialLoad * computeSystemMultiplier(inventory, siteSystemName)
                + (primary ? SCARCITY_WEIGHT_FACTOR : SCARCITY_WEIGHT_FACTOR / 2.0) * scarcityBonus;
            if (score > bestScore) {
                bestScore = score;
                best = inventory;
            }
        }
        return best;
    }

    private LegPlan planLeg(MarketInventory inventory,
                            Map<String, MaterialDemand> demands,
                            double capacityLimit) {
        List<MaterialStock> sortedStock = inventory.sortedStockFor(demands);
        List<PurchaseDto> purchases = new ArrayList<>();
        double loaded = 0;

        for (MaterialStock stock : sortedStock) {
            MaterialDemand demand = demands.get(stock.key);
            if (demand == null || demand.remaining <= EPSILON) {
                continue;
            }
            double capacityLeft = capacityLimit - loaded;
            if (capacityLeft <= EPSILON) {
                break;
            }
            double requestAmount = Math.min(Math.min(demand.remaining, stock.stock), capacityLeft);
            if (requestAmount <= EPSILON) {
                continue;
            }
            demand.consume(requestAmount);
            stock.stock -= requestAmount;
            loaded += requestAmount;
            purchases.add(new PurchaseDto(demand.displayName, requestAmount));
        }

        if (purchases.isEmpty()) {
            return null;
        }

        RunLegDto leg = new RunLegDto();
        leg.setMarketId(inventory.market.getMarketId());
        leg.setMarketName(Optional.ofNullable(inventory.market.getStationName())
            .orElseGet(() -> Optional.ofNullable(inventory.market.getSystemName()).orElse("Unknown Market")));
        leg.setPurchases(purchases);
        return new LegPlan(leg, loaded);
    }

    private double computePotentialLoad(MarketInventory inventory,
                                        Map<String, MaterialDemand> demands,
                                        double capacityLimit) {
        double capacityLeft = capacityLimit;
        double load = 0;
        for (MaterialStock stock : inventory.stock.values()) {
            if (capacityLimit > 0 && capacityLeft <= EPSILON) {
                break;
            }
            MaterialDemand demand = demands.get(stock.key);
            if (demand == null || demand.remaining <= EPSILON) {
                continue;
            }
            double take = Math.min(demand.remaining, stock.stock);
            if (capacityLimit > 0) {
                take = Math.min(take, capacityLeft);
                capacityLeft -= take;
            }
            load += take;
        }
        return load;
    }

    private double computeScarcityBonus(MarketInventory inventory, Map<String, MaterialDemand> demands) {
        double bonus = 0;
        for (MaterialStock stock : inventory.stock.values()) {
            MaterialDemand demand = demands.get(stock.key);
            if (demand == null || demand.remaining <= EPSILON) {
                continue;
            }
            bonus += demand.scarcityWeight;
        }
        return bonus;
    }

    private double computeSystemMultiplier(MarketInventory inventory, String siteSystemName) {
        if (siteSystemName == null) {
            return 1.0;
        }
        String marketSystem = inventory.market.getSystemName();
        if (marketSystem == null) {
            return 1.0;
        }
        if (marketSystem.equalsIgnoreCase(siteSystemName)) {
            return SAME_SYSTEM_MULTIPLIER;
        }
        return DIFFERENT_SYSTEM_MULTIPLIER;
    }

    private void mergeSummary(Map<String, Double> summary, List<PurchaseDto> purchases) {
        for (PurchaseDto purchase : purchases) {
            summary.merge(purchase.getMaterialName(), purchase.getAmountTons(), Double::sum);
        }
    }

    private boolean hasRemainingDemand(Map<String, MaterialDemand> demands) {
        return demands.values().stream().anyMatch(MaterialDemand::hasRemaining);
    }

    private boolean hasUsefulMarkets(List<MarketInventory> inventories, Map<String, MaterialDemand> demands) {
        for (MarketInventory inventory : inventories) {
            if (inventory.hasUsefulStock(demands)) {
                return true;
            }
        }
        return false;
    }

    private double initialDemand(Collection<MaterialDemand> demands) {
        return demands.stream().mapToDouble(d -> d.initialRequired).sum();
    }

    private double remainingDemand(Collection<MaterialDemand> demands) {
        return demands.stream().mapToDouble(d -> d.remaining).sum();
    }

    private RoutePlanDto emptyPlan(Long constructionSiteId) {
        RoutePlanDto plan = new RoutePlanDto();
        plan.setConstructionSiteId(constructionSiteId);
        plan.setCoverageFraction(0);
        plan.setRuns(Collections.emptyList());
        return plan;
    }

    private static String buildCommodityKey(CommodityDto commodity) {
        if (commodity == null) {
            return null;
        }
        if (commodity.getId() != null) {
            return "id:" + commodity.getId();
        }
        if (commodity.getName() != null) {
            return "name:" + commodity.getName().toLowerCase(Locale.ROOT);
        }
        return null;
    }

    private static String displayName(CommodityDto commodity) {
        if (commodity == null) {
            return "Unknown Commodity";
        }
        if (commodity.getNameLocalised() != null && !commodity.getNameLocalised().isBlank()) {
            return commodity.getNameLocalised();
        }
        if (commodity.getName() != null && !commodity.getName().isBlank()) {
            return commodity.getName();
        }
        if (commodity.getId() != null) {
            return "Commodity-" + commodity.getId();
        }
        return "Commodity";
    }

    private static class MaterialDemand {
        private final String displayName;
        private double remaining;
        private double initialRequired;
        private int sellerCount;
        private double scarcityWeight = 1.0;

        MaterialDemand(String key, String displayName) {
            this.displayName = displayName;
        }

        void finalizeScarcityWeight() {
            if (sellerCount > 0) {
                this.scarcityWeight = 1.0 / sellerCount;
            }
        }

        void consume(double amount) {
            this.remaining = Math.max(0, this.remaining - amount);
        }

        boolean hasRemaining() {
            return remaining > EPSILON;
        }
    }

    private static class MarketInventory {
        private final MarketDto market;
        private final Map<String, MaterialStock> stock = new HashMap<>();

        MarketInventory(MarketDto market) {
            this.market = market;
        }

        void addStock(String key, double quantity) {
            stock.put(key, new MaterialStock(key, quantity));
        }

        boolean hasStock() {
            return stock.values().stream().anyMatch(entry -> entry.stock > EPSILON);
        }

        boolean hasUsefulStock(Map<String, MaterialDemand> demands) {
            return stock.values().stream()
                .anyMatch(entry -> entry.stock > EPSILON && Optional.ofNullable(demands.get(entry.key))
                    .map(MaterialDemand::hasRemaining)
                    .orElse(false));
        }

        List<MaterialStock> sortedStockFor(Map<String, MaterialDemand> demands) {
            List<MaterialStock> entries = new ArrayList<>(stock.values());
            entries.sort(Comparator
                .comparingDouble((MaterialStock s) -> scarcityWeight(demands, s.key)).reversed()
                .thenComparingDouble(s -> remaining(demands, s.key)).reversed());
            return entries;
        }

        private double scarcityWeight(Map<String, MaterialDemand> demands, String key) {
            MaterialDemand demand = demands.get(key);
            return demand == null ? 0 : demand.scarcityWeight;
        }

        private double remaining(Map<String, MaterialDemand> demands, String key) {
            MaterialDemand demand = demands.get(key);
            return demand == null ? 0 : demand.remaining;
        }
    }

    private static class MaterialStock {
        private final String key;
        private double stock;

        MaterialStock(String key, double stock) {
            this.key = key;
            this.stock = stock;
        }
    }

    private record LegPlan(RunLegDto legDto, double loadedTons) {}

    private record RunComputationResult(DeliveryRunDto runDto, double deliveredTonnage) {}
}
