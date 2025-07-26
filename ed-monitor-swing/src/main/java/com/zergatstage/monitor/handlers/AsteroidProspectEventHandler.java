package com.zergatstage.monitor.handlers;

import com.zergatstage.monitor.config.DisplayConfig;
import com.zergatstage.monitor.service.AsteroidManager;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.swing.*;
import java.awt.*;

/**
 * The AsteroidProspectEventHandler handles "ProspectedAsteroid" events by extracting material information
 * and updating the asteroid prospecting label with color-coded feedback based on Tritium levels.
 */
public class AsteroidProspectEventHandler implements LogEventHandler {

    private final AsteroidManager asteroidManager;


    public AsteroidProspectEventHandler(AsteroidManager asteroidManager) {
        this.asteroidManager = asteroidManager;
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
        asteroidManager.updateProspectingLabel(event, finalTritium);
    }
}
