package com.zergatstage.services;

import com.zergatstage.domain.ConstructionSite;
import lombok.Getter;
import lombok.Setter;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages construction sites and their material requirements.
 */
@Getter
@Setter
@Component
public class ConstructionSiteManager {

    private final List<ConstructionSite> sites = new ArrayList<>();
    private final List<ConstructionSiteUpdateListener> listeners = new ArrayList<>();

    /**
     * Adds a new construction site.
     *
     * @param site the construction site to add.
     */
    public void addSite(ConstructionSite site) {
        sites.add(site);
    }

    /**
     * Updates all construction sites with the delivered cargo.
     *
     * @param material the material name.
     * @param deliveredQuantity the delivered quantity.
     */
    public void updateSitesWithCargo(String material, double deliveredQuantity) {
        for (ConstructionSite site : sites) {
            site.updateRequirement(material, deliveredQuantity);
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
    // Additional methods for removal, lookup, etc.
}
