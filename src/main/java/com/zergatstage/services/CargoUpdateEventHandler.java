package com.zergatstage.services;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Handles cargo update events and updates the construction site requirements.
 */
public class CargoUpdateEventHandler implements LogEventHandler {

    private final ConstructionSiteManager siteManager;

    /**
     * Constructs a CargoUpdateEventHandler.
     *
     * @param siteManager the manager responsible for construction sites.
     */
    public CargoUpdateEventHandler(ConstructionSiteManager siteManager) {
        this.siteManager = siteManager;
    }

    @Override
    public boolean canHandle(String eventType) {
        // For example, "CargoUpdate" could be the type (if provided)
        return "CargoUpdate".equals(eventType);
    }

    @Override
    public void handleEvent(JSONObject event) {
        // Extract cargo materials and quantities from the event.
        // For each cargo update, inform the ConstructionSiteManager.
        // The actual structure will depend on the log format.
        String material = null;
        double quantity;
        try {
            material = event.getString("Material");
            quantity = event.getDouble("Quantity");
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }


        // Delegate the update to the manager.
        siteManager.updateSitesWithCargo(material, quantity);
    }
}
