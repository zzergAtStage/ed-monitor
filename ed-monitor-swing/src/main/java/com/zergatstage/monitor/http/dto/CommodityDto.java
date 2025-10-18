package com.zergatstage.monitor.http.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CommodityDto {
    private Long id;
    private String name;
    private String nameLocalised;
    private String category;
    private String categoryLocalised;
}

