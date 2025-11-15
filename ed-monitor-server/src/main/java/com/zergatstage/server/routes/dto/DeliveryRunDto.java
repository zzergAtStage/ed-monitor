package com.zergatstage.server.routes.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Summarizes a single delivery run including its legs and aggregated material tonnage.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryRunDto {
    private int runIndex;
    private List<RunLegDto> legs = new ArrayList<>();
    private double totalTonnage;
    private Map<String, Double> materialsSummaryTons = new HashMap<>();
}
