package com.zergatstage.monitor.handlers;

import com.zergatstage.monitor.service.managers.CarrierManager;
import lombok.extern.log4j.Log4j2;
import org.json.JSONObject;

/**
 * Handles the journal {@code CarrierLocation} event, emitted to report the current system of a
 * Fleet Carrier the commander owns or is tracking (e.g. after logging in, or periodically while
 * the carrier is in transit).
 */
@Log4j2
public class CarrierLocationEventHandler implements LogEventHandler {

    private final CarrierManager carrierManager;

    /**
     * Creates the handler, resolving the {@link CarrierManager} singleton.
     */
    public CarrierLocationEventHandler() {
        this.carrierManager = CarrierManager.getInstance();
    }

    /**
     * @return the journal event type this handler processes: {@code "CarrierLocation"}
     */
    @Override
    public String getEventType() {
        return "CarrierLocation";
    }

    /**
     * Parses {@code StarSystem} out of a {@code CarrierLocation} event and forwards it to
     * {@link CarrierManager#updateSystem}.
     *
     * @param event the JSON object representing the {@code CarrierLocation} log event
     */
    @Override
    public void handleEvent(JSONObject event) {
        try {
            String starSystem = event.optString("StarSystem", null);
            if (starSystem == null || starSystem.isBlank()) {
                log.warn("CarrierLocation event missing StarSystem. Skipping event: {}", event);
                return;
            }
            carrierManager.updateSystem(starSystem);
        } catch (Exception e) {
            log.error("Failed to handle CarrierLocation event: {}", event, e);
        }
    }
}
