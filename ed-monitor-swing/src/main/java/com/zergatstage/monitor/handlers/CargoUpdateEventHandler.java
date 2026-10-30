package com.zergatstage.monitor.handlers;

import com.zergatstage.monitor.service.CommodityRegistry;
import com.zergatstage.monitor.service.managers.CargoInventoryManager;
import com.zergatstage.monitor.service.managers.CarrierManager;
import com.zergatstage.monitor.service.ConstructionSiteManager;
import com.zergatstage.tools.CommodityHelper;
import lombok.extern.log4j.Log4j2;
import org.json.JSONArray;
import org.json.JSONObject;

import java.time.Instant;
import java.time.format.DateTimeParseException;

/**
 * Handles the journal {@code CargoTransfer} event, which reports one or more commodity
 * quantities being moved between the commander's ship and a docked Fleet Carrier.
 *
 * <p>Both the ship-side and the carrier-side of every transfer are applied: {@code toShip}
 * credits the ship and debits the carrier; {@code toCarrier} debits the ship and credits the
 * carrier. The carrier side is always applied because the transfer physically happened
 * regardless of whether the ship's cargo state is currently known; only the ship-side mutation
 * is skipped (with a logged warning) when {@link CargoInventoryManager}'s ship cargo state is
 * unknown.</p>
 */
@Log4j2
public class CargoUpdateEventHandler implements LogEventHandler {

    private final CargoInventoryManager cargoInventoryManager;
    private final CarrierManager carrierManager;
    private final CommodityRegistry commodityRegistry;

    /**
     * Creates the handler, resolving the manager singletons it depends on.
     */
    public CargoUpdateEventHandler() {
        ConstructionSiteManager.getInstance();
        cargoInventoryManager = CargoInventoryManager.getInstance();
        carrierManager = CarrierManager.getInstance();
        commodityRegistry = CommodityRegistry.getInstance();
    }

    /**
     * @return {@code true}; this handler moves cargo quantities
     */
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

    /**
     * Processes a {@code CargoTransfer} event, applying every entry in its {@code Transfers}
     * array to both the ship and carrier cargo state.
     *
     * @param event the JSON object representing the log event.
     */
    @Override
    public void handleEvent(JSONObject event) {
        JSONArray transfers = event.optJSONArray("Transfers");
        if (transfers == null || transfers.length() == 0) {
            log.warn("CargoTransfer event has no Transfers array. Skipping event: {}", event);
            return;
        }

        Instant eventTimestamp = parseTimestamp(event.optString("timestamp", null));

        boolean shipCargoKnown = cargoInventoryManager.getShipVariant() != null
                && cargoInventoryManager.getShipVariant().isCargoStateKnown();
        if (!shipCargoKnown) {
            log.warn("Ship cargo state is unknown; skipping ship-side update for this CargoTransfer event "
                    + "(carrier-side quantities will still be applied).");
        }

        for (int i = 0; i < transfers.length(); i++) {
            JSONObject transfer = transfers.optJSONObject(i);
            if (transfer == null) {
                log.warn("CargoTransfer entry at index {} is not a JSON object. Skipping entry.", i);
                continue;
            }

            String rawType = transfer.optString("Type", null);
            if (rawType == null || rawType.isBlank()) {
                log.warn("CargoTransfer entry at index {} is missing Type. Skipping entry.", i);
                continue;
            }
            String materialName = CommodityHelper.normalizeSystemName(rawType);
            long commodityId = commodityRegistry.findCommodityId(materialName, null);
            if (commodityId == -1) {
                log.warn("Unknown commodity '{}' in CargoTransfer entry at index {}. Skipping entry.",
                        materialName, i);
                continue;
            }

            int quantity = transfer.optInt("Count", 0);
            if (quantity <= 0) {
                log.warn("CargoTransfer entry at index {} has a non-positive Count ({}). Skipping entry.",
                        i, quantity);
                continue;
            }

            String direction = transfer.optString("Direction", null);
            boolean toCarrier = "tocarrier".equalsIgnoreCase(direction);
            boolean toShip = "toship".equalsIgnoreCase(direction);
            if (!toCarrier && !toShip) {
                log.warn("CargoTransfer entry at index {} has an unrecognized Direction '{}'. Skipping entry.",
                        i, direction);
                continue;
            }

            int shipDelta = toShip ? quantity : -quantity;
            int carrierDelta = toShip ? -quantity : quantity;

            if (shipCargoKnown) {
                cargoInventoryManager.modifyCargoAmount(commodityId, shipDelta);
            }
            carrierManager.applyTransferDelta(commodityId, carrierDelta, eventTimestamp);
        }
    }

    /**
     * Parses the event's ISO-8601 {@code timestamp} field into an {@link Instant}, falling back
     * to {@link Instant#now()} (with a logged warning) when the field is missing or unparsable.
     *
     * @param rawTimestamp the raw {@code timestamp} field value, or null
     * @return the parsed timestamp, or {@link Instant#now()} on failure
     */
    private Instant parseTimestamp(String rawTimestamp) {
        if (rawTimestamp == null || rawTimestamp.isBlank()) {
            log.warn("CargoTransfer event is missing a timestamp; falling back to Instant.now().");
            return Instant.now();
        }
        try {
            return Instant.parse(rawTimestamp);
        } catch (DateTimeParseException e) {
            log.warn("CargoTransfer event has an unparsable timestamp '{}'; falling back to Instant.now().",
                    rawTimestamp);
            return Instant.now();
        }
    }
}
