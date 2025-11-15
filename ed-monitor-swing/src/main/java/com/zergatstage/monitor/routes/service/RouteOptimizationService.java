package com.zergatstage.monitor.routes.service;

import com.zergatstage.monitor.routes.dto.RouteOptimizationRequest;
import com.zergatstage.monitor.routes.dto.RoutePlanDto;

/**
 * Builds near-optimal delivery route plans for supplying a single construction site using
 * local market data. The interface abstracts away the implementation so the optimizer can
 * later move between Swing and server environments without affecting the UI.
 *
 * <p>Current assumptions:
 * <ul>
 *     <li>All legs have uniform traversal cost.</li>
 *     <li>The optimizer targets a single construction site per request.</li>
 *     <li>Market data is pre-loaded/offline and not fetched as a side effect.</li>
 * </ul>
 * </p>
 */
public interface RouteOptimizationService {

    /**
     * Builds a route plan for the specified construction site request based exclusively on
     * the provided input data.
     *
     * @param request parameters describing the target construction site, cargo capacity, and
     *                run constraints
     * @return route plan describing the optimized runs; returns an empty plan when there are
     * no outstanding requirements or no matching markets
     * @implNote Implementations must be pure with respect to the provided input data (no side
     * effects such as persisting or mutating shared state).
     */
    RoutePlanDto buildRoutePlan(RouteOptimizationRequest request);
}
