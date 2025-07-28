package com.zergatstage.monitor.factory;

import com.zergatstage.monitor.service.AsteroidManager;
import com.zergatstage.monitor.service.CommodityRegistry;
import com.zergatstage.monitor.service.ConstructionSiteManager;
import com.zergatstage.monitor.service.managers.DroneManager;
import com.zergatstage.monitor.service.managers.MarketDataParser;
import lombok.Getter;

@Getter
public class DefaultManagerFactory implements ManagerFactory {

    public static DefaultManagerFactory instance;
    private final ConstructionSiteManager constructionSiteManager;
    private final DroneManager droneManager;
    private final AsteroidManager asteroidManager;
    private final CommodityRegistry commodityRegistry;
    private final MarketDataParser marketDataParser;

    public static DefaultManagerFactory getInstance() {
        if (instance == null) {
            instance = new DefaultManagerFactory();
        }
        return instance;
    }

    private DefaultManagerFactory() {
        this.constructionSiteManager = ConstructionSiteManager.getInstance();
        this.droneManager = new DroneManager();
        this.asteroidManager = new AsteroidManager();
        this.commodityRegistry = CommodityRegistry.getInstance();
        this.marketDataParser = new MarketDataParser();
    }

}
