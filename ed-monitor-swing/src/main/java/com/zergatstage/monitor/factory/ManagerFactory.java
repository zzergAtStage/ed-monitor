package com.zergatstage.monitor.factory;

import com.zergatstage.monitor.service.AsteroidManager;
import com.zergatstage.monitor.service.ConstructionSiteManager;
import com.zergatstage.monitor.service.managers.DroneManager;

public interface ManagerFactory {
    ConstructionSiteManager getConstructionSiteManager();
    DroneManager getDroneManager();
    AsteroidManager getAsteroidManager();
}
