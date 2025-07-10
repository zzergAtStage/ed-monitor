package com.zergatstage.services.handlers;

import com.zergatstage.domain.ConstructionSite;
import com.zergatstage.services.ApplicationContextProvider;
import com.zergatstage.services.ConstructionSiteManager;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.stereotype.Service;

@Service
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
    @Override
    public void handleEvent(JSONObject event) {
        try {
            String stationName = event.getString("StationName");
            long marketId = event.getLong("MarketID");
            ConstructionSite constructionSite = siteManager.getSites().get(marketId);
            //TODO: WIP
            if (constructionSite == null) {
                constructionSite = siteManager.getSites().values().stream()
                        .filter(s -> s.getSiteId().equals(stationName))
                        .findFirst()
                        .orElse(null);

            }
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }
}
