package com.zergatstage.services;

import com.zergatstage.domain.ConstructionSite;
import com.zergatstage.domain.MaterialRequirement;
import com.zergatstage.domain.makret.Market;
import com.zergatstage.dto.ConstructionSiteDTO;
import com.zergatstage.dto.ConstructionSiteMapper;
import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.*;

/**
 * Manages construction sites and their material requirements.
 */
@Getter
@Setter
@Log4j2

public class ConstructionSiteManager {

    private static ConstructionSiteManager instance;
    private final Map<Long, ConstructionSite> sites = new HashMap<>();
    private final List<ConstructionSiteUpdateListener> listeners = new ArrayList<>();


    private ConstructionSiteManager() {}

    public static ConstructionSiteManager getInstance() {
        if (instance == null) {
            return new ConstructionSiteManager();
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
        for (ConstructionSite site : sites.values()) {
            site.updateDeliveredQuantity(material, deliveredQuantity);
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

    /**
     * Call this method when the construction site data is modified externally,
     * for example by processing a commander log update.
     */
    public void updateSites() {
        notifyListeners();
    }


    //TODO: WIP
    @SneakyThrows
    public void updateSite(long marketId, JSONObject event) {
        ConstructionSite currentSite = sites.get(marketId);

        if (currentSite == null) {
            createConstructionSite(event);
        }
        if (!event.has("ResourcesRequired")) {
            log.warn("No required materials found!");
            return;
        }
        JSONArray commodityRequirements =  event.getJSONArray("ResourcesRequired");
        // Find matching commodity
        List<MaterialRequirement> requirementList = currentSite.getRequirements();

        for (int i = 0; i < commodityRequirements.length(); i++) {
            JSONObject resource = commodityRequirements.getJSONObject(i);
            String name = resource.getString("Name");
            String nameLocalised = resource.getString("Name_Localised");
            int requiredAmount = resource.getInt("RequiredAmount");
            int providedAmount = resource.getInt("ProvidedAmount");

            Optional<MaterialRequirement> first = requirementList.stream()
                    .filter(c -> c.getName().equalsIgnoreCase(name))
                    .findFirst();
            if (first.isPresent()) {
                MaterialRequirement requirement = MaterialRequirement.builder().build();
                requirement.setName(nameLocalised);
                requirement.setMaterialName(requirement.getMaterialName());
                requirement.setRequiredQuantity(requiredAmount);
                requirement.setDeliveredQuantity(providedAmount);

                requirementList.add(requirement);
            } else {
                log.warn("Warning: No matching commodity found for: {}",name);
            }
        }
    }

    private void createConstructionSite(JSONObject event) {
        try {
            ConstructionSite site = ConstructionSite.builder()
                    .marketId(event.getLong("MarketID"))
                    .siteId("STUB_" + event.getLong("MarketID")) //TODO: Generate a unique site ID
                    .requirements(new ArrayList<>())
                    .build();
            sites.put(site.getMarketId(), site);
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
