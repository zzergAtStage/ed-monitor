package com.zergatstage.services;


import org.json.JSONObject;

/**
 * The LogEventHandler interface defines methods for processing log events.
 * Each implementation should indicate which event types it can handle
 * and provide the logic for processing those events.
 */
public interface LogEventHandler {

    /**
     * Determines whether the handler can process the specified event type.
     *
     * @param eventType the type of event (e.g., "LaunchDrone", "ProspectedAsteroid").
     * @return true if this handler can process the event; false otherwise.
     */
    boolean canHandle(String eventType);

    /**
     * Processes the given log event.
     *
     * @param event the JSON object representing the log event.
     */
    void handleEvent(JSONObject event);
}
