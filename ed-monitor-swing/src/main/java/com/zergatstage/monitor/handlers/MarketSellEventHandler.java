package com.zergatstage.monitor.handlers;

import com.zergatstage.domain.dictionary.Commodity;
import com.zergatstage.monitor.factory.DefaultManagerFactory;
import com.zergatstage.monitor.service.CommodityRegistry;
import com.zergatstage.monitor.service.managers.CargoInventoryManager;
import com.zergatstage.tools.CommodityHelper;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONException;
import org.json.JSONObject;

@Slf4j
public class MarketSellEventHandler implements LogEventHandler{

    private final CargoInventoryManager cargoInventoryManager;
    private final CommodityRegistry commodityRegistry;
    public MarketSellEventHandler() {
        cargoInventoryManager = CargoInventoryManager.getInstance();
        commodityRegistry = DefaultManagerFactory.getInstance().getCommodityRegistry();
    }

    /**
     * Determines whether the handler can process the specified event type.
     *
     * @return string event type.
     */
    @Override
    public String getEventType() {
        return "MarketSell";
    }

    /**
     * Processes the given log event.
     *
     * @param event the JSON object representing the log event.
     */
    @Override
    public void handleEvent(JSONObject event) {
        try {
            if (cargoInventoryManager.getShipVariant().isCargoStateKnown()){
                String commodityType = event.getString("Type");
                String commodityKey = event.optString("Type_Localised",
                        CommodityHelper.normalizeSystemName(commodityType));
                int amount = event.getInt("Count");
                long commodityId = commodityRegistry.findCommodityId(commodityType, commodityKey);
                Commodity commodity = commodityRegistry.getCommodityById(commodityId);
                cargoInventoryManager.removeCommodity(commodity, amount * -1);
                cargoInventoryManager.notifyListeners();
            }
        } catch (JSONException e) {
            log.error("Error processing Market Buy event: {}", e.getMessage());
        }
    }
}
