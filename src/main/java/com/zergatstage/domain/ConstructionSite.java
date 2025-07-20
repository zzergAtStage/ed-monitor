package com.zergatstage.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;

import java.util.List;

/**
 * Represents a construction site with a list of material requirements.
 */
@Getter
@Entity
@AllArgsConstructor
@NoArgsConstructor
public class ConstructionSite {


    @Id
    private long marketId;
    private String siteId;
    @OneToMany(fetch = FetchType.LAZY)
    private List<MaterialRequirement> requirements;

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
     * @param materialRequirement MaterialRequirement delivered from the cargo event.
     */
    public void updateRequirement(MaterialRequirement materialRequirement) {
        for (MaterialRequirement req : requirements) {
            if (req.getMaterialName().equalsIgnoreCase(materialRequirement.getMaterialName())) {
                req.addDeliveredQuantity(materialRequirement.getDeliveredQuantity());
                req.setRequiredQuantity(materialRequirement.getRequiredQuantity());
            }
        }
    }

    /**
     * Update delivered quantity
     * @parameter Commodity name
     * @parameter Delivered quality
     */
    public void updateDeliveredQuantity(String commodityName, int delivered){
        for (MaterialRequirement req : requirements) {
            if (req.getMaterialName().equalsIgnoreCase(commodityName)) {
                req.addDeliveredQuantity(delivered);//TODO: check negative values
               }
        }
    }
}
