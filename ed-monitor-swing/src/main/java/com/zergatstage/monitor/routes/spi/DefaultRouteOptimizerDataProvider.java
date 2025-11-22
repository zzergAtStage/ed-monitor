package com.zergatstage.monitor.routes.spi;

import com.zergatstage.monitor.http.dto.CommodityDto;
import com.zergatstage.monitor.http.dto.ConstructionSiteDto;
import com.zergatstage.monitor.http.dto.MarketDto;
import com.zergatstage.monitor.http.dto.MarketItemDto;
import com.zergatstage.monitor.http.dto.MaterialRequirementDto;
import com.zergatstage.monitor.service.ConstructionSitesHttpService;
import com.zergatstage.monitor.service.MarketDataHttpService;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Default implementation that calls existing Swing HTTP services to obtain construction site
 * and market data, then filters it to what the optimizer needs.
 */
public class DefaultRouteOptimizerDataProvider implements RouteOptimizerDataProvider {

    private final ConstructionSitesHttpService constructionSitesHttpService;
    private final MarketDataHttpService marketDataHttpService;

    /**
     * Creates a data provider using the shared ED Monitor base URL resolution logic.
     *
     * @param baseUrl ED Monitor server base URL
     */
    public DefaultRouteOptimizerDataProvider(String baseUrl) {
        this(new ConstructionSitesHttpService(baseUrl), new MarketDataHttpService(baseUrl));
    }

    /**
     * Creates a data provider with explicit dependencies (useful for testing).
     *
     * @param constructionSitesHttpService HTTP client for construction site resources
     * @param marketDataHttpService        HTTP client for market resources
     */
    public DefaultRouteOptimizerDataProvider(ConstructionSitesHttpService constructionSitesHttpService,
                                             MarketDataHttpService marketDataHttpService) {
        this.constructionSitesHttpService = constructionSitesHttpService;
        this.marketDataHttpService = marketDataHttpService;
    }

    @Override
    public ConstructionSiteDto loadConstructionSite(long constructionSiteId) throws IOException {
        return constructionSitesHttpService.getSite(constructionSiteId);
    }

    @Override
    public List<MarketDto> loadCandidateMarkets(long constructionSiteId) throws IOException {
        ConstructionSiteDto site = loadConstructionSite(constructionSiteId);
        if (site == null) {
            return List.of();
        }

        Set<Long> requiredCommodityIds = new HashSet<>();
        Set<String> requiredCommodityNames = new HashSet<>();
        List<MaterialRequirementDto> requirements = site.getRequirements();
        if (requirements != null) {
            for (MaterialRequirementDto requirement : requirements) {
                if (requirement == null) {
                    continue;
                }
                if (requirement.getRequiredQuantity() <= requirement.getDeliveredQuantity()) {
                    continue;
                }
                CommodityDto commodity = requirement.getCommodity();
                if (commodity == null) {
                    continue;
                }
                if (commodity.getId() != null) {
                    requiredCommodityIds.add(commodity.getId());
                }
                if (commodity.getName() != null) {
                    requiredCommodityNames.add(commodity.getName().toLowerCase(Locale.ROOT));
                }
            }
        }

        if (requiredCommodityIds.isEmpty() && requiredCommodityNames.isEmpty()) {
            return List.of();
        }

        List<MarketDto> markets = marketDataHttpService.getMarkets();
        List<MarketDto> candidates = new ArrayList<>();
        for (MarketDto market : markets) {
            if (market == null || market.getItems() == null) {
                continue;
            }
            List<MarketItemDto> matchingItems = market.getItems().stream()
                .filter(item -> item != null && matchesRequiredCommodity(item, requiredCommodityIds, requiredCommodityNames))
                .toList();
            if (!matchingItems.isEmpty()) {
                // TODO(route-optimizer): replace client-side filtering with dedicated "candidate markets" endpoint.
                candidates.add(new MarketDto(
                    market.getMarketId(),
                    market.getStationName(),
                    market.getStationType(),
                    market.getSystemName(),
                    new ArrayList<>(matchingItems)
                ));
            }
        }
        return candidates;
    }

    @Override
    public MarketDto loadMarket(long marketId) throws IOException {
        return marketDataHttpService.getMarket(marketId);
    }

    private boolean matchesRequiredCommodity(MarketItemDto item,
                                             Set<Long> requiredCommodityIds,
                                             Set<String> requiredCommodityNames) {
        CommodityDto commodity = item.getCommodity();
        if (commodity == null) {
            return false;
        }
        Long commodityId = commodity.getId();
        if (commodityId != null && requiredCommodityIds.contains(commodityId)) {
            return true;
        }
        String name = commodity.getName();
        return name != null && requiredCommodityNames.contains(name.toLowerCase(Locale.ROOT));
    }
}
