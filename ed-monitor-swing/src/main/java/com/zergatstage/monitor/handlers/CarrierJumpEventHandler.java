package com.zergatstage.monitor.handlers;

import com.zergatstage.monitor.service.managers.CarrierManager;
import lombok.extern.log4j.Log4j2;
import org.json.JSONObject;

/**
 * Handles the journal {@code CarrierJump} event, emitted when the commander's Fleet Carrier
 * completes a jump to a new system (this event fires both for the carrier owner and for anyone
 * aboard the carrier when it jumps).
 */
@Log4j2
public class CarrierJumpEventHandler implements LogEventHandler {

    private final CarrierManager carrierManager;

    /**
     * Creates the handler, resolving the {@link CarrierManager} singleton.
     */
    public CarrierJumpEventHandler() {
        this.carrierManager = CarrierManager.getInstance();
    }

    /**
     * @return the journal event type this handler processes: {@code "CarrierJump"}
     */
    @Override
    public String getEventType() {
        return "CarrierJump";
    }

    /**
     * Parses {@code StarSystem} out of a {@code CarrierJump} event and forwards it to
     * {@link CarrierManager#updateSystem}.
     *
     * @param event the JSON object representing the {@code CarrierJump} log event
     */
    @Override
    public void handleEvent(JSONObject event) {
        try {
            String starSystem = event.optString("StarSystem", null);
            if (starSystem == null || starSystem.isBlank()) {
                log.warn("CarrierJump event missing StarSystem. Skipping event: {}", event);
                return;
            }
            carrierManager.updateSystem(starSystem);
        } catch (Exception e) {
            log.error("Failed to handle CarrierJump event: {}", event, e);
        }
    }
}
