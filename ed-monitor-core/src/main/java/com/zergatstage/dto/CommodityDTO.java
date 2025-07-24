package com.zergatstage.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommodityDTO {
    private String id;
    private String name;
    private String nameLocalised;
    private String category;
    private String categoryLocalised;
}
