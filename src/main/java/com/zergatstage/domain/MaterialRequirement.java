package com.zergatstage.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;

/**
 * Represents a material requirement for a construction site.
 */
@Data
@Entity
@Builder
@RequiredArgsConstructor
@AllArgsConstructor
public class MaterialRequirement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;
    private String name;

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
