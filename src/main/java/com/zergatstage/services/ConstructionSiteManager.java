package com.zergatstage.services;

import com.zergatstage.domain.ConstructionSite;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages construction sites and their material requirements.
 */
@Getter
@Setter
public class ConstructionSiteManager {

    private final List<ConstructionSite> sites = new ArrayList<>();

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
        }
    }

    // Additional methods for removal, lookup, etc.
}
