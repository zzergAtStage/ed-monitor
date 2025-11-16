package com.zergatstage.server.routes.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a single leg of a delivery run, corresponding to a visit to a market.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RunLegDto {
    private Long marketId;
    private String marketName;
    private List<PurchaseDto> purchases = new ArrayList<>();
}
