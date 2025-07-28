package com.zergatstage.monitor.handlers;

import com.zergatstage.monitor.factory.DefaultManagerFactory;

import java.util.*;

public class HandlerConfiguration {
    /**
     * Builds the list of LogEventHandlers to use in the app.
     * You can extend this method (or read from a file, DI container, etc.)
     * to add new handlers without touching the UI.
     */
    public static Map<String, LogEventHandler> getLogEventHandlers() {
        Map<String, LogEventHandler> handlerMap = new HashMap<>();
        handlerMap.put("LaunchDrone",new DroneLaunchEventHandler(DefaultManagerFactory.getInstance().getDroneManager()));
        handlerMap.put("ProspectedAsteroid",new AsteroidProspectEventHandler(DefaultManagerFactory.instance.getAsteroidManager()));

        ServiceLoader<LogEventHandler> loader = ServiceLoader.load(LogEventHandler.class);
        for (LogEventHandler handler : loader) {
            String type = handler.getEventType();
            if (handlerMap.containsKey(type)) {
                System.err.println("Warning: Duplicate handler for event type: " + type);
            }
            handlerMap.put(type, handler);
        }
        return handlerMap;
    }
}
