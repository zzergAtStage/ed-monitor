package com.zergatstage.services.handlers;

import com.zergatstage.monitor.DisplayConfig;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.swing.*;
import java.awt.*;

/**
 * The AsteroidProspectEventHandler handles "ProspectedAsteroid" events by extracting material information
 * and updating the asteroid prospecting label with color-coded feedback based on Tritium levels.
 */
public class AsteroidProspectEventHandler implements LogEventHandler {

    private final JLabel asteroidLabel;

    /**
     * Constructs an AsteroidProspectEventHandler.
     *
     * @param asteroidLabel the JLabel to update for asteroid prospect events.
     */
    public AsteroidProspectEventHandler(JLabel asteroidLabel) {
        this.asteroidLabel = asteroidLabel;
    }

    @Override
    public boolean canHandle(String eventType) {
        return "ProspectedAsteroid".equals(eventType);
    }

    @Override
    public void handleEvent(JSONObject event) {
        double tritiumProportion = 0.0;
        try {
            if (event.has("Materials")) {
                JSONArray materials = event.getJSONArray("Materials");
                for (int i = 0; i < materials.length(); i++) {
                    JSONObject material = materials.getJSONObject(i);
                    if ("Tritium".equalsIgnoreCase(material.getString("Name"))) {
                        tritiumProportion = material.getDouble("Proportion");
                        break;
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error processing asteroid prospect event: " + e.getMessage());
        }
        final double finalTritium = tritiumProportion;
        SwingUtilities.invokeLater(() -> {
            // Update the asteroid label text with the Tritium proportion
            asteroidLabel.setText(String.format("Asteroid Prospected: Yes (Tritium: %.1f%%)", finalTritium));

            // Update label colors based on Tritium thresholds
            if (finalTritium < DisplayConfig.TRITIUM_THRESHOLD_LOW) {
                asteroidLabel.setBackground(DisplayConfig.COLOR_LOW);
                asteroidLabel.setForeground(Color.WHITE);
            } else if (finalTritium < DisplayConfig.TRITIUM_THRESHOLD_MEDIUM) {
                System.out.println("High tritium found: " + finalTritium);
                asteroidLabel.setBackground(DisplayConfig.COLOR_MEDIUM);
                asteroidLabel.setForeground(Color.BLACK);
            } else if (finalTritium < DisplayConfig.TRITIUM_THRESHOLD_HIGH) {
                asteroidLabel.setBackground(DisplayConfig.COLOR_HIGH);
                asteroidLabel.setForeground(Color.BLACK);
            } else {
                asteroidLabel.setBackground(DisplayConfig.COLOR_VERY_HIGH);
                asteroidLabel.setForeground(Color.WHITE);
            }
            System.out.println("Tritium content: " + finalTritium + "%");
        });
    }
}
