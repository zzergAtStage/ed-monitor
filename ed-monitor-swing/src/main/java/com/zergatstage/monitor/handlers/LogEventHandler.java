package com.zergatstage.monitor.handlers;


import org.json.JSONObject;

/**
 * The LogEventHandler interface defines methods for processing log events.
 * Each implementation should indicate which event types it can handle
 * and provide the logic for processing those events.
 */
public interface LogEventHandler {

    /**
     * Determines whether the handler can process the specified event type.
     * @return string event type.
     */
    String getEventType();

    /**
     * Processes the given log event.
     *
     * @param event the JSON object representing the log event.
     */
    void handleEvent(JSONObject event);
}
