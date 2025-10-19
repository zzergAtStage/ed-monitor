package com.zergatstage.domain;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Represents a construction site with a list of material requirements.
 */
@Getter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConstructionSite{


    @Id
    private long marketId;
    private String siteId;
    @OneToMany(fetch = FetchType.LAZY, cascade = CascadeType.ALL)
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

    public synchronized List<MaterialRequirement> getRequirements() {
        if (requirements == null) {
            requirements = new ArrayList<>();
        }
        return requirements;
    }

    /**
     * Updates the requirement based on newly available cargo materials.
     *
     * @param materialRequirement MaterialRequirement delivered from the cargo event.
     */
    public void updateRequirement(MaterialRequirement materialRequirement) {
        for (MaterialRequirement req : requirements) {
            if (Objects.equals(req.getCommodity().getId(), materialRequirement.getCommodity().getId())) {
                req.addDeliveredQuantity(materialRequirement.getDeliveredQuantity());
                req.setRequiredQuantity(materialRequirement.getRequiredQuantity());
            }
        }
    }

    /**
     * Update delivered quantity
     * @param commodityId Commodity id
     * @param delivered Delivered quantity
     */
    public void updateDeliveredQuantity(long commodityId , int delivered){
        for (MaterialRequirement req : requirements) {
            if (req.getCommodity().getId() == commodityId) {
                req.addDeliveredQuantity(delivered);//TODO: check negative values
               }
        }
    }
}
