package com.zergatstage.monitor.http.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Wire representation of one commodity stack currently sitting in a Fleet Carrier's cargo hold.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CarrierHoldItemDto {
    private CommodityDto commodity;
    private int quantity;
}
