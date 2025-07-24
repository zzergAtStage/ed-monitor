package com.zergatstage.services.handlers;

import com.zergatstage.services.ConstructionSiteManager;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.json.JSONObject;


@Log4j2
public class ColonisationConstructionDepot implements LogEventHandler {
    private final ConstructionSiteManager siteManager;

    public ColonisationConstructionDepot() {
        siteManager =  ConstructionSiteManager.getInstance();
    }

    /**
     * Determines whether the handler can process the specified event type.
     *
     * @param eventType the type of event (e.g., "LaunchDrone", "ProspectedAsteroid").
     * @return true if this handler can process the event; false otherwise.
     */
    @Override
    public boolean canHandle(String eventType) {
        return "ColonisationConstructionDepot".equals(eventType);
    }

    /**
     * Processes the given log event.
     *
     * @param event the JSON object representing the log event.
     */
    @SneakyThrows
    @Override
    public void handleEvent(JSONObject event) {
        if (!event.has("MarketID")) {
            log.warn("There is no any required attributes (StationName, MarketID)");
            return;
        }
        long marketId = event.getLong("MarketID");
        log.info("Event: ColonisationConstructionDepot -> MarketId: {}", marketId );
        siteManager.updateSite(marketId, event); //TODO: WIP
    }
}
