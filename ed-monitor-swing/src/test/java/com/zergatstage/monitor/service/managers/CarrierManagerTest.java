package com.zergatstage.monitor.service.managers;

import com.zergatstage.domain.dictionary.Commodity;
import com.zergatstage.domain.makret.Market;
import com.zergatstage.domain.makret.MarketItem;
import com.zergatstage.monitor.service.CommodityRegistry;
import org.json.JSONException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure-unit coverage of {@link CarrierManager}'s snapshot/delta reconciliation rules, following
 * the fixture-builder style of {@code GreedyRouteOptimizationServiceTest} and the singleton-reset
 * approach used by {@code ConstructionSiteManagerTest}.
 *
 * <p>{@link CarrierManager} and {@link CommodityRegistry} are both process-wide singletons, so
 * each test resets their static {@code instance} fields via reflection and reseeds the registry
 * from scratch. Each test also uses a commodity id unique to it, so the tests remain
 * order-independent even if the reflective reset were ever removed.</p>
 */
class CarrierManagerTest {

    private CarrierManager manager;
    private CommodityRegistry registry;

    @BeforeEach
    void setUp() throws Exception {
        resetSingletons();
        registry = CommodityRegistry.getInstance();
        manager = CarrierManager.getInstance();
    }

    @AfterEach
    void tearDown() throws Exception {
        resetSingletons();
    }

    @Test
    void applyTransferDelta_withNoBaseline_isNoOp() throws JSONException {
        seedCommodity(9001L, "Gold");

        manager.applyTransferDelta(9001L, 5, Instant.now());

        assertTrue(manager.getHoldSnapshot().isEmpty(), "A delta with no snapshot baseline must be ignored");
        assertEquals(null, manager.getHoldSnapshotAt());
    }

    @Test
    void applyHoldSnapshot_setsHoldAbsolutelyAndEstablishesBaseline() throws JSONException {
        seedCommodity(9002L, "Silver");
        Instant snapshotAt = Instant.now();

        manager.applyHoldSnapshot(Map.of(9002L, 20), snapshotAt);

        assertEquals(20, manager.getHoldSnapshot().get(9002L));
        assertEquals(snapshotAt, manager.getHoldSnapshotAt());
    }

    @Test
    void applyTransferDelta_afterSnapshot_isAppliedAndAddsToTotal() throws JSONException {
        seedCommodity(9003L, "Palladium");
        Instant snapshotAt = Instant.now();
        manager.applyHoldSnapshot(Map.of(9003L, 10), snapshotAt);

        manager.applyTransferDelta(9003L, 5, snapshotAt.plusSeconds(10));

        assertEquals(15, manager.getHoldSnapshot().get(9003L));
    }

    @Test
    void applyTransferDelta_atOrBeforeSnapshot_isIgnored() throws JSONException {
        seedCommodity(9004L, "Platinum");
        Instant snapshotAt = Instant.now();
        manager.applyHoldSnapshot(Map.of(9004L, 10), snapshotAt);

        // Exactly at the snapshot instant: already baked into the snapshot total.
        manager.applyTransferDelta(9004L, 5, snapshotAt);
        assertEquals(10, manager.getHoldSnapshot().get(9004L));

        // Strictly before the snapshot instant: also already covered.
        manager.applyTransferDelta(9004L, 5, snapshotAt.minusSeconds(5));
        assertEquals(10, manager.getHoldSnapshot().get(9004L));
    }

    @Test
    void applyHoldSnapshot_staleSnapshot_doesNotRegressHold() throws JSONException {
        seedCommodity(9005L, "Osmium");
        Instant snapshotAt = Instant.now();
        manager.applyHoldSnapshot(Map.of(9005L, 10), snapshotAt);

        Instant staleSnapshotAt = snapshotAt.minusSeconds(60);
        manager.applyHoldSnapshot(Map.of(9005L, 999), staleSnapshotAt);

        assertEquals(10, manager.getHoldSnapshot().get(9005L), "Stale snapshot must not regress the hold");
        assertEquals(snapshotAt, manager.getHoldSnapshotAt(), "Stale snapshot must not overwrite the stored snapshot time");
    }

    @Test
    void updateSystem_isNoOpAfterSetSystemManually_butWorksBefore() {
        manager.updateSystem("Sol");
        assertEquals("Sol", manager.getCurrentSystem());

        manager.setSystemManually("Deciat");
        assertEquals("Deciat", manager.getCurrentSystem());

        manager.updateSystem("Sol");
        assertEquals("Deciat", manager.getCurrentSystem(), "updateSystem must be a no-op after a manual override");
    }

    private void seedCommodity(long id, String name) throws JSONException {
        Commodity commodity = Commodity.builder()
                .id(id)
                .name(name)
                .nameLocalised(name)
                .category("category")
                .categoryLocalised("category")
                .build();
        MarketItem item = MarketItem.builder()
                .commodity(commodity)
                .build();
        Market market = Market.builder()
                .marketId(1L)
                .items(new HashMap<>())
                .build();
        market.addItem(item);

        Map<Long, Market> markets = new HashMap<>();
        markets.put(market.getMarketId(), market);
        registry.loadMarketData(markets);
    }

    private void resetSingletons() throws Exception {
        setStaticField(CarrierManager.class, "instance", null);
        setStaticField(CommodityRegistry.class, "instance", null);
    }

    private void setStaticField(Class<?> clazz, String fieldName, Object value) throws Exception {
        Field field = clazz.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(null, value);
    }
}
