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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Improved Greedy {@link RouteOptimizationService} implementation with proper inter-system jump tracking.
 * 
 * <p>Key improvements over original:</p>
 * <ul>
 *     <li>Tracks system transitions between consecutive legs in a run</li>
 *     <li>Accumulates jump penalties throughout the route</li>
 *     <li>Considers both intra-system (no jump) and inter-system (jump required) transitions</li>
 *     <li>Applies progressive penalties for each additional system jump</li>
 * </ul>
 *
 * <p>Limitations:</p>
 * <ul>
 *     <li>Still uses simplified distance model (presence/absence of jump, not actual distance)</li>
 *     <li>Heuristic is not globally optimal but produces human-like plans</li>
 *     <li>Relies on offline market data provided via {@link RouteOptimizerDataProvider}</li>
 * </ul>
 */
public class GreedyRouteOptimizationService implements RouteOptimizationService {

    private static final double EPSILON = 1.0e-6;
    private static final double SCARCITY_WEIGHT_FACTOR = 0.25;

    private static final Logger log = LoggerFactory.getLogger(GreedyRouteOptimizationService.class);
    
    // System preference multipliers
    private static final double SAME_SYSTEM_BONUS = 1.3;
    private static final double JUMP_BASE_PENALTY = 0.75;
    private static final double ADDITIONAL_JUMP_PENALTY = 0.85; // Multiplier for each additional jump
    
    private final RouteOptimizerDataProvider dataProvider;

    /**
     * Creates the improved greedy service with the mandatory data provider dependency.
     *
     * @param dataProvider abstraction that supplies construction site and market data
     */
    public GreedyRouteOptimizationService(RouteOptimizerDataProvider dataProvider) {
        this.dataProvider = Objects.requireNonNull(dataProvider, "dataProvider");
    }

    @Override
    public RoutePlanDto buildRoutePlan(RouteOptimizationRequest request) {
        Objects.requireNonNull(request, "request");
        String requestId = UUID.randomUUID().toString();
        logRouteRequestStart(requestId, request);
        if (request.getConstructionSiteId() == null) {
            logRouteWarning(requestId, "MISSING_SITE_ID",
                "constructionSiteId must be provided for optimization");
            throw new IllegalArgumentException("constructionSiteId must be provided");
        }
        if (request.getCargoCapacityTons() <= 0) {
            logRouteWarning(requestId, "NO_CAPACITY",
                "cargo capacity is non-positive, returning empty plan");
            return emptyPlan(request.getConstructionSiteId(), 0);
        }

        ConstructionSiteDto site = loadConstructionSite(request.getConstructionSiteId());
        if (site == null) {
            logRouteWarning(requestId, "SITE_NOT_FOUND",
                "construction site %s not found".formatted(request.getConstructionSiteId()));
            return emptyPlan(request.getConstructionSiteId(), 0);
        }
        logConstructionSite(requestId, site);

        Map<String, MaterialDemand> demands = buildMaterialDemands(site);
        logDemandSnapshot(requestId, "INITIAL", demands);
        double initialDemand = initialDemand(demands.values());
        if (initialDemand <= EPSILON) {
            logRouteWarning(requestId, "NO_DEMAND",
                "construction site %s has no outstanding demand".formatted(request.getConstructionSiteId()));
            return emptyPlan(request.getConstructionSiteId(), 1.0);
        }

        List<MarketDto> candidateMarkets = loadCandidateMarkets(request.getConstructionSiteId());
        String siteSystemName = resolveConstructionSiteSystem(site, candidateMarkets);
        enrichSellerCounts(demands, candidateMarkets);
        List<MarketInventory> inventories = buildMarketInventories(candidateMarkets, demands.keySet());
        logCandidateMarkets(requestId, candidateMarkets, inventories);

        List<DeliveryRunDto> runs = new ArrayList<>();
        int runIndex = 1;
        while (hasRemainingDemand(demands) && hasUsefulMarkets(inventories, demands)) {
            logRunStart(requestId, runIndex, demands, request);
            RunComputationResult runResult = buildSingleRun(requestId, runIndex, inventories, demands, request, siteSystemName);
            if (runResult == null || runResult.deliveredTonnage <= EPSILON) {
                logRouteWarning(requestId, "RUN_ABORTED",
                    "run %d delivered no tonnage; stopping optimization".formatted(runIndex));
                break;
            }
            runs.add(runResult.runDto);
            logRunCompletion(requestId, runResult);
            logDemandSnapshot(requestId, "POST_RUN_" + runIndex, demands);
            runIndex++;
        }

        RoutePlanDto plan = new RoutePlanDto();
        plan.setConstructionSiteId(request.getConstructionSiteId());
        plan.setRuns(runs);
        double remainingDemand = remainingDemand(demands.values());
        double coverage = initialDemand <= EPSILON ? 1.0 : (initialDemand - remainingDemand) / initialDemand;
        plan.setCoverageFraction(Math.max(0, Math.min(1, coverage)));
        logRouteSummary(requestId, runs, coverage, demands, inventories, initialDemand);
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

    private String resolveConstructionSiteSystem(ConstructionSiteDto site, List<MarketDto> candidateMarkets) {
        Long marketId = site != null ? site.getMarketId() : null;
        try {
            MarketDto market = dataProvider.loadMarket(marketId);
            if (market != null && market.getSystemName() != null && !market.getSystemName().isBlank()) {
                return market.getSystemName();
            }
        } catch (IOException ignored) {
            // fall through to candidate-derived inference below
        }
        return inferSystemFromCandidates(candidateMarkets);
    }

    private String inferSystemFromCandidates(List<MarketDto> candidateMarkets) {
        if (candidateMarkets == null || candidateMarkets.isEmpty()) {
            return null;
        }
        Map<String, SystemStats> stats = new HashMap<>();
        for (MarketDto market : candidateMarkets) {
            if (market == null) {
                continue;
            }
            String system = market.getSystemName();
            if (system == null || system.isBlank()) {
                continue;
            }
            String normalized = system.toLowerCase(Locale.ROOT);
            stats.computeIfAbsent(normalized, key -> new SystemStats(system))
                .accumulate(market);
        }
        return stats.values().stream()
            .max(Comparator
                .comparingInt(SystemStats::getCount)
                .thenComparingDouble(SystemStats::getTotalStock)
                .thenComparing(SystemStats::getDisplayName, String.CASE_INSENSITIVE_ORDER))
            .map(SystemStats::getDisplayName)
            .orElse(null);
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

    /**
     * Builds a single delivery run with proper inter-system jump tracking.
     * 
     * @param runIndex the sequential run number
     * @param inventories available market inventories
     * @param demands remaining material demands
     * @param request optimization parameters
     * @param siteSystemName the construction site's system name
     * @return computation result with the run and delivered tonnage, or null if no valid run
     */
    private RunComputationResult buildSingleRun(String requestId,
                                                int runIndex,
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
        
        // Track the current system location and jump count
        RouteContext context = new RouteContext(siteSystemName);

        // Select and plan primary market (first leg)
        SelectionTrace primaryTrace = new SelectionTrace(requestId, runIndex, 1, context.getCurrentSystem());
        MarketInventory primary = selectBestMarket(inventories, demands, capacity, visited, context, primaryTrace);
        if (primary == null) {
            return null;
        }
        visited.add(primary);
        LegPlan primaryLeg = planLeg(primary, demands, capacity);
        if (primaryLeg != null) {
            delivered += primaryLeg.loadedTons;
            legs.add(primaryLeg.legDto);
            mergeSummary(materialsSummary, primaryLeg.legDto.getPurchases());
            logPlannedLeg(primaryTrace, primary, primaryLeg, context.moveToMarket(primary));
        }
        double remainingCapacity = capacity - delivered;

        // Add secondary markets (additional legs)
        while (legs.size() < maxMarkets && remainingCapacity > EPSILON) {
            SelectionTrace trace = new SelectionTrace(requestId, runIndex, legs.size() + 1, context.getCurrentSystem());
            MarketInventory secondary = selectBestMarket(inventories, demands, remainingCapacity, visited, context, trace);
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
            logPlannedLeg(trace, secondary, legPlan, context.moveToMarket(secondary));
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

    /**
     * Selects the best market considering:
     * 1. Potential cargo load
     * 2. Scarcity of materials
     * 3. System jump penalties (accumulated throughout the route)
     * 
     * @param inventories available markets
     * @param demands remaining demands
     * @param capacityLimit remaining cargo capacity
     * @param visited already visited markets in this run
     * @param context current route context with system location and jump count
     * @return best market to visit next, or null if none suitable
     */
    private MarketInventory selectBestMarket(List<MarketInventory> inventories,
                                             Map<String, MaterialDemand> demands,
                                             double capacityLimit,
                                             Set<MarketInventory> visited,
                                             RouteContext context,
                                             SelectionTrace trace) {
        double bestScore = 0;
        MarketInventory best = null;
        int candidateOrder = 0;
        
        for (MarketInventory inventory : inventories) {
            candidateOrder++;
            if (visited.contains(inventory)) {
                logCandidateEvaluation(trace, inventory, candidateOrder, 0, 0, 0, 0, false, "ALREADY_VISITED", capacityLimit);
                continue;
            }
            
            double potentialLoad = computePotentialLoad(inventory, demands, capacityLimit);
            if (potentialLoad <= EPSILON) {
                logCandidateEvaluation(trace, inventory, candidateOrder, potentialLoad, 0, 0, 0, false, "NO_MATCHING_STOCK", capacityLimit);
                continue;
            }
            
            double scarcityBonus = computeScarcityBonus(inventory, demands);
            double systemMultiplier = computeSystemMultiplier(inventory, context);
            
            // Score combines load potential, scarcity, and system jump considerations
            double score = potentialLoad * systemMultiplier
                + SCARCITY_WEIGHT_FACTOR * scarcityBonus;
            logCandidateEvaluation(trace, inventory, candidateOrder, potentialLoad, scarcityBonus,
                systemMultiplier, score, true, "OK", capacityLimit);
            
            if (score > bestScore + EPSILON) {
                bestScore = score;
                best = inventory;
            } else if (best != null && Math.abs(score - bestScore) <= EPSILON
                && isBetterTieCandidate(inventory, best, context)) {
                best = inventory;
            }
        }
        logSelectionDecision(trace, best, bestScore, candidateOrder);
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
        leg.setMarketName(resolveMarketDisplayName(inventory.market));
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

    /**
     * Computes the system multiplier based on whether moving to this market requires a jump.
     * 
     * Key improvements:
     * - Checks if the market is in the CURRENT system (not just construction site)
     * - Applies progressive penalties for accumulated jumps
     * - Strongly prefers staying in the current system
     * 
     * @param inventory the candidate market
     * @param context current route context with location and jump history
     * @return multiplier to apply to the market's score
     */
    private double computeSystemMultiplier(MarketInventory inventory, RouteContext context) {
        String marketSystem = inventory.market.getSystemName();
        if (marketSystem == null) {
            return 1.0;
        }

        if (context.currentSystem == null) {
            if (context.preferredSystem != null
                && marketSystem.equalsIgnoreCase(context.preferredSystem)) {
                return SAME_SYSTEM_BONUS;
            }
            return 1.0;
        }

        // This market requires a jump to a different system
        // Apply base penalty, with additional penalty based on accumulated jumps
        double jumpPenalty = JUMP_BASE_PENALTY * Math.pow(ADDITIONAL_JUMP_PENALTY, context.jumpCount);
        return jumpPenalty;
    }

    private boolean isBetterTieCandidate(MarketInventory challenger,
                                         MarketInventory incumbent,
                                         RouteContext context) {
        boolean challengerPreferred = isPreferredSystem(challenger, context);
        boolean incumbentPreferred = isPreferredSystem(incumbent, context);
        if (challengerPreferred != incumbentPreferred) {
            return challengerPreferred;
        }

        boolean challengerHasSystem = hasSystemName(challenger);
        boolean incumbentHasSystem = hasSystemName(incumbent);
        if (challengerHasSystem != incumbentHasSystem) {
            return challengerHasSystem;
        }

        double challengerStock = challenger.initialTotalStock();
        double incumbentStock = incumbent.initialTotalStock();
        if (challengerStock > incumbentStock + EPSILON) {
            return true;
        }
        if (incumbentStock > challengerStock + EPSILON) {
            return false;
        }

        String challengerName = resolveMarketDisplayName(challenger.market);
        String incumbentName = resolveMarketDisplayName(incumbent.market);
        return challengerName.compareToIgnoreCase(incumbentName) < 0;
    }

    private boolean isPreferredSystem(MarketInventory inventory, RouteContext context) {
        if (context == null || context.preferredSystem == null) {
            return false;
        }
        String marketSystem = inventory.market.getSystemName();
        return marketSystem != null
            && marketSystem.equalsIgnoreCase(context.preferredSystem);
    }

    private boolean hasSystemName(MarketInventory inventory) {
        String system = inventory.market.getSystemName();
        return system != null && !system.isBlank();
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

    private RoutePlanDto emptyPlan(Long constructionSiteId, double coverage) {
        RoutePlanDto plan = new RoutePlanDto();
        plan.setConstructionSiteId(constructionSiteId);
        plan.setCoverageFraction(coverage);
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

    private static String resolveMarketDisplayName(MarketDto market) {
        if (market == null) {
            return "Unknown Market";
        }
        return Optional.ofNullable(market.getStationName())
            .orElseGet(() -> Optional.ofNullable(market.getSystemName()).orElse("Unknown Market"));
    }

    private static String formatDouble(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return String.valueOf(value);
        }
        return String.format(Locale.ROOT, "%.3f", value);
    }

    private static double sumPurchases(List<PurchaseDto> purchases) {
        if (purchases == null) {
            return 0;
        }
        return purchases.stream()
            .filter(Objects::nonNull)
            .mapToDouble(PurchaseDto::getAmountTons)
            .sum();
    }

    private void logRouteRequestStart(String requestId, RouteOptimizationRequest request) {
        if (!log.isDebugEnabled()) {
            return;
        }
        log.debug("ROUTE_REQUEST requestId={} phase=START constructionSiteId={} cargoCapacityTons={} maxMarketsPerRun={}",
            requestId,
            request.getConstructionSiteId(),
            formatDouble(request.getCargoCapacityTons()),
            request.getMaxMarketsPerRun());
    }

    private void logConstructionSite(String requestId, ConstructionSiteDto site) {
        if (!log.isDebugEnabled()) {
            return;
        }
        log.debug("ROUTE_REQUEST requestId={} phase=SITE siteId={} marketId={} version={} lastUpdated={}",
            requestId,
            site.getSiteId(),
            site.getMarketId(),
            site.getVersion(),
            site.getLastUpdated());
    }

    private void logDemandSnapshot(String requestId, String stage, Map<String, MaterialDemand> demands) {
        if (!log.isDebugEnabled()) {
            return;
        }
        String snapshot = demands.values().stream()
            .map(demand -> "{material=%s,remaining=%s,initial=%s,scarcityWeight=%s}"
                .formatted(demand.displayName, formatDouble(demand.remaining),
                    formatDouble(demand.initialRequired), formatDouble(demand.scarcityWeight)))
            .collect(Collectors.joining(",", "[", "]"));
        log.debug("ROUTE_REQUEST requestId={} phase=DEMAND stage={} snapshot={}",
            requestId, stage, snapshot);
    }

    private void logCandidateMarkets(String requestId,
                                     List<MarketDto> candidateMarkets,
                                     List<MarketInventory> inventories) {
        if (!log.isDebugEnabled()) {
            return;
        }
        String marketsSummary = inventories.stream()
            .map(inventory -> "{marketId=%s,station=%s,system=%s,trackedMaterials=%s,totalStock=%s}"
                .formatted(inventory.market.getMarketId(),
                    resolveMarketDisplayName(inventory.market),
                    inventory.market.getSystemName(),
                    inventory.stock.keySet(),
                    formatDouble(inventory.initialTotalStock())))
            .collect(Collectors.joining(",", "[", "]"));
        log.debug("ROUTE_REQUEST requestId={} phase=MARKETS candidatesLoaded={} usableMarkets={} markets={}",
            requestId,
            candidateMarkets.size(),
            inventories.size(),
            marketsSummary);
    }

    private void logRunStart(String requestId,
                             int runIndex,
                             Map<String, MaterialDemand> demands,
                             RouteOptimizationRequest request) {
        if (!log.isDebugEnabled()) {
            return;
        }
        log.debug("ROUTE_RUN requestId={} phase=START runIndex={} remainingDemandTons={} cargoCapacityTons={} maxMarketsPerRun={}",
            requestId,
            runIndex,
            formatDouble(remainingDemand(demands.values())),
            formatDouble(request.getCargoCapacityTons()),
            Math.max(1, request.getMaxMarketsPerRun()));
    }

    private void logRunCompletion(String requestId, RunComputationResult result) {
        if (!log.isDebugEnabled() || result == null || result.runDto == null) {
            return;
        }
        DeliveryRunDto run = result.runDto;
        int legs = run.getLegs() == null ? 0 : run.getLegs().size();
        log.debug("ROUTE_RUN requestId={} phase=COMPLETE runIndex={} legs={} deliveredTons={} materialsSummary={}",
            requestId,
            run.getRunIndex(),
            legs,
            formatDouble(result.deliveredTonnage),
            run.getMaterialsSummaryTons());
    }

    private void logPlannedLeg(SelectionTrace trace,
                               MarketInventory market,
                               LegPlan plan,
                               RouteContext.SystemTransition transition) {
        if (!log.isDebugEnabled()) {
            return;
        }
        log.debug("ROUTE_LEG requestId={} runIndex={} legIndex={} marketId={} stationName={} loadedTons={} purchases={} fromSystem={} toSystem={} jumped={} totalJumps={}",
            trace.requestId(),
            trace.runIndex(),
            trace.legIndex(),
            market.market.getMarketId(),
            resolveMarketDisplayName(market.market),
            formatDouble(plan.loadedTons),
            summarizePurchases(plan.legDto.getPurchases()),
            transition.fromSystem(),
            transition.toSystem(),
            transition.jumped(),
            transition.totalJumps());
    }

    private void logCandidateEvaluation(SelectionTrace trace,
                                        MarketInventory inventory,
                                        int candidateOrder,
                                        double potentialLoad,
                                        double scarcityBonus,
                                        double systemMultiplier,
                                        double score,
                                        boolean feasible,
                                        String reason,
                                        double capacityLimit) {
        if (!log.isDebugEnabled()) {
            return;
        }
        log.debug("ROUTE_STEP requestId={} runIndex={} legIndex={} candidateOrder={} marketId={} marketSystem={} currentSystem={} potentialLoad={} scarcityBonus={} systemMultiplier={} score={} capacityLimit={} feasible={} reason={}",
            trace.requestId(),
            trace.runIndex(),
            trace.legIndex(),
            candidateOrder,
            inventory.market.getMarketId(),
            inventory.market.getSystemName(),
            trace.currentSystem(),
            formatDouble(potentialLoad),
            formatDouble(scarcityBonus),
            formatDouble(systemMultiplier),
            formatDouble(score),
            formatDouble(capacityLimit),
            feasible,
            reason);
    }

    private void logSelectionDecision(SelectionTrace trace,
                                      MarketInventory best,
                                      double bestScore,
                                      int evaluatedCount) {
        if (!log.isDebugEnabled()) {
            return;
        }
        if (best == null) {
            log.debug("ROUTE_DECISION requestId={} runIndex={} legIndex={} status=NO_FEASIBLE_MARKET candidatesEvaluated={}",
                trace.requestId(),
                trace.runIndex(),
                trace.legIndex(),
                evaluatedCount);
        } else {
            log.debug("ROUTE_DECISION requestId={} runIndex={} legIndex={} chosenMarketId={} stationName={} score={} candidatesEvaluated={}",
                trace.requestId(),
                trace.runIndex(),
                trace.legIndex(),
                best.market.getMarketId(),
                resolveMarketDisplayName(best.market),
                formatDouble(bestScore),
                evaluatedCount);
        }
    }

    private void logRouteSummary(String requestId,
                                 List<DeliveryRunDto> runs,
                                 double coverage,
                                 Map<String, MaterialDemand> demands,
                                 List<MarketInventory> inventories,
                                 double initialDemand) {
        double totalDelivered = runs.stream()
            .filter(Objects::nonNull)
            .map(DeliveryRunDto::getTotalTonnage)
            .filter(Objects::nonNull)
            .mapToDouble(Double::doubleValue)
            .sum();
        double remaining = remainingDemand(demands.values());
        String status;
        if (runs.isEmpty()) {
            status = "FAILED";
        } else if (remaining <= EPSILON) {
            status = "SUCCESS";
        } else {
            status = "PARTIAL";
        }
        String unassigned = demands.values().stream()
            .filter(MaterialDemand::hasRemaining)
            .map(demand -> "{material=%s,remaining=%s}"
                .formatted(demand.displayName, formatDouble(demand.remaining)))
            .collect(Collectors.joining(",", "[", "]"));
        String stationStats = buildStationStats(runs, inventories);
        log.debug("ROUTE_SUMMARY requestId={} status={} coverage={} totalDeliveredTons={} initialDemandTons={} remainingDemandTons={} runs={} stationStats={} unassignedDemands={}",
            requestId,
            status,
            formatDouble(coverage),
            formatDouble(totalDelivered),
            formatDouble(initialDemand),
            formatDouble(remaining),
            runs.size(),
            stationStats,
            unassigned);
        if (remaining > EPSILON) {
            logRouteWarning(requestId, "UNFULFILLED_DEMAND",
                "remainingDemandTons=" + formatDouble(remaining));
        }
    }

    private String buildStationStats(List<DeliveryRunDto> runs, List<MarketInventory> inventories) {
        Map<Long, StationSnapshot> snapshot = new LinkedHashMap<>();
        for (MarketInventory inventory : inventories) {
            if (inventory.market.getMarketId() == null) {
                continue;
            }
            snapshot.put(inventory.market.getMarketId(),
                new StationSnapshot(
                    inventory.market.getMarketId(),
                    resolveMarketDisplayName(inventory.market),
                    inventory.market.getSystemName(),
                    inventory.initialTotalStock(),
                    inventory.remainingTotalStock()));
        }
        for (DeliveryRunDto run : runs) {
            if (run.getLegs() == null) {
                continue;
            }
            for (RunLegDto leg : run.getLegs()) {
                Long marketId = leg.getMarketId();
                double delivered = sumPurchases(leg.getPurchases());
                StationSnapshot stats = snapshot.computeIfAbsent(marketId,
                    id -> new StationSnapshot(id, leg.getMarketName(), null, 0, 0));
                stats.recordVisit(delivered);
            }
        }
        return snapshot.values().stream()
            .map(StationSnapshot::toLogString)
            .collect(Collectors.joining(",", "[", "]"));
    }

    private String summarizePurchases(List<PurchaseDto> purchases) {
        if (purchases == null || purchases.isEmpty()) {
            return "[]";
        }
        return purchases.stream()
            .map(p -> "{material=%s,tons=%s}"
                .formatted(p.getMaterialName(), formatDouble(p.getAmountTons())))
            .collect(Collectors.joining(",", "[", "]"));
    }

    private void logRouteWarning(String requestId, String code, String message) {
        log.warn("ROUTE_WARNING requestId={} code={} message={}", requestId, code, message);
    }

    private record SelectionTrace(String requestId, int runIndex, int legIndex, String currentSystem) {}

    private static class StationSnapshot {
        private final Long marketId;
        private final String marketName;
        private final String systemName;
        private final double initialStock;
        private final double remainingStock;
        private double delivered;
        private int visits;

        StationSnapshot(Long marketId,
                        String marketName,
                        String systemName,
                        double initialStock,
                        double remainingStock) {
            this.marketId = marketId;
            this.marketName = marketName != null ? marketName : "Unknown Market";
            this.systemName = systemName != null ? systemName : "Unknown System";
            this.initialStock = initialStock;
            this.remainingStock = remainingStock;
        }

        void recordVisit(double deliveredTons) {
            this.delivered += deliveredTons;
            this.visits++;
        }

        String toLogString() {
            return "{marketId=%s,name=%s,system=%s,initialStock=%s,remainingStock=%s,delivered=%s,visits=%s}"
                .formatted(marketId,
                    marketName,
                    systemName,
                    formatDouble(initialStock),
                    formatDouble(remainingStock),
                    formatDouble(delivered),
                    visits);
        }
    }

    /**
     * Tracks the current state of the route being built.
     * Maintains current system location and accumulated jump count.
     */
    private static class RouteContext {
        private final String preferredSystem;
        private String currentSystem;
        private int jumpCount;
        
        RouteContext(String startingSystem) {
            this.preferredSystem = startingSystem;
            this.currentSystem = startingSystem;
            this.jumpCount = 0;
        }
        
        /**
         * Updates the context when moving to a new market.
         * Increments jump count if changing systems.
         */
        SystemTransition moveToMarket(MarketInventory market) {
            String marketSystem = market.market.getSystemName();
            String fromSystem = currentSystem;
            boolean jumped = false;
            if (marketSystem != null && currentSystem != null 
                && !marketSystem.equalsIgnoreCase(currentSystem)) {
                jumpCount++;
                jumped = true;
            }
            currentSystem = marketSystem;
            return new SystemTransition(fromSystem, currentSystem, jumped, jumpCount);
        }

        String getCurrentSystem() {
            return currentSystem;
        }

        String getPreferredSystem() {
            return preferredSystem;
        }

        private record SystemTransition(String fromSystem, String toSystem, boolean jumped, int totalJumps) {}
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

    private static final class SystemStats {
        private final String displayName;
        private int count;
        private double totalStock;

        private SystemStats(String displayName) {
            this.displayName = displayName;
        }

        void accumulate(MarketDto market) {
            count++;
            if (market.getItems() != null) {
                for (MarketItemDto item : market.getItems()) {
                    if (item != null) {
                        totalStock += item.getStock();
                    }
                }
            }
        }

        String getDisplayName() {
            return displayName;
        }

        int getCount() {
            return count;
        }

        double getTotalStock() {
            return totalStock;
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

        double initialTotalStock() {
            return stock.values().stream()
                .mapToDouble(MaterialStock::initialStock)
                .sum();
        }

        double remainingTotalStock() {
            return stock.values().stream()
                .mapToDouble(materialStock -> materialStock.stock)
                .sum();
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
        private final double initialStock;
        private double stock;

        MaterialStock(String key, double stock) {
            this.key = key;
            this.stock = stock;
            this.initialStock = stock;
        }

        double initialStock() {
            return initialStock;
        }
    }

    private record LegPlan(RunLegDto legDto, double loadedTons) {}

    private record RunComputationResult(DeliveryRunDto runDto, double deliveredTonnage) {}
}
