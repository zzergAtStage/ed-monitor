package com.zergatstage.monitor.factory;

import com.zergatstage.monitor.service.managers.AsteroidManager;
import com.zergatstage.monitor.service.CommodityRegistry;
import com.zergatstage.monitor.service.ConstructionSiteManager;
import com.zergatstage.monitor.service.managers.DroneManager;
import com.zergatstage.monitor.service.managers.MarketDataParser;
import com.zergatstage.monitor.service.managers.MarketDataUpdateService;
import com.zergatstage.monitor.service.MarketDataHttpService;
import lombok.Getter;

@Getter
public class DefaultManagerFactory implements ManagerFactory {

    public static volatile DefaultManagerFactory instance;
    private final ConstructionSiteManager constructionSiteManager;
    private final DroneManager droneManager;
    private final AsteroidManager asteroidManager;
    private final CommodityRegistry commodityRegistry;
    private final MarketDataParser marketDataParser;
    private final MarketDataUpdateService marketDataUpdateService;

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
        this.marketDataParser = new MarketDataParser();
        this.marketDataUpdateService = new MarketDataUpdateService(
               this.commodityRegistry,
                this.getMarketDataParser());

        String baseUrl = System.getProperty("ed.server.baseUrl", System.getenv().getOrDefault("ED_SERVER_BASE_URL", "http://localhost:8080"));
        try {
            this.marketDataUpdateService.setHttpService(new MarketDataHttpService(baseUrl));
        } catch (IllegalArgumentException ignored) {
            // Invalid base URL provided; proceed without HTTP sync.
        }
    }

}
