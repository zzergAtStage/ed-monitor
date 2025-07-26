package com.zergatstage.monitor.handlers;

import com.zergatstage.monitor.service.ConstructionSiteManager;
import lombok.extern.log4j.Log4j2;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Handles cargo update events and updates the construction site requirements.
 */
@Log4j2
public class CargoUpdateEventHandler implements LogEventHandler {

    private final ConstructionSiteManager siteManager;

    public CargoUpdateEventHandler(){
        siteManager = ConstructionSiteManager.getInstance();
    }

    @Override
    public boolean canHandle(String eventType) {
        return "CargoTransfer".equals(eventType);
    }

    @Override
    public void handleEvent(JSONObject event) {
        String material;
        int quantity;
        CargoTransferDirection direction;
        try {
            // Extract the "Transfers" array from the event JSON object sw
            JSONArray transfers = event.getJSONArray("Transfers");

            // Assuming the first element in the transfers array holds the relevant cargo details.
            JSONObject transfer = transfers.getJSONObject(0);

            material = transfer.getString("Type");
            quantity = transfer.getInt("Count");
            direction = transfer.getString("Direction")
                    .equalsIgnoreCase("tocarrier") ? CargoTransferDirection.TO_CARRIER
                                                                : CargoTransferDirection.TO_SHIP;
            //TODO: implement Ship haul update
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        log.info("Trying to update site commodities list...");
        siteManager.updateSitesWithCargo(material, quantity);
    }
}

enum CargoTransferDirection {
    TO_SHIP,
    TO_CARRIER
}
