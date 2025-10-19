package com.zergatstage.monitor.http.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConstructionSiteDto {
    private long marketId;
    private String siteId;
    private List<MaterialRequirementDto> requirements = new ArrayList<>();
}

