package com.zergatstage.monitor.routes.spi;

import com.zergatstage.monitor.http.dto.ConstructionSiteDto;
import com.zergatstage.monitor.http.dto.MarketDto;

import java.io.IOException;
import java.util.List;

/**
 * Provides read-only access to construction site and market data required by the route
 * optimization algorithm. The facade shields the optimizer from HTTP/client specifics
 * and is intended to work with cached/offline data snapshots.
 */
public interface RouteOptimizerDataProvider {

    /**
     * Loads the latest construction site view for the supplied identifier.
     *
     * @param constructionSiteId target site identifier
     * @return construction site DTO if found, otherwise {@code null}
     * @throws IOException if data cannot be loaded due to network or serialization errors
     */
    ConstructionSiteDto loadConstructionSite(long constructionSiteId) throws IOException;

    /**
     * Loads markets that sell at least one outstanding required material for the specified
     * construction site. The implementation may fetch full market inventories and filter
     * them client-side until a dedicated endpoint is available.
     *
     * @param constructionSiteId target site identifier
     * @return markets that have at least one matching commodity; empty if none exist
     * @throws IOException if data cannot be loaded due to network or serialization errors
     */
    List<MarketDto> loadCandidateMarkets(long constructionSiteId) throws IOException;

    /**
     * Loads a specific market by identifier to provide auxiliary metadata such as system name.
     *
     * @param marketId market identifier
     * @return market DTO or {@code null} if not found
     * @throws IOException if data cannot be loaded
     */
    MarketDto loadMarket(long marketId) throws IOException;
}
