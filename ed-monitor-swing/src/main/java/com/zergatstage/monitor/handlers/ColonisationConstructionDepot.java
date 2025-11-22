package com.zergatstage.monitor.handlers;

import com.zergatstage.monitor.service.ConstructionSiteManager;
import lombok.extern.log4j.Log4j2;
import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.CRC32;


@Log4j2
public class ColonisationConstructionDepot implements LogEventHandler {
    private final ConstructionSiteManager siteManager;
    private final Map<Long, Long> lastFingerprints = new ConcurrentHashMap<>();
    public ColonisationConstructionDepot() {
        siteManager =  ConstructionSiteManager.getInstance();
    }

    /**
     * Determines whether the handler can process the specified event type.
     *
     * @return string event type.
     */
    @Override
    public String getEventType() {
        return "ColonisationConstructionDepot";
    }

    /**
     * Processes the given log event.
     *
     * @param event the JSON object representing the log event.
     */

    @Override
    public void handleEvent(JSONObject event) {
        try {
            if (!event.has("MarketID")) {
                log.warn("There is no any required attributes (StationName, MarketID)");
                return;
            }
            long marketId = event.getLong("MarketID");
            long currFingerprint = computeFingerprint(event);
            if (currFingerprint == lastFingerprints.getOrDefault(marketId, -1L)) {
                log.trace("\tskip line processing due the non-unique fingerprint ");
                return;
            }

            log.debug("Event: ColonisationConstructionDepot -> MarketId: {}, fingerprint: {}", marketId, currFingerprint);
            siteManager.updateSite(marketId, event);
            lastFingerprints.put(marketId, currFingerprint);
        }catch (JSONException e) {
            log.error("An error with parsing JSON at {}: {}",event, e.getMessage());
        }
    }

    private long computeFingerprint(JSONObject event) throws JSONException {
        CRC32 crc = new CRC32();
        crc.update(event.getJSONArray("ResourcesRequired").toString().getBytes(StandardCharsets.UTF_8));
        crc.update(Long.toString(event.getLong("MarketID")).getBytes(StandardCharsets.UTF_8));
        return crc.getValue();
    }
}
