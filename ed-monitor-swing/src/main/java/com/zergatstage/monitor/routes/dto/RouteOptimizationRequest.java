package com.zergatstage.monitor.routes.dto;

import java.util.Objects;

/**
 * Request payload describing the context for building a delivery route plan for a single
 * construction site.
 */
public class RouteOptimizationRequest {

    private static final int DEFAULT_MAX_MARKETS_PER_RUN = 2;

    private Long constructionSiteId;
    private double cargoCapacityTons;
    private int maxMarketsPerRun = DEFAULT_MAX_MARKETS_PER_RUN;

    /**
     * Creates an empty request with the default {@link #maxMarketsPerRun} value.
     */
    public RouteOptimizationRequest() {
    }

    /**
     * Creates a request with the provided construction site id and cargo capacity using
     * the default maximum number of markets per run.
     *
     * @param constructionSiteId identifier of the site that needs to be supplied
     * @param cargoCapacityTons  tonnage capacity of the delivery vehicle
     */
    public RouteOptimizationRequest(Long constructionSiteId, double cargoCapacityTons) {
        this(constructionSiteId, cargoCapacityTons, DEFAULT_MAX_MARKETS_PER_RUN);
    }

    /**
     * Creates a request with fully specified parameters.
     *
     * @param constructionSiteId identifier of the site that needs to be supplied
     * @param cargoCapacityTons  tonnage capacity of the delivery vehicle
     * @param maxMarketsPerRun   upper bound of markets that can be visited per run
     */
    public RouteOptimizationRequest(Long constructionSiteId,
                                    double cargoCapacityTons,
                                    int maxMarketsPerRun) {
        this.constructionSiteId = constructionSiteId;
        this.cargoCapacityTons = cargoCapacityTons;
        this.maxMarketsPerRun = maxMarketsPerRun;
    }

    /**
     * @return identifier of the construction site the optimizer should consider
     */
    public Long getConstructionSiteId() {
        return constructionSiteId;
    }

    /**
     * @param constructionSiteId identifier of the construction site the optimizer should consider
     */
    public void setConstructionSiteId(Long constructionSiteId) {
        this.constructionSiteId = constructionSiteId;
    }

    /**
     * @return cargo capacity of a single delivery run in tons
     */
    public double getCargoCapacityTons() {
        return cargoCapacityTons;
    }

    /**
     * @param cargoCapacityTons cargo capacity of a single delivery run in tons
     */
    public void setCargoCapacityTons(double cargoCapacityTons) {
        this.cargoCapacityTons = cargoCapacityTons;
    }

    /**
     * @return maximum number of markets a single run is allowed to visit
     */
    public int getMaxMarketsPerRun() {
        return maxMarketsPerRun;
    }

    /**
     * @param maxMarketsPerRun maximum number of markets a single run is allowed to visit
     */
    public void setMaxMarketsPerRun(int maxMarketsPerRun) {
        this.maxMarketsPerRun = maxMarketsPerRun;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        RouteOptimizationRequest that = (RouteOptimizationRequest) o;
        return Double.compare(that.cargoCapacityTons, cargoCapacityTons) == 0
            && maxMarketsPerRun == that.maxMarketsPerRun
            && Objects.equals(constructionSiteId, that.constructionSiteId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(constructionSiteId, cargoCapacityTons, maxMarketsPerRun);
    }

    @Override
    public String toString() {
        return "RouteOptimizationRequest{"
            + "constructionSiteId=" + constructionSiteId
            + ", cargoCapacityTons=" + cargoCapacityTons
            + ", maxMarketsPerRun=" + maxMarketsPerRun
            + '}';
    }
}
