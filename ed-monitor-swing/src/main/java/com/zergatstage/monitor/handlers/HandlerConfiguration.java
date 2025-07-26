package com.zergatstage.monitor.handlers;

import com.zergatstage.monitor.factory.DefaultManagerFactory;
import com.zergatstage.monitor.service.DroneManager;

import javax.swing.JLabel;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class HandlerConfiguration {
    /**
     * Builds the list of LogEventHandlers to use in the app.
     * You can extend this method (or read from a file, DI container, etc.)
     * to add new handlers without touching the UI.
     */
    public static List<LogEventHandler> getLogEventHandlers() {
        List<LogEventHandler> handlers = new ArrayList<>();
        handlers.add(new DroneLaunchEventHandler(DefaultManagerFactory.getInstance().getDroneManager()));
        handlers.add(new AsteroidProspectEventHandler(DefaultManagerFactory.instance.getAsteroidManager()));
        handlers.add(new CargoUpdateEventHandler());
        handlers.add(new ColonisationConstructionDepot());
        handlers.add(new DockedEventHandler());

        // if you need to load handlers from plugins or external modules:
        // handlers.addAll(PluginLoader.load(LogEventHandler.class));
        return handlers;
    }
}
