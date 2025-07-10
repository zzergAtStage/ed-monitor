package com.zergatstage.domain;

import lombok.Data;

/**
 * Represents a material requirement for a construction site.
 */
@Data
public class MaterialRequirement {

    private String materialName;
    private int requiredQuantity;
    private int deliveredQuantity;

    /**
     * Constructs a MaterialRequirement.
     *
     * @param materialName Name of the material.
     * @param requiredQuantity Total quantity required.
     */
    public MaterialRequirement(String materialName, int requiredQuantity) {
        this.materialName = materialName;
        this.requiredQuantity = requiredQuantity;
        this.deliveredQuantity = 0;
    }

    // Getters and setters omitted for brevity

    /**
     * Adds delivered quantity to the current requirement.
     *
     * @param quantity Quantity to add.
     */
    public void addDeliveredQuantity(int quantity) {
        this.deliveredQuantity += quantity;
    }

    /**
     * Calculates the remaining quantity needed.
     *
     * @return remaining quantity.
     */
    public int getRemainingQuantity() {
        return Math.max(requiredQuantity - deliveredQuantity, 0);
    }
}
