package com.zergatstage.server.routes.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents the result of a route optimization request, including all planned runs and
 * the percentage of remaining demand covered by the plan.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RoutePlanDto {
    private Long constructionSiteId;
    private List<DeliveryRunDto> runs = new ArrayList<>();
    private double coverageFraction;
}
