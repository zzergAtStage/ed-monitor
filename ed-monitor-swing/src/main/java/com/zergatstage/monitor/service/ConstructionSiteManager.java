package com.zergatstage.monitor.service;

import com.zergatstage.domain.ConstructionSite;
import com.zergatstage.domain.MaterialRequirement;
import com.zergatstage.dto.ConstructionSiteDTO;
import com.zergatstage.dto.ConstructionSiteMapper;
import com.zergatstage.tools.CommodityHelper;
import lombok.Getter;
import lombok.Setter;
import lombok.Synchronized;
import lombok.extern.log4j.Log4j2;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Manages construction sites and their material requirements.
 */
@Getter
@Setter
@Log4j2
public class ConstructionSiteManager {

    private static volatile  ConstructionSiteManager instance;
    private final Map<Long, ConstructionSite> sites = new HashMap<>();
    private final Set<ConstructionSiteUpdateListener> listeners = new HashSet<>();
    private final CommodityRegistry commodityRegistry;
    private com.zergatstage.monitor.service.ConstructionSitesHttpService httpService;
    private java.util.concurrent.ScheduledExecutorService scheduler;

    private ConstructionSiteManager() {
        commodityRegistry = CommodityRegistry.getInstance();
    }

    public static synchronized ConstructionSiteManager getInstance() {
        if (instance == null) {
            synchronized(ConstructionSiteManager.class) {
                if (instance == null) {
                    instance = new ConstructionSiteManager();
                }
            }
        }
        return instance;
    }

    /**
     * Adds a new construction site.
     *
     * @param site the construction site to add.
     */
    public void addSite(ConstructionSiteDTO site) {
        ConstructionSite savedSite = ConstructionSiteMapper.INSTANCE.constructionSiteDtoToEntity(site);
        sites.put(savedSite.getMarketId(), savedSite);
        notifyListeners();
    }

    /**
     * Updates all construction sites with the delivered cargo.
     *
     * @param material the material name.
     * @param deliveredQuantity the delivered quantity.
     */
    //todo: rework it - this is a GPT a result of hallucination.
    public void updateSitesWithCargo(String material, int deliveredQuantity) {
        long commodityId = commodityRegistry.findCommodityId(material, null);
        for (ConstructionSite site : sites.values()) {
            site.updateDeliveredQuantity(commodityId, deliveredQuantity);
            notifyListeners();
        }
    }

    /**
     * Registers a listener to be notified when construction site data changes.
     *
     * @param listener the listener to register.
     */
    public void addListener(ConstructionSiteUpdateListener listener) {
        listeners.add(listener);
    }

    /**
     * Notifies all registered listeners about the data update.
     */
    private void notifyListeners() {
        for (ConstructionSiteUpdateListener listener : listeners) {
            listener.onConstructionSiteUpdated();
        }
    }

    public void setHttpService(com.zergatstage.monitor.service.ConstructionSitesHttpService httpService) {
        this.httpService = httpService;
        startAutoSync();
    }

    private void startAutoSync() {
        if (scheduler != null) return;
        scheduler = java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "construction-sync");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(() -> {
            try { syncWithServer(); } catch (Exception ignored) {}
        }, 5, 10, java.util.concurrent.TimeUnit.SECONDS);
    }

    private void syncWithServer() {
        if (httpService == null) return;
        try {
            // Push local state (best-effort)
            var dtos = sites.values().stream()
                    .map(com.zergatstage.monitor.http.ConstructionSiteDtoMapper::toDto)
                    .toList();
            if (!dtos.isEmpty()) {
                httpService.postSites(dtos);
            }
            // Pull from server and merge, keeping uniqueness by marketId
            var remote = httpService.getSites(false);
            if (remote != null) {
                for (var dto : remote) {
                    ConstructionSite site = com.zergatstage.monitor.http.ConstructionSiteDtoMapper.fromDto(dto);
                    sites.put(site.getMarketId(), site);
                }
                notifyListeners();
            }
        } catch (Exception e) {
            // swallow periodic sync errors to avoid UI noise
        }
    }

    /**
     * Call this method when the construction site data is modified externally,
     * for example by processing a commander log update.
     */
    public void updateSites() {
        notifyListeners();
    }


    //TODO: WIP
    @Synchronized
    public void updateSite(long marketId, JSONObject event) {
        ConstructionSite currentSite = sites.get(marketId);

        if (currentSite == null) {
            currentSite = createConstructionSite(event);
        }
        if (!event.has("ResourcesRequired")) {
            log.warn("No required materials found!");
            return;
        }
        try {
            JSONArray commodityRequirements = event.getJSONArray("ResourcesRequired");
            // Find matching commodity
            List<MaterialRequirement> requirementList = currentSite.getRequirements();

            for (int i = 0; i < commodityRequirements.length(); i++) {
                JSONObject resource = commodityRequirements.getJSONObject(i);
                String name = resource.getString("Name");
                String nameLocalised = resource.optString("Name_Localised", null);
                String commodityKey = CommodityHelper.normalizeSystemName(name);
                long commodityId = commodityRegistry.findCommodityId(commodityKey, nameLocalised);
                int requiredAmount = resource.getInt("RequiredAmount");
                int providedAmount = resource.getInt("ProvidedAmount");
                Optional<MaterialRequirement> first = Optional.empty();
                if (!requirementList.isEmpty()) {
                    first = requirementList.stream()
                            .filter(c -> c.getCommodity().getId() == commodityId)
                            .findFirst();
                }
                if (first.isEmpty()) {
                    MaterialRequirement requirement = MaterialRequirement.builder().build();
                    requirement.setCommodity(commodityRegistry.getCommodityById(commodityId));

                    requirement.setRequiredQuantity(requiredAmount);
                    requirement.setDeliveredQuantity(providedAmount);

                    requirementList.add(requirement);
                } else {
                    first.get().setRequiredQuantity(requiredAmount);
                    first.get().setDeliveredQuantity(providedAmount);
                }
            }
        }catch (JSONException e) {
            log.error("Dropped NPE or something else: {}",e.getMessage());
        }
        notifyListeners();
    }

    private ConstructionSite createConstructionSite(JSONObject event) {
        try {
            ConstructionSite site = ConstructionSite.builder()
                    .marketId(event.getLong("MarketID"))
                    .siteId("STUB_" + event.getLong("MarketID")) //TODO: Generate a unique site ID
                    .requirements(new CopyOnWriteArrayList<>())
                    .build();
            sites.put(site.getMarketId(), site);
            return site;
        } catch (JSONException e) {
            throw new IllegalArgumentException("Invalid event data for creating construction site: " + e.getMessage(), e);
        }
    }

    public ConstructionSite getSite(long marketId) {
        return sites.get(marketId);
    }

    public ConstructionSite getSiteById(String siteId) {
        return sites.values().stream()
                .filter(site -> site.getSiteId().equals(siteId))
                .findFirst()
                .orElse(null);
    }
    // Additional methods for removal, lookup, etc.
}
