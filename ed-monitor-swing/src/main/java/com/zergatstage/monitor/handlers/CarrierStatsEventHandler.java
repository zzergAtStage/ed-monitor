package com.zergatstage.monitor.handlers;

import com.zergatstage.monitor.service.managers.CarrierManager;
import lombok.extern.log4j.Log4j2;
import org.json.JSONObject;

/**
 * Handles the journal {@code CarrierStats} event, which reports the commander's Fleet Carrier
 * identity and cargo capacity/usage statistics.
 *
 * <p>Elite Dangerous emits this event periodically while docked at, or owning, a Fleet Carrier,
 * and it is the authoritative source for the carrier's total capacity and free space.</p>
 */
@Log4j2
public class CarrierStatsEventHandler implements LogEventHandler {

    private final CarrierManager carrierManager;

    /**
     * Creates the handler, resolving the {@link CarrierManager} singleton.
     */
    public CarrierStatsEventHandler() {
        this.carrierManager = CarrierManager.getInstance();
    }

    /**
     * @return the journal event type this handler processes: {@code "CarrierStats"}
     */
    @Override
    public String getEventType() {
        return "CarrierStats";
    }

    /**
     * Parses carrier identity and cargo stats out of a {@code CarrierStats} event and forwards
     * them to {@link CarrierManager#updateStats}.
     *
     * <p>NOTE: {@code SpaceUsage.FreeSpace} is the carrier's TRUE free space as reported by the
     * game — it already accounts for Crew, ShipPacks, ModulePacks and CargoSpaceReserved
     * allocations, not just {@code TotalCapacity - Cargo}. The reported value is used directly
     * here; recomputing it via {@code TotalCapacity - Cargo} would silently overstate free space
     * by the size of those other allocations. This is the obvious-looking mistake to avoid.</p>
     *
     * @param event the JSON object representing the {@code CarrierStats} log event
     */
    @Override
    public void handleEvent(JSONObject event) {
        try {
            long carrierId = event.optLong("CarrierID", -1);
            if (carrierId <= 0) {
                log.warn("CarrierStats event missing a valid CarrierID. Skipping event: {}", event);
                return;
            }
            String callsign = event.optString("Callsign", null);
            String name = event.optString("Name", null);

            JSONObject spaceUsage = event.optJSONObject("SpaceUsage");
            int totalCapacity = spaceUsage != null ? spaceUsage.optInt("TotalCapacity", 0) : 0;
            int cargoUsed = spaceUsage != null ? spaceUsage.optInt("Cargo", 0) : 0;
            // FreeSpace already accounts for Crew/ShipPacks/ModulePacks/CargoSpaceReserved -
            // use it as-is, do NOT recompute as (TotalCapacity - Cargo).
            int freeSpace = spaceUsage != null ? spaceUsage.optInt("FreeSpace", 0) : 0;

            carrierManager.updateStats(carrierId, callsign, name, totalCapacity, cargoUsed, freeSpace);
        } catch (Exception e) {
            log.error("Failed to handle CarrierStats event: {}", event, e);
        }
    }
}
