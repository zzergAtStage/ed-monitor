package com.zergatstage.monitor.service;

import lombok.Getter;

@Getter
public class DroneManager extends BaseManager {

    private boolean droneLaunched;

    public void updateDroneStatus(boolean launched) {
        this.droneLaunched = launched;
        notifyListeners();
    }
}
