package com.zergatstage.server.market.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CommodityDto {
    @NotNull
    @Positive
    private Long id;
    private String name;
    private String nameLocalised;
    private String category;
    private String categoryLocalised;
}

