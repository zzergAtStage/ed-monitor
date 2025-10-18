package com.zergatstage.server.market.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MarketDto {
    @NotNull
    @Positive
    private Long marketId;

    @NotBlank
    private String stationName;
    private String stationType;
    private String systemName;

    @Valid
    private List<MarketItemDto> items = new ArrayList<>();
}

