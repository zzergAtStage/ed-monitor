package com.zergatstage.server.market.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MarketItemDto {

    @NotNull
    @Valid
    private CommodityDto commodity;

    @Min(0)
    private int buyPrice;
    @Min(0)
    private int sellPrice;
    @Min(0)
    private int stock;
    @Min(0)
    private int demand;
}

