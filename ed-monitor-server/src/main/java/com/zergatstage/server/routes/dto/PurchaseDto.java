package com.zergatstage.server.routes.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Describes the purchase of a particular material in tons at a market leg.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseDto {
    private String materialName;
    private double amountTons;
}
