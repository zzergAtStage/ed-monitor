package com.zergatstage.monitor.handlers;

import com.zergatstage.monitor.service.managers.DroneManager;
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

    /**
     * Determines whether the handler can process the specified event type.
     *
     * @return string event type.
     */
    @Override
    public String getEventType() {
        return "LaunchDrone";
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