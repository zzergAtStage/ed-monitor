package com.zergatstage.monitor.routes.service;

import com.zergatstage.monitor.http.dto.CommodityDto;
import com.zergatstage.monitor.http.dto.ConstructionSiteDto;
import com.zergatstage.monitor.http.dto.MarketDto;
import com.zergatstage.monitor.http.dto.MarketItemDto;
import com.zergatstage.monitor.http.dto.MaterialRequirementDto;
import com.zergatstage.monitor.routes.dto.RouteOptimizationRequest;
import com.zergatstage.monitor.routes.dto.RoutePlanDto;
import com.zergatstage.monitor.routes.spi.RouteOptimizerDataProvider;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GreedyRouteOptimizationServiceTest {

    private static final double EPS = 1.0e-6;

    @Test
    void singleMarketCoversAllMaterialsInOneRun() {
        CommodityDto steel = commodity(1, "Steel");
        CommodityDto polymers = commodity(2, "Polymers");

        ConstructionSiteDto site = siteDto(100L,
            requirement(1, steel, 10),
            requirement(2, polymers, 5));

        MarketDto depot = market(200L, "Depot",
            item(steel, 50),
            item(polymers, 50));

        GreedyRouteOptimizationService service = service(site, depot);
        RouteOptimizationRequest request = new RouteOptimizationRequest(100L, 100);

        RoutePlanDto plan = service.buildRoutePlan(request);

        assertEquals(1, plan.getRuns().size());
        assertEquals(1.0, plan.getCoverageFraction(), EPS);
        assertEquals(100L, plan.getConstructionSiteId());

        var run = plan.getRuns().get(0);
        assertEquals(1, run.getLegs().size());
        assertEquals(15.0, run.getTotalTonnage(), EPS);
        assertEquals(2, run.getMaterialsSummaryTons().size());
    }

    @Test
    void scarceMaterialMarketChosenAsPrimary() {
        CommodityDto rare = commodity(11, "RareMetal");
        CommodityDto common = commodity(12, "CommonAlloy");

        ConstructionSiteDto site = siteDto(101L,
            requirement(11, rare, 4),
            requirement(12, common, 4));

        MarketDto scarceMarket = market(301L, "Scarce Hub",
            item(rare, 4),
            item(common, 2));
        MarketDto commonMarket = market(302L, "Common Depot",
            item(common, 10));

        GreedyRouteOptimizationService service = service(site, scarceMarket, commonMarket);
        RouteOptimizationRequest request = new RouteOptimizationRequest(101L, 10);

        RoutePlanDto plan = service.buildRoutePlan(request);

        assertEquals(1, plan.getRuns().size());
        var run = plan.getRuns().get(0);
        assertEquals(2, run.getLegs().size(), "Expected secondary market to top off cargo");
        assertEquals(301L, run.getLegs().get(0).getMarketId(), "Scarce market should be primary leg");
        assertEquals(302L, run.getLegs().get(1).getMarketId(), "Common market should be used to fill remaining demand");
        assertEquals(8.0, run.getTotalTonnage(), EPS);
    }

    @Test
    void limitedCapacityProducesMultipleRuns() {
        CommodityDto components = commodity(21, "Components");
        ConstructionSiteDto site = siteDto(102L, requirement(21, components, 25));
        MarketDto warehouse = market(401L, "Warehouse", item(components, 40));

        GreedyRouteOptimizationService service = service(site, warehouse);
        RouteOptimizationRequest request = new RouteOptimizationRequest(102L, 10);

        RoutePlanDto plan = service.buildRoutePlan(request);

        assertEquals(3, plan.getRuns().size(), "Demand requires three trips with 10t capacity");
        assertEquals(1.0, plan.getCoverageFraction(), EPS);
        assertEquals(10.0, plan.getRuns().get(0).getTotalTonnage(), EPS);
        assertEquals(10.0, plan.getRuns().get(1).getTotalTonnage(), EPS);
        assertEquals(5.0, plan.getRuns().get(2).getTotalTonnage(), EPS);
    }

    @Test
    void insufficientStockResultsInPartialCoverage() {
        CommodityDto alloys = commodity(31, "Alloys");
        ConstructionSiteDto site = siteDto(103L, requirement(31, alloys, 30));
        MarketDto minorOutpost = market(501L, "Minor Outpost", item(alloys, 20));

        GreedyRouteOptimizationService service = service(site, minorOutpost);
        RouteOptimizationRequest request = new RouteOptimizationRequest(103L, 15);

        RoutePlanDto plan = service.buildRoutePlan(request);

        assertTrue(plan.getCoverageFraction() < 1.0);
        assertEquals(20.0 / 30.0, plan.getCoverageFraction(), EPS);
        assertEquals(2, plan.getRuns().size());
    }

    @Test
    void noCandidateMarketsReturnsEmptyPlan() {
        CommodityDto alloys = commodity(41, "Alloys");
        ConstructionSiteDto site = siteDto(104L, requirement(41, alloys, 10));

        GreedyRouteOptimizationService service = service(site /* no markets */);
        RouteOptimizationRequest request = new RouteOptimizationRequest(104L, 20);

        RoutePlanDto plan = service.buildRoutePlan(request);

        assertTrue(plan.getRuns().isEmpty());
        assertEquals(0.0, plan.getCoverageFraction(), EPS);
    }

    private GreedyRouteOptimizationService service(ConstructionSiteDto site, MarketDto... markets) {
        return new GreedyRouteOptimizationService(new FakeDataProvider(site, Arrays.asList(markets)));
    }

    private static CommodityDto commodity(long id, String name) {
        return new CommodityDto(id, name, name, "category", "category");
    }

    private static MaterialRequirementDto requirement(long id, CommodityDto commodity, int required) {
        return new MaterialRequirementDto(id, commodity, required, 0);
    }

    private static MarketItemDto item(CommodityDto commodity, int stock) {
        return new MarketItemDto(commodity, 0, 0, stock, 0);
    }

    private static MarketDto market(long id, String name, MarketItemDto... items) {
        MarketDto market = new MarketDto();
        market.setMarketId(id);
        market.setStationName(name);
        market.setStationType("Outpost");
        market.setSystemName("System");
        market.setItems(new ArrayList<>(List.of(items)));
        return market;
    }

    private static ConstructionSiteDto siteDto(long siteId, MaterialRequirementDto... requirements) {
        ConstructionSiteDto dto = new ConstructionSiteDto();
        dto.setMarketId(siteId);
        dto.setSiteId("SITE-" + siteId);
        dto.setRequirements(new ArrayList<>(List.of(requirements)));
        return dto;
    }

    private static final class FakeDataProvider implements RouteOptimizerDataProvider {
        private final ConstructionSiteDto site;
        private final List<MarketDto> markets;

        private FakeDataProvider(ConstructionSiteDto site, List<MarketDto> markets) {
            this.site = site;
            this.markets = markets;
        }

        @Override
        public ConstructionSiteDto loadConstructionSite(long constructionSiteId) throws IOException {
            return site;
        }

        @Override
        public List<MarketDto> loadCandidateMarkets(long constructionSiteId) throws IOException {
            return markets;
        }
    }
}
