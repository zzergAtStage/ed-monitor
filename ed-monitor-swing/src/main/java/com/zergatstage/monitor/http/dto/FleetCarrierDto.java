package com.zergatstage.monitor.http.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Wire representation of a Fleet Carrier's state, matching the server-side
 * {@code /api/v1/carriers} contract.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FleetCarrierDto {
    private Long carrierId;
    private String callsign;
    private String name;
    private String currentSystem;
    private boolean systemManuallySet;
    private int totalCapacity;
    private int cargoUsed;
    private int freeSpace;
    private Instant holdSnapshotAt;
    private List<CarrierHoldItemDto> hold = new ArrayList<>();
    private long version;
    private Instant lastUpdated;
}
