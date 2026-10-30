package com.zergatstage.monitor.routes.ui;

import com.zergatstage.monitor.routes.dto.CarrierSnapshot;
import com.zergatstage.monitor.routes.dto.HaulingMode;
import com.zergatstage.monitor.routes.dto.RouteOptimizationRequest;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Guards the request round-tripping done by {@link RouteOptimizerModel}: every parameter update
 * copies the stored request, so any field the copy forgets is silently lost before the optimizer
 * ever sees it.
 */
class RouteOptimizerModelTest {

    @Test
    void updateRequestKeepsSiteModeAndCarrierAcrossCopies() {
        RouteOptimizerModel model = new RouteOptimizerModel();
        CarrierSnapshot carrier = new CarrierSnapshot(3700005678L, "Sol", 12_000, Map.of(128L, 40));

        model.updateRouteOptimizationRequest(request -> {
            request.setConstructionSiteId(42L);
            request.setMode(HaulingMode.LOAD_CARRIER);
            request.setCarrier(carrier);
        });
        // Mimics the UI's follow-up parameter pushes (capacity / max markets / onboard cargo),
        // each of which round-trips the request through the model's internal copy.
        model.updateRouteOptimizationRequest(request -> {
            request.setCargoCapacityTons(720.0);
            request.setMaxMarketsPerRun(3);
        });
        model.updateRouteOptimizationRequest(request -> request.setOnboardCommodities(Map.of(128L, 10)));

        RouteOptimizationRequest snapshot = model.getRouteOptimizationRequest();
        assertEquals(42L, snapshot.getConstructionSiteId());
        assertEquals(720.0, snapshot.getCargoCapacityTons());
        assertEquals(3, snapshot.getMaxMarketsPerRun());
        assertEquals(Map.of(128L, 10), snapshot.getOnboardCommodities());
        assertEquals(HaulingMode.LOAD_CARRIER, snapshot.getMode());
        assertNotNull(snapshot.getCarrier());
        assertEquals(3700005678L, snapshot.getCarrier().getCarrierId());
        assertEquals("Sol", snapshot.getCarrier().getSystemName());
    }
}
