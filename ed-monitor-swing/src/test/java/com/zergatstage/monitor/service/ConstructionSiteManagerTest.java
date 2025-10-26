package com.zergatstage.monitor.service;

import com.zergatstage.domain.ConstructionSite;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConstructionSiteManagerTest {

    private ConstructionSiteManager siteManager;

    @BeforeEach
    void setUp() throws Exception {
        resetSingleton();
        siteManager = ConstructionSiteManager.getInstance();
        siteManager.getSites().clear();
    }

    @AfterEach
    void tearDown() throws Exception {
        resetSingleton();
    }

    @Test
    void updateSite_setsProvidedStationNameImmediately() throws org.json.JSONException {
        long marketId = 111L;
        JSONObject event = new JSONObject()
                .put("MarketID", marketId)
                .put("StationName", "Orbital Construction Site: Vega");

        siteManager.updateSite(marketId, event);

        assertEquals("Orbital Construction Site: Vega", siteManager.getSite(marketId).getSiteId());
    }

    @Test
    void updateSite_promotesStubSiteIdWhenNameArrives() throws org.json.JSONException {
        long marketId = 222L;
        JSONObject stubEvent = new JSONObject().put("MarketID", marketId);
        JSONObject namedEvent = new JSONObject()
                .put("MarketID", marketId)
                .put("StationName", "Orbital Construction Site: Altair");

        siteManager.updateSite(marketId, stubEvent);
        assertTrue(siteManager.getSite(marketId).getSiteId().startsWith("STUB_"));

        siteManager.updateSite(marketId, namedEvent);

        assertEquals("Orbital Construction Site: Altair", siteManager.getSite(marketId).getSiteId());
    }

    @Test
    void updateSite_overwritesMarketIdBasedName() throws org.json.JSONException {
        long marketId = 333L;
        ConstructionSite placeholder = ConstructionSite.builder()
                .marketId(marketId)
                .siteId(Long.toString(marketId))
                .requirements(new CopyOnWriteArrayList<>())
                .build();
        siteManager.getSites().put(marketId, placeholder);

        JSONObject namedEvent = new JSONObject()
                .put("MarketID", marketId)
                .put("StationName", "Orbital Construction Site: Sirius");

        siteManager.updateSite(marketId, namedEvent);

        assertEquals("Orbital Construction Site: Sirius", siteManager.getSite(marketId).getSiteId());
    }

    private void resetSingleton() throws Exception {
        Field instanceField = ConstructionSiteManager.class.getDeclaredField("instance");
        instanceField.setAccessible(true);
        instanceField.set(null, null);
    }
}
