package com.zergatstage.monitor.service.managers;

import com.zergatstage.domain.Ship;
import com.zergatstage.domain.dictionary.CargoItem;
import com.zergatstage.domain.dictionary.Commodity;
import com.zergatstage.monitor.factory.DefaultManagerFactory;
import com.zergatstage.monitor.service.BaseManager;
import com.zergatstage.monitor.service.CommodityRegistry;
import com.zergatstage.monitor.service.JournalLogMonitor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;


/**
 * Manages cargo inventory for a ship. When Journal log initialized,
 * it provides ship characteristics and cargo inventory. So look at {@link JournalLogMonitor} and {@link com.zergatstage.monitor.handlers.LogEventHandler}
 */
@Getter
@Slf4j
public class CargoInventoryManager extends BaseManager {

    private static volatile CargoInventoryManager instance;
    private final CommodityRegistry commodityRegistry;
    private Ship shipVariant;

    private CargoInventoryManager() {
        this.shipVariant = Ship.builder().build();
        this.commodityRegistry = DefaultManagerFactory.getInstance().getCommodityRegistry();
    }

    public static CargoInventoryManager getInstance() {
        if (instance == null) {
            synchronized (CargoInventoryManager.class) {
                if (instance == null) {
                    instance = new CargoInventoryManager();
                }
            }
        }
        return instance;
    }


    /**
     * Initializes or updates the current ship based on the "Loadout" event.
     * This resets the cargo state to unknown.
     *
     * @param event JSON object from a "Loadout" event.
     */
    public void initShip(JSONObject event) {
        try {
            if (shipVariant == null) {
                shipVariant = new Ship();
                //shipVariant.setCommodities(new HashMap<>()); // Initialize with the correct Map type
            }
            shipVariant.setShipId(event.getInt("ShipID"));
            shipVariant.setShip(event.getString("Ship"));
            shipVariant.setShipName(event.getString("ShipName"));
            shipVariant.setCargoCapacity(event.getInt("CargoCapacity"));
            shipVariant.setCargoStateKnown(false); // Reset state on new loadout
            shipVariant.clearCargo();

        } catch (JSONException e) {
            log.error("Failed to initialize ship from Loadout event", e);
            throw new RuntimeException(e);
        }
        notifyListeners();
    }

    /**
     * Modifies the quantity of a specific commodity in the cargo hold.
     * Use positive amounts to add, negative amounts to remove.
     *
     * @param commodityId The canonical ID of the commodity.
     * @param amountDelta The amount to add or remove.
     */
    public void modifyCargoAmount(long commodityId, int amountDelta) {
        if (shipVariant == null || !shipVariant.isCargoStateKnown()) {
            log.warn("Cannot modify cargo, ship state is not yet known.");
            return;
        }

        Map<Long, CargoItem> commodities = shipVariant.getCommodities();
        CargoItem currentItem = commodities.get(commodityId);

        if (currentItem != null) {
            int newCount = currentItem.getCount() + amountDelta;
            if (newCount > 0) {
                currentItem.setCount(newCount);
            } else {
                commodities.remove(commodityId); // Remove if count is zero or less
            }
        } else if (amountDelta > 0) {
            // Item is not in cargo, so add it.
            Commodity commodity = commodityRegistry.getCommodityById(commodityId);
            if (commodity != null) {
                CargoItem newItem = new CargoItem(commodityId, amountDelta, 0);
                commodities.put(commodityId, newItem);
            } else {
                log.error("Attempted to add unknown commodity with ID: {}", commodityId);
            }
        }
        notifyListeners();
    }

    /**
     * Replaces the entire cargo inventory based on a "Cargo" event snapshot.
     * This is the primary method for synchronizing state.
     *
     * @param event JSON object from a "Cargo" event.
     */
    public void setCargoFromSnapshot(JSONObject event) {
        if (shipVariant == null) {
            log.error("Ship is not initialized. Cannot process Cargo event.");
            return;
        }

        shipVariant.clearCargo(); // Always start fresh from a snapshot

        try {
            if (event.has("Inventory")) {
                JSONArray cargoInventory = event.getJSONArray("Inventory");
                for (int i = 0; i < cargoInventory.length(); i++) {
                    JSONObject itemJson = cargoInventory.getJSONObject(i);
                    String systemName = itemJson.getString("Name");
                    String localisedName = itemJson.optString("Name_Localised");

                    long commodityId = commodityRegistry.findCommodityId(systemName, localisedName);

                    if (commodityId != -1) {
                        CargoItem cargoItem = new CargoItem(
                                commodityId,
                                itemJson.getInt("Count"),
                                itemJson.getInt("Stolen")
                        );
                        shipVariant.getCommodities().put(commodityId, cargoItem);
                    } else {
                        log.warn("Could not find commodity ID for: {}. Skipping item.", systemName);
                    }
                }
            }
        } catch (JSONException e) {
            log.error("Failed to parse Cargo event.", e);
            throw new RuntimeException(e);
        }
        shipVariant.setCargoStateKnown(true); // State is now synchronized
        log.info("Cargo state synchronized. Current cargo count: {}", shipVariant.getCurrentCargoCount());
        notifyListeners();
    }

    /**
     * Gets the current count of a commodity in cargo by its ID.
     *
     * @param commodityId The canonical ID of the commodity.
     * @return The number of units in the cargo hold, or 0 if none.
     */
    public int getInCargo(long commodityId) {
        if (shipVariant == null || shipVariant.getCommodities() == null) {
            return 0;
        }
        CargoItem item = shipVariant.getCommodities().get(commodityId);
        return (item != null) ? item.getCount() : 0;
    }

    public void addCommodityToCargo(Commodity commodity, int amount) {

        Map<Long, CargoItem> commoditiesInCargo = shipVariant.getCommodities();

        commoditiesInCargo.compute(commodity.getId(), (_, existingItem) -> {
            if (existingItem == null) {
                // If the commodity does not exist, create a new CargoItem
                return CargoItem.builder()
                        .id(commodity.getId()) // Use commodity.getId() for the new item's ID
                        .count(amount)
                        .stolen(0)
                        .build();
            } else {
                // If it exists, update its count
                existingItem.setCount(existingItem.getCount() + amount);
                return existingItem; // Return the updated existing item
            }
        });
    }

    public void removeCommodity(Commodity commodity, int i) {
        shipVariant.getCommodities().computeIfPresent(commodity.getId(), (_, cargoItem) -> {
            int newCount = cargoItem.getCount() + i;
            if (newCount <= 0) {
                return null; // Remove the item if count is zero or less
            } else {
                cargoItem.setCount(newCount);
                return cargoItem; // Update the count
            }
        });
    }
}
