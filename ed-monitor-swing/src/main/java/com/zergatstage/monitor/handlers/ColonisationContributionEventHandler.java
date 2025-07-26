package com.zergatstage.monitor.handlers;

import lombok.extern.log4j.Log4j2;
import org.json.JSONObject;

@Log4j2
public class ColonisationContributionEventHandler implements LogEventHandler {
    /**
     * Determines whether the handler can process the specified event type.
     *
     * @param eventType the type of event (e.g., "LaunchDrone", "ProspectedAsteroid").
     * @return true if this handler can process the event; false otherwise.
     */
    @Override
    public boolean canHandle(String eventType) {
        return "ColonisationContribution".equals(eventType);
    }

    /**
     * Processes the given log event.
     *
     * @param event the JSON object representing the log event.
     */
    @Override
    public void handleEvent(JSONObject event) {
        log.debug("");
    }
}
