package com.zergatstage.domain;

import lombok.Data;

import java.util.List;

/**
 * Represents a construction site with a list of material requirements.
 */
@Data
public class ConstructionSite {

    private String siteId; // Unique identifier or name
    private List<MaterialRequirement> requirements;

    /**
     * Constructs a ConstructionSite.
     *
     * @param siteId Unique identifier for the construction site.
     * @param requirements List of material requirements.
     */
    public ConstructionSite(String siteId, List<MaterialRequirement> requirements) {
        this.siteId = siteId;
        this.requirements = requirements;
    }

    // Getters and setters omitted for brevity

    /**
     * Updates the requirement based on newly available cargo materials.
     *
     * @param cargoMaterial Material name delivered from the cargo event.
     * @param deliveredQuantity Quantity delivered.
     */
    public void updateRequirement(String cargoMaterial, double deliveredQuantity) {
        for (MaterialRequirement req : requirements) {
            if (req.getMaterialName().equalsIgnoreCase(cargoMaterial)) {
                req.addDeliveredQuantity(deliveredQuantity);
            }
        }
    }
}

