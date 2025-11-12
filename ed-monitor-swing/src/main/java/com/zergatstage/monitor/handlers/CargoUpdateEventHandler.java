package com.zergatstage.monitor.handlers;

import com.zergatstage.monitor.service.CommodityRegistry;
import com.zergatstage.monitor.service.managers.CargoInventoryManager;
import com.zergatstage.monitor.service.ConstructionSiteManager;
import com.zergatstage.tools.CommodityHelper;
import lombok.extern.log4j.Log4j2;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Handles cargo update events and updates the construction site requirements.
 */
@Log4j2
public class CargoUpdateEventHandler implements LogEventHandler {

    private final CargoInventoryManager cargoInventoryManager;
    private final CommodityRegistry commodityRegistry;
    public CargoUpdateEventHandler(){
        ConstructionSiteManager.getInstance();
        cargoInventoryManager = CargoInventoryManager.getInstance();
        commodityRegistry = CommodityRegistry.getInstance();
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
        return "CargoTransfer";
    }

    @Override
    public void handleEvent(JSONObject event) {


        if (!cargoInventoryManager.getShipVariant().isCargoStateKnown()) {
            log.warn("Cargo state is unknown, cannot handle CargoTransfer event.");
            return;
        }
        String material;
        long commodityId;
        int quantity;
        CargoTransferDirection direction;
        try {
            // Extract the "Transfers" array from the event JSON object sw
            JSONArray transfers = event.getJSONArray("Transfers");

            // Assuming the first element in the transfers array holds the relevant cargo details.
            JSONObject transfer = transfers.getJSONObject(0);

            material = transfer.getString("Type");
            String materialName = CommodityHelper.normalizeSystemName(material);
            commodityId = commodityRegistry.findCommodityId(materialName, null);
            quantity = transfer.getInt("Count");
            direction = transfer.getString("Direction")
                    .equalsIgnoreCase("tocarrier") ? CargoTransferDirection.TO_CARRIER
                                                                : CargoTransferDirection.TO_SHIP;
            //cargoInventoryManager.modifyCargoAmount();
            log.info("Trying to update site commodities list...");
            if (direction == CargoTransferDirection.TO_SHIP) {
                cargoInventoryManager.modifyCargoAmount(commodityId, quantity);
            } else {
                cargoInventoryManager.modifyCargoAmount(commodityId, -quantity);
            }
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }

    }
}

enum CargoTransferDirection {
    TO_SHIP,
    TO_CARRIER
}
