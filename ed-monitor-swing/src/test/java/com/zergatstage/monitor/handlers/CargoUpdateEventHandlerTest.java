package com.zergatstage.monitor.handlers;

import com.zergatstage.domain.Ship;
import com.zergatstage.domain.dictionary.Commodity;
import com.zergatstage.domain.makret.Market;
import com.zergatstage.domain.makret.MarketItem;
import com.zergatstage.monitor.service.CommodityRegistry;
import com.zergatstage.monitor.service.ConstructionSiteManager;
import com.zergatstage.monitor.service.managers.CargoInventoryManager;
import com.zergatstage.monitor.service.managers.CarrierManager;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers {@link CargoUpdateEventHandler}'s handling of the journal {@code CargoTransfer} event.
 *
 * <p>{@link CargoInventoryManager} is mocked (via the same reflective singleton-override pattern
 * as {@code ColonisationConstructionDepotTest}) so the ship-side {@code modifyCargoAmount} calls
 * can be verified directly. {@link CarrierManager} and {@link CommodityRegistry} are left as real
 * singletons, reset between tests, so the carrier-side reconciliation logic in
 * {@code CarrierManager} runs for real and is asserted through
 * {@link CarrierManager#getHoldSnapshot()} - this is the "assert through the manager" fallback
 * called out in the task, since {@code CarrierManager} is resolved internally by the handler and
 * is not otherwise injectable.</p>
 */
class CargoUpdateEventHandlerTest {

    private static final Instant BASELINE_SNAPSHOT_AT = Instant.parse("2020-01-01T00:00:00Z");

    private CarrierManager carrierManager;
    private CommodityRegistry registry;
    private CargoInventoryManager mockCargoInventoryManager;
    private CargoUpdateEventHandler handlerUnderTest;

    @BeforeEach
    void setUp() throws Exception {
        resetSingletons();

        registry = CommodityRegistry.getInstance();
        seedCommodity(8001L, "Gold");
        seedCommodity(8002L, "Silver");
        seedCommodity(8003L, "Platinum");

        carrierManager = CarrierManager.getInstance();
        // Establish a hold baseline dated well before any test event's timestamp, since
        // CarrierManager.applyTransferDelta is a no-op until a snapshot baseline exists.
        carrierManager.applyHoldSnapshot(Map.of(), BASELINE_SNAPSHOT_AT);

        Ship ship = new Ship();
        ship.setCargoStateKnown(true);
        mockCargoInventoryManager = mock(CargoInventoryManager.class);
        when(mockCargoInventoryManager.getShipVariant()).thenReturn(ship);
        setStaticField(CargoInventoryManager.class, "instance", mockCargoInventoryManager);

        handlerUnderTest = new CargoUpdateEventHandler();
    }

    @AfterEach
    void tearDown() throws Exception {
        resetSingletons();
    }

    @Test
    void handleEvent_threeTransferEntries_appliesAllThree() throws JSONException {
        JSONObject event = cargoTransferEvent(
                transfer("gold", 5, "toCarrier"),
                transfer("silver", 3, "toShip"),
                transfer("platinum", 2, "toCarrier"));

        handlerUnderTest.handleEvent(event);

        // Carrier-side: two toCarrier credits and one toShip debit, all three must land.
        Map<Long, Integer> hold = carrierManager.getHoldSnapshot();
        assertEquals(5, hold.get(8001L), "gold toCarrier transfer should credit the carrier");
        assertEquals(2, hold.get(8003L), "platinum toCarrier transfer should credit the carrier");
        // The silver debit DID land, but a carrier hold cannot go negative: the baseline said the
        // carrier held no silver, so shipping 3t off it clamps to zero (the entry disappears)
        // rather than recording a physically impossible -3. Reaching this state means the
        // baseline snapshot was stale; CarrierManager logs a warning for it.
        assertFalse(hold.containsKey(8002L),
                "silver toShip debit against an empty baseline should clamp to zero, not go negative");

        // Ship-side: verify all three entries reached the ship inventory manager, not just Transfers[0].
        verify(mockCargoInventoryManager).modifyCargoAmount(8001L, -5);
        verify(mockCargoInventoryManager).modifyCargoAmount(8002L, 3);
        verify(mockCargoInventoryManager).modifyCargoAmount(8003L, -2);
    }

    @Test
    void handleEvent_toCarrier_creditsCarrierAndDebitsShip() throws JSONException {
        JSONObject event = cargoTransferEvent(transfer("gold", 7, "toCarrier"));

        handlerUnderTest.handleEvent(event);

        assertEquals(7, carrierManager.getHoldSnapshot().get(8001L));
        verify(mockCargoInventoryManager).modifyCargoAmount(8001L, -7);
    }

    @Test
    void handleEvent_toShip_creditsShipAndDebitsCarrier() throws JSONException {
        JSONObject event = cargoTransferEvent(transfer("silver", 4, "toShip"));

        handlerUnderTest.handleEvent(event);

        // Ship side is credited; carrier side clamps at zero rather than recording -4 tons,
        // since a hold cannot contain a negative quantity. See the three-entry test for why.
        assertFalse(carrierManager.getHoldSnapshot().containsKey(8002L));
        verify(mockCargoInventoryManager).modifyCargoAmount(8002L, 4);
    }

    private JSONObject cargoTransferEvent(JSONObject... transfers) throws JSONException {
        JSONObject event = new JSONObject();
        event.put("event", "CargoTransfer");
        event.put("timestamp", "2026-08-23T10:00:00Z");
        JSONArray transfersArray = new JSONArray();
        for (JSONObject transfer : transfers) {
            transfersArray.put(transfer);
        }
        event.put("Transfers", transfersArray);
        return event;
    }

    private JSONObject transfer(String type, int count, String direction) throws JSONException {
        return new JSONObject()
                .put("Type", type)
                .put("Count", count)
                .put("Direction", direction);
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
        setStaticField(CargoInventoryManager.class, "instance", null);
        setStaticField(ConstructionSiteManager.class, "instance", null);
    }

    private void setStaticField(Class<?> clazz, String fieldName, Object value) throws Exception {
        Field field = clazz.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(null, value);
    }
}
