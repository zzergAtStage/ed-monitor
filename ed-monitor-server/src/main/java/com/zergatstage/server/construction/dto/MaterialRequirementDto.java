package com.zergatstage.server.construction.dto;

import com.zergatstage.server.market.dto.CommodityDto;
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

