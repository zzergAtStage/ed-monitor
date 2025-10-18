package com.zergatstage.monitor.http.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MarketItemDto {
    private CommodityDto commodity;
    private int buyPrice;
    private int sellPrice;
    private int stock;
    private int demand;
}

