package com.zergatstage.dto;

import com.zergatstage.domain.makret.Market;
import lombok.*;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * DTO representing {@code ConstructionSite}.
 * Contains only data needed for transfer between layers/boundaries.
 */

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConstructionSiteDTO{

    /**
     * Identifier of the market where the site belongs.
     */
    private long marketId;

    /**
     * Human-readable/site-specific identifier.
     */
    private String siteId;

    /**
     * List of material requirements for the site.
     */
    private CopyOnWriteArrayList<MaterialRequirementDTO> requirements;
}

