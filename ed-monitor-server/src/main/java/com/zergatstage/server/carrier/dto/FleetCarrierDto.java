package com.zergatstage.server.carrier.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * REST transfer object for a Fleet Carrier, mirroring {@link com.zergatstage.domain.carrier.FleetCarrier}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FleetCarrierDto {

    @NotNull
    @Positive
    private Long carrierId;

    private String callsign;
    private String name;
    private String currentSystem;

    private boolean systemManuallySet;

    private int totalCapacity;
    private int cargoUsed;
    private int freeSpace;

    private Instant holdSnapshotAt;

    @Valid
    private List<CarrierHoldItemDto> hold = new ArrayList<>();

    private long version;
    private Instant lastUpdated;
}
