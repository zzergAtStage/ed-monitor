package com.zergatstage.services.hauling;

import com.zergatstage.domain.dictionary.Commodity;
import lombok.Getter;

/**
 * Represents a material with a name and the required amount.
 */
class Material {
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

}
