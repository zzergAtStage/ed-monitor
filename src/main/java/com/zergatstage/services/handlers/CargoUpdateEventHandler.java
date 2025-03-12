package com.zergatstage.services.handlers;

import com.zergatstage.services.ApplicationContextProvider;
import com.zergatstage.services.ConstructionSiteManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.stereotype.Service;

/**
 * Handles cargo update events and updates the construction site requirements.
 */
@Service
@Log4j2
public class CargoUpdateEventHandler implements LogEventHandler {

    private final ConstructionSiteManager siteManager;

    public CargoUpdateEventHandler(){
        siteManager = ApplicationContextProvider.getApplicationContext().getBean(ConstructionSiteManager.class);
    }

    @Override
    public boolean canHandle(String eventType) {
        return "CargoTransfer".equals(eventType);
    }

    @Override
    public void handleEvent(JSONObject event) {
        String material = null;
        double quantity;
        try {
            // Extract the "Transfers" array from the event JSON object
            JSONArray transfers = event.getJSONArray("Transfers");

            // Assuming the first element in the transfers array holds the relevant cargo details.
            JSONObject transfer = transfers.getJSONObject(0);

            material = transfer.getString("Type");
            quantity = transfer.getDouble("Count");
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        log.info("Trying to update site commodities list...");
        siteManager.updateSitesWithCargo(material, quantity);
    }
}
