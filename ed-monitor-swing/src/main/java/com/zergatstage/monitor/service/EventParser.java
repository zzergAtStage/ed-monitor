package com.zergatstage.monitor.service;

import org.json.JSONObject;

/**
 * Interface for parsing raw event strings into JSON objects.
 */
public interface EventParser {
    /**
     * Parses a raw event string into a JSONObject.
     *
     * @param rawEvent the raw event string.
     * @return the parsed JSONObject, or null if parsing fails.
     */
    JSONObject parse(String rawEvent);
}
