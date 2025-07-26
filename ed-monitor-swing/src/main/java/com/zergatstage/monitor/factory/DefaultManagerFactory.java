package com.zergatstage.monitor.factory;

import com.zergatstage.monitor.service.AsteroidManager;
import com.zergatstage.monitor.service.ConstructionSiteManager;
import com.zergatstage.monitor.service.DroneManager;
import lombok.Getter;

@Getter
public class DefaultManagerFactory implements ManagerFactory {

    public static DefaultManagerFactory instance;
    private final ConstructionSiteManager constructionSiteManager;
    private final DroneManager droneManager;
    private final AsteroidManager asteroidManager;

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
    }

}
