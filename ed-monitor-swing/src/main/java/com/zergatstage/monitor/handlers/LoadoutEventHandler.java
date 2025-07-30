package com.zergatstage.monitor.handlers;

import com.zergatstage.monitor.service.managers.CargoInventoryManager;
import org.json.JSONObject;

public class LoadoutEventHandler implements LogEventHandler {

    private final CargoInventoryManager cargoInventoryManager;

    public LoadoutEventHandler() {
        cargoInventoryManager = CargoInventoryManager.getInstance();
    }

    @Override
    public boolean isCargoRelated() {
        return true;
    }

    /**
     * Determines whether the handler can process the specified event type.
     * @return "Loadout"
     */
    @Override
    public String getEventType() {
        return "Loadout";
    }

    /**
     * Processes the given log event.
     *
     * @param event the JSON object representing the log event.
     */
    @Override
    public void handleEvent(JSONObject event) {
        try {
            if (!event.has("Ship") || !event.has("ShipName") || !event.has("CargoCapacity") || !event.has("ShipID")) {
                throw new IllegalArgumentException("Missing required attributes in Loadout event");
            }
            cargoInventoryManager.initShip(event);
        } catch (IllegalArgumentException e) {
            System.err.println("Error processing Loadout event: " + e.getMessage());
            return;
        }
        cargoInventoryManager.notifyListeners();
    }
}

