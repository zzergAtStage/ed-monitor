package com.zergatstage.services;

import com.zergatstage.domain.ConstructionSite;
import com.zergatstage.domain.MaterialRequirement;
import com.zergatstage.domain.dictionary.Commodity;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Manages construction sites and their material requirements.
 */
@Getter
@Setter
@Log4j2
@Component
@RequiredArgsConstructor
public class ConstructionSiteManager {

    private final Map<Long, ConstructionSite> sites = new HashMap<>();
    private final List<ConstructionSiteUpdateListener> listeners = new ArrayList<>();
    private final ConstructionSitesRepository sitesRepository;

    /**
     * Adds a new construction site.
     *
     * @param site the construction site to add.
     */
    public void addSite(ConstructionSite site) {
        ConstructionSite savedSite = sitesRepository.save(site);

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
    // Additional methods for removal, lookup, etc.
}
