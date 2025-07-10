package com.zergatstage.domain;

import lombok.Getter;

import java.util.List;

/**
 * Represents a construction site with a list of material requirements.
 */
@Getter
public class ConstructionSite {
    private final String siteId;
    private final long marketId;
    private final List<MaterialRequirement> requirements;

    public ConstructionSite(String siteId, long marketId, List<MaterialRequirement> requirements) {
        this.siteId = siteId;
        this.marketId = marketId;
        this.requirements = requirements;
    }

    /**
     * Calculates the overall construction progress in percentage (0–100).
     * This is the ratio of delivered materials to total required materials, across all commodities.
     */
    public int getProgressPercent() {
        int totalRequired = 0;
        int totalDelivered = 0;

        for (MaterialRequirement req : requirements) {
            totalRequired += req.getRequiredQuantity();
            totalDelivered += req.getDeliveredQuantity();
        }

        if (totalRequired == 0) {
            // If no materials are required, treat as 0% or 100%—here we choose 0%
            return 0;
        }
        double ratio = (double) totalDelivered / totalRequired;
        return (int) Math.min(ratio * 100, 100);
    }

    /**
     * Updates the requirement based on newly available cargo materials.
     *
     * @param cargoMaterial Material name delivered from the cargo event.
     * @param deliveredQuantity Quantity delivered.
     */
    public void updateRequirement(String cargoMaterial, int deliveredQuantity) {
        for (MaterialRequirement req : requirements) {
            if (req.getMaterialName().equalsIgnoreCase(cargoMaterial)) {
                req.addDeliveredQuantity(deliveredQuantity);
            }
        }
    }
}
