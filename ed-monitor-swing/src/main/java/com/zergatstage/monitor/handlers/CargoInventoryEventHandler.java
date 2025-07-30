package com.zergatstage.monitor.handlers;

import com.zergatstage.monitor.service.managers.CargoInventoryManager;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

@Slf4j
public class CargoInventoryEventHandler implements LogEventHandler{

    private final CargoInventoryManager cargoInventoryManager;

    public CargoInventoryEventHandler() {
        cargoInventoryManager = CargoInventoryManager.getInstance();
    }

    /**
     * Determines whether the handler can process the specified event type.
     *
     * @return string event type.
     */
    @Override
    public String getEventType() {
        return "Cargo";
    }

    @Override
    public boolean isCargoRelated() {
        return true;
    }

    /**
     * Processes the given log event.
     * "event":"Cargo", "Vessel":"Ship", "Count":1232, "Inventory":[]
     * @param event the JSON object representing the log event.
     */
    @Override
    public void handleEvent(JSONObject event) {
        try {
            if (event.has("Inventory")) {
                JSONArray inventory = event.getJSONArray("Inventory");
                cargoInventoryManager.setCargoFromSnapshot(event);
                cargoInventoryManager.notifyListeners();
            }
        } catch (JSONException e) {
            log.error("Error processing Cargo inventory event{} : {}", event, e.getMessage());
        }
    }
}
