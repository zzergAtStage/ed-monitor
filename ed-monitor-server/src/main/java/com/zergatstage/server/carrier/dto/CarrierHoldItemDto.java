package com.zergatstage.server.carrier.dto;

import com.zergatstage.server.market.dto.CommodityDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents one commodity stack currently sitting in a Fleet Carrier's cargo hold, as
 * transferred over the REST API.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CarrierHoldItemDto {

    @NotNull
    @Valid
    private CommodityDto commodity;

    @Min(0)
    private int quantity;
}
