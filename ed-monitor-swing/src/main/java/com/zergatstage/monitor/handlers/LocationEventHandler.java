package com.zergatstage.monitor.handlers;

import com.zergatstage.dto.ConstructionSiteDTO;
import com.zergatstage.dto.ConstructionSiteMapper;
import com.zergatstage.monitor.service.ConstructionSiteManager;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.json.JSONObject;

import java.util.concurrent.CopyOnWriteArrayList;

@Log4j2
public class LocationEventHandler implements LogEventHandler {

    private final ConstructionSiteManager siteManager;

    public LocationEventHandler() {
        this.siteManager = ConstructionSiteManager.getInstance();
    }

    @Override
    public String getEventType() {
        return "Location";
    }

    @SneakyThrows
    @Override
    public void handleEvent(JSONObject event) {
        // We care only when we're docked and can resolve station name + market id
        boolean docked = event.optBoolean("Docked", false);
        if (!docked) return;

        String stationName = event.optString("StationName", null);
        if (stationName == null || stationName.isBlank()) return;

        // Only capture construction site stations
        if (!stationName.toLowerCase().contains("construction site")) return;

        long marketId = event.optLong("MarketID", -1);
        if (marketId <= 0) return;

        // Promote name into ConstructionSiteManager (mirrors DockedEventHandler logic)
        ConstructionSiteDTO constructionSite = ConstructionSiteMapper
                .INSTANCE.constructionSiteToDto(siteManager.getSite(marketId));

        if (constructionSite == null) {
            constructionSite = ConstructionSiteMapper
                    .INSTANCE.constructionSiteToDto(siteManager.getSiteById(stationName));
        }
        if (constructionSite == null) {
            constructionSite = ConstructionSiteDTO.builder()
                    .marketId(marketId)
                    .requirements(new CopyOnWriteArrayList<>())
                    .build();
            constructionSite.setSiteId(stationName);
        }
        if (!stationName.equalsIgnoreCase(constructionSite.getSiteId())) {
            constructionSite.setSiteId(stationName);
        }

        siteManager.addSite(constructionSite);
        log.info("Construction site {} was updated due event {}", constructionSite.getSiteId(), getEventType());
    }
}

