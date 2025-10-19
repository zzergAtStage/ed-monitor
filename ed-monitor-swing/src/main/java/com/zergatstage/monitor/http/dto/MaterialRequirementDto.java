package com.zergatstage.monitor.http.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MaterialRequirementDto {
    private long id;
    private CommodityDto commodity;
    private int requiredQuantity;
    private int deliveredQuantity;
}

