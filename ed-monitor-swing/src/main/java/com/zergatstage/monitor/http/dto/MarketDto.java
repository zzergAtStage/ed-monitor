package com.zergatstage.monitor.http.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MarketDto {
    private Long marketId;
    private String stationName;
    private String stationType;
    private String systemName;
    private List<MarketItemDto> items = new ArrayList<>();
}

