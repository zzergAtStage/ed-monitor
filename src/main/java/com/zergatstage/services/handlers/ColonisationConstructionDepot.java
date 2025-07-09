package com.zergatstage.services.handlers;

import com.zergatstage.services.ApplicationContextProvider;
import com.zergatstage.services.ConstructionSiteManager;
import lombok.extern.log4j.Log4j2;
import org.json.JSONObject;
import org.springframework.stereotype.Service;


@Service
@Log4j2
public class ColonisationConstructionDepot implements LogEventHandler {
    private final ConstructionSiteManager siteManager;

    public ColonisationConstructionDepot() {
        siteManager =  ApplicationContextProvider.getApplicationContext().getBean(ConstructionSiteManager.class);
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
    @Override
    public void handleEvent(JSONObject event) {

    }
}
