package com.zergatstage.monitor.handlers;

import org.json.JSONObject;

import com.zergatstage.monitor.service.managers.AsteroidManager;

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
    public String getEventType() {
        return "ProspectedAsteroid";
    }

    @Override
    public void handleEvent(JSONObject event) {
        try {
            if (event.has("Materials")) {
                asteroidManager.updateProspectingLabel(event);
            }
        } catch (Exception e) {
            System.err.println("Error processing asteroid prospect event: " + e.getMessage());
        }

    }
}
