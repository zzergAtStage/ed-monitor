package com.zergatstage.services.handlers;

import com.zergatstage.domain.ConstructionSite;
import com.zergatstage.services.ApplicationContextProvider;
import com.zergatstage.services.ConstructionSiteManager;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.json.JSONObject;
import org.springframework.stereotype.Service;

import java.util.ArrayList;

@Service
@Log4j2
public class DockedEventHandler implements LogEventHandler {

    private final ConstructionSiteManager siteManager;

    public DockedEventHandler() {
        this.siteManager = ApplicationContextProvider.getApplicationContext().getBean(ConstructionSiteManager.class);;
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
        ConstructionSite constructionSite = siteManager.getSites().get(marketId);
        //TODO: WIP
        if (constructionSite == null) {
            constructionSite = siteManager.getSites().values().stream()
                    .filter(s -> s.getSiteId().equals(stationName))
                    .findFirst()
                    .orElse(null);
        }
        if (constructionSite == null) constructionSite = new ConstructionSite(marketId, stationName, new ArrayList<>());
        siteManager.addSite(constructionSite);
        assert constructionSite != null;
        log.info("Construction site {} added to list", constructionSite.getSiteId());
    }
}
