package com.zergatstage.monitor.handlers;

import com.zergatstage.monitor.service.ConstructionSiteManager;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.jupiter.api.*;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ColonisationConstructionDepotTest {

    private ConstructionSiteManager mockSiteManager;
    private ColonisationConstructionDepot handlerUnderTest;

    @BeforeEach
    void setUp() throws Exception {
        mockSiteManager = mock(ConstructionSiteManager.class);
        overrideSingleton(mockSiteManager);
        handlerUnderTest = new ColonisationConstructionDepot();
    }

    @AfterEach
    void tearDown() throws Exception {
        Mockito.reset(mockSiteManager);
        overrideSingleton(null);
    }

    @Test
    void handleEvent_samePayloadTwice_skipsDuplicateUpdate() throws JSONException {
        JSONObject event = createEvent(42L, "StructuralRegulators");

        handlerUnderTest.handleEvent(event);
        handlerUnderTest.handleEvent(event);

        verify(mockSiteManager, times(1)).updateSite(eq(42L), any(JSONObject.class));
    }

    @Test
    @Timeout(5)
    void handleEvent_parallelMarkets_shouldNotPolluteFingerprintCache() throws InterruptedException, JSONException {
        long marketAlpha = 1_000_001L;
        long marketBeta = 9_999_999L;

        JSONObject alphaEvent = createEvent(marketAlpha, "PowerGridFeed");
        JSONObject betaEvent = createEvent(marketBeta, "HullSegments");

        CountDownLatch alphaEnteredUpdate = new CountDownLatch(1);
        CountDownLatch releaseAlpha = new CountDownLatch(1);

        doAnswer(invocation -> {
            long marketId = invocation.getArgument(0);
            if (marketId == marketAlpha) {
                alphaEnteredUpdate.countDown();
                assertTrue(releaseAlpha.await(2, TimeUnit.SECONDS), "Timed out waiting to release alpha update");
            }
            return null;
        }).when(mockSiteManager).updateSite(anyLong(), any(JSONObject.class));

        Thread alphaThread = new Thread(() -> handlerUnderTest.handleEvent(alphaEvent), "alpha-market-thread");
        alphaThread.start();

        assertTrue(alphaEnteredUpdate.await(2, TimeUnit.SECONDS), "Alpha handler never reached updateSite");

        handlerUnderTest.handleEvent(betaEvent);

        releaseAlpha.countDown();
        alphaThread.join();

        handlerUnderTest.handleEvent(createEvent(marketAlpha, "PowerGridFeed"));

        verify(mockSiteManager, times(1)).updateSite(eq(marketAlpha), any(JSONObject.class));
    }

    private JSONObject createEvent(long marketId, String commodityName) throws JSONException {
        JSONObject root = new JSONObject();
        root.put("event", "ColonisationConstructionDepot");
        root.put("MarketID", marketId);
        JSONArray resources = new JSONArray();
        JSONObject resource = new JSONObject();
        resource.put("Name", commodityName);
        resource.put("Name_Localised", commodityName + " Localised");
        resource.put("RequiredAmount", 100);
        resource.put("ProvidedAmount", 10);
        resources.put(resource);
        root.put("ResourcesRequired", resources);
        return root;
    }

    private void overrideSingleton(ConstructionSiteManager instance) throws Exception {
        Field field = ConstructionSiteManager.class.getDeclaredField("instance");
        field.setAccessible(true);
        field.set(null, instance);
    }
}
