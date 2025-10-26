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

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Manages construction sites and their material requirements.
 */
@Getter
@Setter
@Log4j2
public class ConstructionSiteManager {

    private static volatile ConstructionSiteManager instance;
    private final Map<Long, ConstructionSite> sites = new HashMap<>();
    private final Set<ConstructionSiteUpdateListener> listeners = new HashSet<>();
    private final Set<Long> dirtySites = java.util.Collections.synchronizedSet(new java.util.HashSet<>());
    private final CommodityRegistry commodityRegistry;
    private com.zergatstage.monitor.service.ConstructionSitesHttpService httpService;
    private java.util.concurrent.ScheduledExecutorService scheduler;
    private static final String STUB_PREFIX = "STUB_";

    private ConstructionSiteManager() {
        commodityRegistry = CommodityRegistry.getInstance();
    }

    public static synchronized ConstructionSiteManager getInstance() {
        if (instance == null) {
            synchronized (ConstructionSiteManager.class) {
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
     * @param material          the material name.
     * @param deliveredQuantity the delivered quantity.
     */
    // todo: rework it - this is a GPT a result of hallucination.
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
        if (scheduler != null)
            return;
        scheduler = java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "construction-sync");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(() -> {
            try {
                syncWithServer();
            } catch (Exception ignored) {
            }
        }, 5, 10, java.util.concurrent.TimeUnit.SECONDS);
    }

    private void syncWithServer() {
        if (httpService == null)
            return;
        boolean changed = false;
        try {
            // 1) Flush local dirty sites first so local truth wins
            java.util.List<Long> toFlush;
            synchronized (dirtySites) {
                toFlush = new java.util.ArrayList<>(dirtySites);
            }
            for (Long id : toFlush) {
                ConstructionSite local = sites.get(id);
                if (local == null) {
                    dirtySites.remove(id);
                    continue;
                }
                try {
                    var dto = com.zergatstage.monitor.http.ConstructionSiteDtoMapper.toDto(local);
                    // Preserve non-stub name from server when local has a stub
                    if (isStubSiteId(local.getSiteId(), local.getMarketId())) {
                        try {
                            var latest = httpService.getSite(id);
                            if (latest != null && latest.getSiteId() != null
                                    && !latest.getSiteId().isBlank()
                                    && !isStubSiteId(latest.getSiteId(), id)) {
                                dto.setSiteId(latest.getSiteId());
                                // align version to latest to reduce conflicts
                                dto.setVersion(latest.getVersion());
                            }
                        } catch (Exception ignore) { /* proceed with current dto */ }
                    }
                    var updated = httpService.putSite(dto);
                    sites.put(id, com.zergatstage.monitor.http.ConstructionSiteDtoMapper.fromDto(updated));
                    dirtySites.remove(id);
                    changed = true;
                } catch (com.zergatstage.monitor.service.ConstructionSitesHttpService.VersionConflictException cf) {
                    var latest = cf.getLatest();
                    if (latest != null) {
                        try {
                            var dto = com.zergatstage.monitor.http.ConstructionSiteDtoMapper.toDto(local);
                            dto.setVersion(latest.getVersion());
                            // If local name is stub but server has a real name, keep server name
                            if (isStubSiteId(local.getSiteId(), local.getMarketId())
                                    && latest.getSiteId() != null
                                    && !latest.getSiteId().isBlank()
                                    && !isStubSiteId(latest.getSiteId(), id)) {
                                dto.setSiteId(latest.getSiteId());
                            }
                            var updated = httpService.putSite(dto);
                            sites.put(id, com.zergatstage.monitor.http.ConstructionSiteDtoMapper.fromDto(updated));
                            dirtySites.remove(id);
                            changed = true;
                        } catch (com.zergatstage.monitor.service.ConstructionSitesHttpService.VersionConflictException ignore) {
                            // keep dirty, retry on next cycle
                        }
                    }
                } catch (Exception ignore) {
                    // keep dirty, retry later
                }
            }

            // Pull first: get authoritative snapshot
            var remoteList = httpService.getSites(false);
            Map<Long, com.zergatstage.monitor.http.dto.ConstructionSiteDto> remoteMap = new HashMap<>();
            if (remoteList != null) {
                for (var dto : remoteList) {
                    remoteMap.put(dto.getMarketId(), dto);
                }
            }

            // Reconcile with local cache
            for (var entry : remoteMap.entrySet()) {
                long id = entry.getKey();
                var serverDto = entry.getValue();
                ConstructionSite local = sites.get(id);
                if (local == null) {
                    // Not present locally → adopt server
                    sites.put(id, com.zergatstage.monitor.http.ConstructionSiteDtoMapper.fromDto(serverDto));
                    changed = true;
                    continue;
                }
                Long lv = local.getVersion();
                Long sv = serverDto.getVersion();
                lv = lv == null ? 0L : lv;
                sv = sv == null ? 0L : sv;
                if (lv < sv) {
                    // If local site is dirty, skip replacing to avoid losing unsynced changes
                    if (dirtySites.contains(id)) {
                        continue;
                    }
                    // Server newer → replace local
                    sites.put(id, com.zergatstage.monitor.http.ConstructionSiteDtoMapper.fromDto(serverDto));
                    changed = true;
                } else if (lv > sv) {
                    // Local ahead (e.g., offline change) → resend local via PUT
                    log.debug(" \t...updating server data ConstructionSite");
                    try {
                        var updated = httpService
                                .putSite(com.zergatstage.monitor.http.ConstructionSiteDtoMapper.toDto(local));
                        sites.put(id, com.zergatstage.monitor.http.ConstructionSiteDtoMapper.fromDto(updated));
                        changed = true;
                    } catch (com.zergatstage.monitor.service.ConstructionSitesHttpService.VersionConflictException cf) {
                        log.debug("\t ... updating local data from event {}", cf.getMessage());
                        var latest = cf.getLatest();
                        if (latest != null && local.getLastUpdated().isBefore(latest.getLastUpdated())) {
                            sites.put(id, com.zergatstage.monitor.http.ConstructionSiteDtoMapper.fromDto(latest));
                            changed = true; // TODO: manual merge if needed
                        }
                    }
                }
            }

            // Handle local sites missing on server → push them (insert)
            for (ConstructionSite local : new java.util.ArrayList<>(sites.values())) {
                if (!remoteMap.containsKey(local.getMarketId())) {
                    try {
                        var updated = httpService
                                .putSite(com.zergatstage.monitor.http.ConstructionSiteDtoMapper.toDto(local));
                        sites.put(local.getMarketId(),
                                com.zergatstage.monitor.http.ConstructionSiteDtoMapper.fromDto(updated));
                        changed = true;
                    } catch (com.zergatstage.monitor.service.ConstructionSitesHttpService.VersionConflictException cf) {
                        var latest = cf.getLatest();
                        if (latest != null) {
                            sites.put(local.getMarketId(),
                                    com.zergatstage.monitor.http.ConstructionSiteDtoMapper.fromDto(latest));
                            changed = true; // TODO: manual merge if needed
                        }
                    }
                }
            }

            if (changed)
                notifyListeners();
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

    // TODO: WIP
    @Synchronized
    public void updateSite(long marketId, JSONObject event) {
        ConstructionSite currentSite = sites.get(marketId);

        if (currentSite == null) {
            currentSite = createConstructionSite(event);
        } else {
            promoteSiteNameIfNeeded(currentSite, event);
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
        } catch (JSONException e) {
            log.error("Dropped NPE or something else: {}", e.getMessage());
        }
        // mark as dirty so sync pushes local truth first
        if (currentSite != null) {
            dirtySites.add(currentSite.getMarketId());
        } else {
            dirtySites.add(marketId);
        }
        notifyListeners();
    }

    private ConstructionSite createConstructionSite(JSONObject event) {
        try {
            long marketId = event.getLong("MarketID");
            ConstructionSite site = ConstructionSite.builder()
                    .marketId(marketId)
                    .siteId(resolveSiteName(event).orElseGet(() -> buildStubSiteId(marketId)))
                    .requirements(new CopyOnWriteArrayList<>())
                    .version(99)
                    .lastUpdated(Instant.now())
                    .build();
            promoteSiteNameIfNeeded(site, event);
            sites.put(site.getMarketId(), site);
            return site;
        } catch (JSONException e) {
            throw new IllegalArgumentException("Invalid event data for creating construction site: " + e.getMessage(),
                    e);
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

    private void promoteSiteNameIfNeeded(ConstructionSite site, JSONObject event) {
        if (site == null) return;
        Optional<String> resolvedName = resolveSiteName(event);
        if (resolvedName.isEmpty()) return;
        if (isStubSiteId(site.getSiteId(), site.getMarketId())) {
            site.setSiteId(resolvedName.get());
        }
    }

    private boolean isStubSiteId(String currentSiteId, long marketId) {
        if (currentSiteId == null || currentSiteId.isBlank()) {
            return true;
        }
        if (currentSiteId.startsWith(STUB_PREFIX)) {
            return true;
        }
        return Long.toString(marketId).equals(currentSiteId);
    }

    private Optional<String> resolveSiteName(JSONObject event) {
        String[] candidateKeys = {"StationName", "ConstructionSite", "Body", "Name"};
        for (String key : candidateKeys) {
            String value = event.optString(key, null);
            if (value != null && !value.isBlank()) {
                return Optional.of(value.trim());
            }
        }
        return Optional.empty();
    }

    private String buildStubSiteId(long marketId) {
        return STUB_PREFIX + marketId;
    }
    // Additional methods for removal, lookup, etc.
}
