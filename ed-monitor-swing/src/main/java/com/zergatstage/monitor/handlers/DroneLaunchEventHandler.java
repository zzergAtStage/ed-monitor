package com.zergatstage.monitor.handlers;

import com.zergatstage.monitor.service.DroneManager;
import org.json.JSONObject;

/**
 * The DroneLaunchEventHandler handles "LaunchDrone" events by updating UI labels.
 * Decoupled from any specific frame implementation.
 */
public class DroneLaunchEventHandler implements LogEventHandler {


    private final DroneManager droneManager;

    public DroneLaunchEventHandler(DroneManager droneManager) {
        this.droneManager = droneManager;
    }

    @Override
    public boolean canHandle(String eventType) {
        return "LaunchDrone".equals(eventType);
    }

    @Override
    public void handleEvent(JSONObject event) {
        try {
            String droneType = event.getString("Type");
            boolean isProspector = "Prospector".equals(droneType);
            droneManager.updateDroneStatus(isProspector);

        } catch (Exception e) {
            System.err.println("Error processing drone launch event: " + e.getMessage());
        }
    }
}