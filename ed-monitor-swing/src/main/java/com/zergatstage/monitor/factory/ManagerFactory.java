package com.zergatstage.monitor.factory;

import com.zergatstage.monitor.service.managers.AsteroidManager;
import com.zergatstage.monitor.service.managers.DroneManager;

public interface ManagerFactory {
    DroneManager getDroneManager();
    AsteroidManager getAsteroidManager();
}
