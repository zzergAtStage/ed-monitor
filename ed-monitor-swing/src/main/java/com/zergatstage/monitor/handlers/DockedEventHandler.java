package com.zergatstage.monitor.handlers;

import com.zergatstage.dto.ConstructionSiteDTO;
import com.zergatstage.dto.ConstructionSiteMapper;
import com.zergatstage.monitor.service.ConstructionSiteManager;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.json.JSONObject;

import java.util.ArrayList;

@Log4j2
public class DockedEventHandler implements LogEventHandler {

    private final ConstructionSiteManager siteManager;

    public DockedEventHandler() {
        this.siteManager = ConstructionSiteManager.getInstance();
    }

    /**
     * Determines whether the handler can process the specified event type.
     *
     * @param eventType the type of event (e.g., "LaunchDrone", "ProspectedAsteroid").
     * @return true if this handler can process the event; false otherwise.
     */
    @Override
    public boolean canHandle(String eventType) {
        return "Docked".equals(eventType);
    }

    /**
     * Processes the given log event.
     *
     * @param event the JSON object representing the log event.
     */
    @SneakyThrows
    @Override
    public void handleEvent(JSONObject event) {
        if (!event.has("StationName") || !event.has("MarketID")) {
            log.warn("There is no any required attributes (StationName, MarketID)");
            return;
        }
        String stationName = event.getString("StationName");
        if (!stationName.contains("Construction Site")) return;
        long marketId = event.getLong("MarketID");
        ConstructionSiteDTO constructionSite = ConstructionSiteMapper
                .INSTANCE.constructionSiteToDto(siteManager.getSite(marketId));

        if (constructionSite == null) {
            constructionSite = ConstructionSiteMapper
                    .INSTANCE.constructionSiteToDto(siteManager.getSiteById(stationName));
        }
        if (constructionSite == null) {
            constructionSite = ConstructionSiteDTO.builder()
                    .marketId(marketId)
                    .requirements(new ArrayList<>())
                    .build();
            constructionSite.setSiteId(stationName);
        }
        siteManager.addSite(constructionSite);
        log.info("Construction site {} added to list", (constructionSite).getSiteId());
    }
}
