package com.zergatstage.domain.dictionary;

import lombok.Getter;

/**
 * Represents a material with a name and the required amount.
 */
public class Material {
    private final Commodity commodity;

    @Getter
    private final int requiredAmount;

    /**
     * Constructs a Material.
     *
     * @param commodity      the name of the material
     * @param requiredAmount the required amount of the material
     */
    public Material(Commodity commodity, int requiredAmount) {
        this.commodity = commodity;
        this.requiredAmount = requiredAmount;
    }

    /**
     * Returns the name of the material.
     *
     * @return the material name
     */
    public String getName() {
        return commodity.getName();
    }

    /**
     * Exposes the commodity backing this material so callers can access its metadata (id, etc.).
     *
     * @return the commodity instance backing this material
     */
    public Commodity getCommodity() {
        return commodity;
    }
}
