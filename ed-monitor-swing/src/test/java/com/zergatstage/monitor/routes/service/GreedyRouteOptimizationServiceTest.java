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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    @Test
    void prefersMarketsInSameSystem() {
        CommodityDto metals = commodity(51, "Metals");

        ConstructionSiteDto site = siteDto(105L, requirement(51, metals, 60));

        MarketDto localMarket = market(601L, "Local Depot", item(metals, 60));
        localMarket.setSystemName("Sol");

        MarketDto distantMarket = market(602L, "Distant Depot", item(metals, 60));
        distantMarket.setSystemName("Achenar");

        MarketDto siteMarket = market(105L, "Construction Hub");
        siteMarket.setSystemName("Sol");

        GreedyRouteOptimizationService service =
            serviceWithSiteMarket(site, siteMarket, localMarket, distantMarket);
        RouteOptimizationRequest request = new RouteOptimizationRequest(105L, 30);

        RoutePlanDto plan = service.buildRoutePlan(request);

        assertEquals("Local Depot", plan.getRuns().get(0).getLegs().get(0).getMarketName());
    }

    @Test
    void onboardCargoIsCreditedAndDiscountsOnlyTheFirstRunCapacity() {
        CommodityDto steel = commodity(71, "Steel");
        ConstructionSiteDto site = siteDto(120L, requirement(71, steel, 20));
        MarketDto depot = market(801L, "Depot", item(steel, 50));

        // Total ship capacity is 20t; 12t of it is already occupied by onboard Steel.
        GreedyRouteOptimizationService service = service(site, depot);
        RouteOptimizationRequest request = new RouteOptimizationRequest(120L, 20);
        request.setOnboardCommodities(Map.of(71L, 12));

        RoutePlanDto plan = service.buildRoutePlan(request);

        assertEquals(1.0, plan.getCoverageFraction(), EPS);
        assertEquals(2, plan.getRuns().size(), "Onboard cargo plus a single market run for the remaining 8t");
        var onboardRun = plan.getRuns().get(0);
        assertEquals(1, onboardRun.getLegs().size());
        assertEquals(null, onboardRun.getLegs().get(0).getMarketId());
        assertEquals(12.0, onboardRun.getTotalTonnage(), EPS);
        assertEquals(8.0, plan.getRuns().get(1).getTotalTonnage(), EPS,
            "Only the remaining 8t should be bought - the other 12t of capacity is already used by onboard cargo");
    }

    @Test
    void onboardCargoOnlyDiscountsTheFirstMarketRunNotLaterOnes() {
        CommodityDto steel = commodity(73, "Steel");
        // Demand well beyond what a single 20t trip can carry, so a second run is required.
        ConstructionSiteDto site = siteDto(122L, requirement(73, steel, 28));
        MarketDto depot = market(803L, "Depot", item(steel, 50));

        GreedyRouteOptimizationService service = service(site, depot);
        RouteOptimizationRequest request = new RouteOptimizationRequest(122L, 20);
        request.setOnboardCommodities(Map.of(73L, 12));

        RoutePlanDto plan = service.buildRoutePlan(request);

        assertEquals(1.0, plan.getCoverageFraction(), EPS);
        assertEquals(3, plan.getRuns().size(), "Onboard credit, an 8t first market run, then a full 20t second run");
        assertEquals(12.0, plan.getRuns().get(0).getTotalTonnage(), EPS);
        assertEquals(8.0, plan.getRuns().get(1).getTotalTonnage(), EPS,
            "First market run is capped by the 12t already occupied onboard");
        assertEquals(8.0, plan.getRuns().get(2).getTotalTonnage(), EPS,
            "Second run gets the full 20t capacity back but only 8t remains to satisfy demand");
    }

    @Test
    void onboardCargoNotNeededByDemandStillOccupiesHullSpaceButSkipsTheSyntheticRun() {
        CommodityDto steel = commodity(72, "Steel");
        ConstructionSiteDto site = siteDto(121L, requirement(72, steel, 10));
        MarketDto depot = market(802L, "Depot", item(steel, 50));

        // 5t of unrelated cargo occupies half of the 10t hold, so it still takes two 5t trips.
        GreedyRouteOptimizationService service = service(site, depot);
        RouteOptimizationRequest request = new RouteOptimizationRequest(121L, 10);
        request.setOnboardCommodities(Map.of(999L, 5));

        RoutePlanDto plan = service.buildRoutePlan(request);

        assertEquals(2, plan.getRuns().size(),
            "No synthetic onboard-credit run since it doesn't match demand, but hull space is still reduced");
        assertEquals(5.0, plan.getRuns().get(0).getTotalTonnage(), EPS,
            "First run capped to 5t since 5t of the hold is already occupied by unrelated cargo");
        assertEquals(5.0, plan.getRuns().get(1).getTotalTonnage(), EPS);
    }

    @Test
    void onboardTonsExceedingCapacityFallsBackToFullCapacityInsteadOfAborting() {
        CommodityDto steel = commodity(74, "Steel");
        CommodityDto titanium = commodity(75, "Titanium");
        ConstructionSiteDto site = siteDto(123L,
            requirement(74, steel, 50),
            requirement(75, titanium, 10));
        MarketDto depot = market(804L, "Depot", item(steel, 50));

        // A bad/unknown capacity value (20t) that's smaller than what's already reported onboard
        // (30t) is physically impossible - the optimizer must not silently zero out the first
        // market run because of it.
        GreedyRouteOptimizationService service = service(site, depot);
        RouteOptimizationRequest request = new RouteOptimizationRequest(123L, 20);
        request.setOnboardCommodities(Map.of(75L, 30));

        RoutePlanDto plan = service.buildRoutePlan(request);

        assertEquals(1.0, plan.getCoverageFraction(), EPS);
        assertEquals(4, plan.getRuns().size(),
            "Onboard credit for titanium, then three 20t market runs to cover the 50t of steel demand");
        assertEquals(20.0, plan.getRuns().get(1).getTotalTonnage(), EPS,
            "First market run should use full capacity, not be aborted by a bogus zero capacity");
    }

    @Test
    void infersConstructionSiteSystemFromDominantMarkets() {
        CommodityDto metals = commodity(61, "Metals");
        ConstructionSiteDto site = siteDto(111L, requirement(61, metals, 60));

        MarketDto local = market(701L, "Local Hub", item(metals, 60));
        local.setSystemName("Colonia");

        MarketDto remote = market(702L, "Remote Hub", item(metals, 5));
        remote.setSystemName("Beagle Point");

        GreedyRouteOptimizationService service = service(site, local, remote);
        RoutePlanDto plan = service.buildRoutePlan(new RouteOptimizationRequest(111L, 60));

        assertEquals("Local Hub", plan.getRuns().get(0).getLegs().get(0).getMarketName());
    }

    @Test
    void prefersOrbitalStationOverPlanetaryPortWithEqualStockInSameSystem() {
        CommodityDto metals = commodity(52, "Metals");

        ConstructionSiteDto site = siteDto(106L, requirement(52, metals, 60));

        MarketDto orbital = market(603L, "Orbital Dock", item(metals, 60));
        orbital.setStationType("Coriolis");

        MarketDto planetary = market(604L, "Surface Hub", item(metals, 60));
        planetary.setStationType("CraterOutpost");

        // Both markets share the default "System" system name from the market() helper,
        // so the only difference the optimizer should react to is dock type.
        GreedyRouteOptimizationService service = service(site, planetary, orbital);
        RouteOptimizationRequest request = new RouteOptimizationRequest(106L, 60);

        RoutePlanDto plan = service.buildRoutePlan(request);

        assertEquals("Orbital Dock", plan.getRuns().get(0).getLegs().get(0).getMarketName(),
            "Orbital station should be preferred over an equally-stocked planetary port");
    }

    @Test
    void planetaryPortStillWinsWhenItIsTheOnlyOptionWithMatchingStock() {
        CommodityDto metals = commodity(53, "Metals");

        ConstructionSiteDto site = siteDto(107L, requirement(53, metals, 40));

        MarketDto planetary = market(605L, "Only Surface Hub", item(metals, 40));
        planetary.setStationType("CraterOutpost");

        GreedyRouteOptimizationService service = service(site, planetary);
        RouteOptimizationRequest request = new RouteOptimizationRequest(107L, 40);

        RoutePlanDto plan = service.buildRoutePlan(request);

        assertEquals(1.0, plan.getCoverageFraction(), EPS,
            "Planetary port should still be used when it's the only source of the material");
    }

    private GreedyRouteOptimizationService service(ConstructionSiteDto site, MarketDto... markets) {
        return new GreedyRouteOptimizationService(
            new FakeDataProvider(site, Arrays.asList(markets), null));
    }

    private GreedyRouteOptimizationService serviceWithSiteMarket(ConstructionSiteDto site,
                                                                 MarketDto siteMarket,
                                                                 MarketDto... markets) {
        return new GreedyRouteOptimizationService(
            new FakeDataProvider(site, Arrays.asList(markets), siteMarket));
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
        private final Map<Long, MarketDto> marketsById = new HashMap<>();

        private FakeDataProvider(ConstructionSiteDto site, List<MarketDto> markets, MarketDto siteMarket) {
            this.site = site;
            this.markets = markets;
            if (siteMarket != null && siteMarket.getMarketId() != null) {
                marketsById.put(siteMarket.getMarketId(), siteMarket);
            }
            for (MarketDto market : markets) {
                if (market != null && market.getMarketId() != null) {
                    marketsById.put(market.getMarketId(), market);
                }
            }
        }

        @Override
        public ConstructionSiteDto loadConstructionSite(long constructionSiteId) throws IOException {
            return site;
        }

        @Override
        public List<MarketDto> loadCandidateMarkets(long constructionSiteId) throws IOException {
            return markets;
        }

        @Override
        public MarketDto loadMarket(long marketId) throws IOException {
            return marketsById.get(marketId);
        }
    }
}
