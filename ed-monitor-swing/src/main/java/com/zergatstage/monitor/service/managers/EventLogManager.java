package com.zergatstage.monitor.service.managers;

import com.zergatstage.monitor.service.BaseManager;
import org.json.JSONException;
import org.json.JSONObject;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Captures every journal event seen by {@link com.zergatstage.monitor.service.JournalLogMonitor},
 * independent of whether a {@link com.zergatstage.monitor.handlers.LogEventHandler} is registered
 * for it. Backs the raw event viewer UI.
 */
public class EventLogManager extends BaseManager {

    public record JournalEventRecord(Instant timestamp, String eventType, String rawJson, String prettyJson) {
    }

    private static final int MAX_EVENTS = 5000;

    private final Deque<JournalEventRecord> events = new ArrayDeque<>();
    private final Set<String> knownEventTypes = new LinkedHashSet<>();

    public void record(JSONObject json) {
        String eventType = json.optString("event", "Unknown");
        Instant timestamp = parseTimestamp(json.optString("timestamp", null));
        String rawJson = json.toString();
        String prettyJson;
        try {
            prettyJson = json.toString(2);
        } catch (JSONException e) {
            prettyJson = rawJson;
        }
        JournalEventRecord record = new JournalEventRecord(timestamp, eventType, rawJson, prettyJson);

        synchronized (events) {
            events.addLast(record);
            while (events.size() > MAX_EVENTS) {
                events.removeFirst();
            }
            knownEventTypes.add(eventType);
        }
        notifyListeners();
    }

    public List<JournalEventRecord> snapshot() {
        synchronized (events) {
            return new ArrayList<>(events);
        }
    }

    public List<String> knownEventTypesSorted() {
        synchronized (events) {
            List<String> types = new ArrayList<>(knownEventTypes);
            Collections.sort(types);
            return types;
        }
    }

    private static Instant parseTimestamp(String raw) {
        if (raw == null || raw.isEmpty()) {
            return Instant.now();
        }
        try {
            return Instant.parse(raw);
        } catch (DateTimeParseException e) {
            return Instant.now();
        }
    }
}
