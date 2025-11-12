package com.zergatstage.server.construction.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConstructionSiteDto {
    private long marketId;
    private String siteId;
    private List<MaterialRequirementDto> requirements;
    private Long version;
    private Instant lastUpdated;
}
