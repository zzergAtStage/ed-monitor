package com.zergatstage.monitor.handlers;

import com.zergatstage.monitor.factory.DefaultManagerFactory;
import com.zergatstage.monitor.service.managers.CargoInventoryManager;
import com.zergatstage.monitor.service.CommodityRegistry;
import lombok.extern.log4j.Log4j2;
import org.json.JSONArray;
import org.json.JSONObject;

@Log4j2
public class ColonisationContributionEventHandler implements LogEventHandler {

    private final CargoInventoryManager cargoInventoryManager;
    private final CommodityRegistry commodityRegistry;

    public ColonisationContributionEventHandler() {
        cargoInventoryManager = CargoInventoryManager.getInstance();
        commodityRegistry = DefaultManagerFactory.getInstance().getCommodityRegistry();
    }

    @Override
    public boolean isCargoRelated() {
        return true;
    }

    /**
     * Determines whether the handler can process the specified event type.
     *
     * @return string event type.
     */
    @Override
    public String getEventType() {
        return "ColonisationContribution";
    }

    /**
     * Processes the given log event.
     *
     * @param event the JSON object representing the log event.
     */
    @Override
    public void handleEvent(JSONObject event) {
        if (event.has("Contributions")){
            int amount;

            try {
                JSONArray contributions = event.getJSONArray("Contributions");
                for (int i = 0; i < contributions.length(); i++) {
                    amount = contributions.getJSONObject(i).getInt("Amount");
                    String commodityName = contributions.getJSONObject(i).getString("Name");
                    long commodityId = commodityRegistry.findCommodityId(commodityName,"");
                    int finalAmount = amount * -1;
                    //update the cargo inventory
                    if (cargoInventoryManager.getShipVariant() != null && cargoInventoryManager.getShipVariant().isCargoStateKnown()) {
                        cargoInventoryManager.modifyCargoAmount(commodityId,finalAmount);
                    }
                }
            } catch (Exception e) {
                log.error("Error processing Colonisation Contribution event: {}", e.getMessage());
            }
            cargoInventoryManager.notifyListeners();
        }
    }
}
