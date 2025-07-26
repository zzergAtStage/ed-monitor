package com.zergatstage.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for {@code MaterialRequirement}.
 * Keeps only raw values, no behavior.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MaterialRequirementDTO {


    private String name;
    /**
     * Name of the material/commodity.
     */
    private String materialName;

    /**
     * Quantity required in total.
     */
    private int requiredQuantity;

    /**
     * Quantity already delivered.
     */
    private int deliveredQuantity;
}
