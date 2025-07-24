package com.zergatstage.services.handlers;

import com.zergatstage.monitor.EliteLogMonitorFrame;
import org.json.JSONObject;

import javax.swing.*;

/**
 * The DroneLaunchEventHandler handles events of type "LaunchDrone" by updating the drone launch label.
 */
public class DroneLaunchEventHandler implements LogEventHandler {

    private final JLabel droneLabel;
    private final EliteLogMonitorFrame frame;

    /**
     * Constructs a DroneLaunchEventHandler.
     *
     * @param droneLabel the JLabel to update for drone launch events.
     * @param frame the main application frame (used for additional UI updates if necessary).
     */
    public DroneLaunchEventHandler(JLabel droneLabel, EliteLogMonitorFrame frame) {
        this.droneLabel = droneLabel;
        this.frame = frame;
    }

    @Override
    public boolean canHandle(String eventType) {
        return "LaunchDrone".equals(eventType);
    }

    @Override
    public void handleEvent(JSONObject event) {
        try {
            // Extract drone type from event
            String droneType = event.getString("Type");
            SwingUtilities.invokeLater(() -> {
                // Update the drone label text
                droneLabel.setText("Drone Launched: Yes (" + droneType + ")");
                // If the drone type is "Prospector", reset the asteroid label via the main frame
                if ("Prospector".equals(droneType)) {
                    frame.resetAsteroidLabel();
                }
            });
        } catch (Exception e) {
            System.err.println("Error processing drone launch event: " + e.getMessage());
        }
    }
}
