package com.zergatstage.monitor.service;

import com.zergatstage.monitor.config.LogMonitorConfig;
import com.zergatstage.monitor.service.managers.CargoInventoryManager;
import com.zergatstage.monitor.service.readers.RewriteFileReadStrategy;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONException;
import org.json.JSONObject;

import java.nio.file.Path;

/**
 * Watches {@code Cargo.json}, the file Elite Dangerous rewrites in place next to the journal
 * whenever cargo changes. In real gameplay the journal's own "Cargo" event only ever carries a
 * total {@code Count} - the per-commodity inventory is written exclusively to this file - so it
 * is the actual source of truth for {@link CargoInventoryManager}, the same way
 * {@link MarketDataIOService} watches {@code Market.json}.
 */
@Slf4j
public class CargoDataIOService {

    private final GenericFileMonitor fileMonitor;
    private final CargoInventoryManager cargoInventoryManager;

    public CargoDataIOService(CargoInventoryManager cargoInventoryManager) {
        this(cargoInventoryManager, LogMonitorConfig.logDirectory().resolve("Cargo.json"));
    }

    CargoDataIOService(CargoInventoryManager cargoInventoryManager, Path cargoFile) {
        this.cargoInventoryManager = cargoInventoryManager;
        this.fileMonitor = new GenericFileMonitor(cargoFile, new RewriteFileReadStrategy(), this::onCargoFileContent);
    }

    public void start() {
        fileMonitor.start();
    }

    public void stop() {
        fileMonitor.stop();
    }

    private void onCargoFileContent(String content) {
        try {
            JSONObject snapshot = new JSONObject(content);
            if (snapshot.has("Inventory")) {
                cargoInventoryManager.setCargoFromSnapshot(snapshot);
                cargoInventoryManager.notifyListeners();
            }
        } catch (JSONException e) {
            log.warn("Failed to parse Cargo.json content: {}", e.getMessage());
        }
    }
}
