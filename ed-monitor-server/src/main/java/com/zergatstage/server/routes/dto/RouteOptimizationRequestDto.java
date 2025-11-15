package com.zergatstage.server.routes.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Server-side transport describing an optimization request for a single construction site.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RouteOptimizationRequestDto {
    private Long constructionSiteId;
    private double cargoCapacityTons;
    private int maxMarketsPerRun = 2;
}
