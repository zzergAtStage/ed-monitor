package com.zergatstage.monitor.factory;

import lombok.extern.slf4j.Slf4j;

import com.zergatstage.monitor.service.managers.AsteroidManager;
import com.zergatstage.monitor.service.CommodityRegistry;
import com.zergatstage.monitor.service.ConstructionSiteManager;
import com.zergatstage.monitor.service.managers.DroneManager;
import com.zergatstage.monitor.service.managers.EventLogManager;
import com.zergatstage.monitor.service.managers.MarketDataParser;
import com.zergatstage.monitor.service.managers.MarketDataUpdateService;
import com.zergatstage.monitor.service.MarketDataHttpService;
import lombok.Getter;

@Getter
@Slf4j
public class DefaultManagerFactory implements ManagerFactory {

    public static volatile DefaultManagerFactory instance;
    private final ConstructionSiteManager constructionSiteManager;
    private final DroneManager droneManager;
    private final AsteroidManager asteroidManager;
    private final CommodityRegistry commodityRegistry;
    private final MarketDataParser marketDataParser;
    private final MarketDataUpdateService marketDataUpdateService;
    private final EventLogManager eventLogManager;
    private volatile String currentBaseUrl;

    public static  DefaultManagerFactory getInstance() {
        if (instance == null) {
            synchronized (DefaultManagerFactory.class) {
                if (instance == null) {
                    // Double-checked locking to ensure thread safety
                    instance = new DefaultManagerFactory();
                }
            }
        }
        return instance;
    }

    private DefaultManagerFactory() {
        this.constructionSiteManager = ConstructionSiteManager.getInstance();
        this.droneManager = new DroneManager();
        this.asteroidManager = new AsteroidManager();
        this.commodityRegistry = CommodityRegistry.getInstance();
        this.eventLogManager = new EventLogManager();
        this.marketDataParser = new MarketDataParser();
        this.marketDataUpdateService = new MarketDataUpdateService(
               this.commodityRegistry,
                this.getMarketDataParser());

        this.currentBaseUrl = System.getProperty("ed.server.baseUrl", System.getenv().getOrDefault("ED_SERVER_BASE_URL", "http://localhost:50080"));
        try {
            this.marketDataUpdateService.setHttpService(new MarketDataHttpService(currentBaseUrl));
            // Hook up construction site HTTP sync (every ~10s inside manager)
            this.constructionSiteManager.setHttpService(new com.zergatstage.monitor.service.ConstructionSitesHttpService(currentBaseUrl));
            // Initial pull from server to populate cache after restart (best-effort)
            this.marketDataUpdateService.refreshFromServer();
        } catch (IllegalArgumentException ignored) {
            // Invalid base URL provided; proceed without HTTP sync.
        }
    }

    public void updateBaseUrl(String baseUrl) {
        try {
            this.marketDataUpdateService.setHttpService(new MarketDataHttpService(baseUrl));
            this.constructionSiteManager.setHttpService(
                    new com.zergatstage.monitor.service.ConstructionSitesHttpService(baseUrl));
            this.currentBaseUrl = baseUrl;
            log.info("HTTP services switched to {}", baseUrl);
        } catch (IllegalArgumentException e) {
            log.warn("Ignoring invalid base URL from server: {}", baseUrl);
        }
    }

    public String getCurrentBaseUrl() {
        return currentBaseUrl;
    }

}
