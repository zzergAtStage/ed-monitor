package com.zergatstage.monitor.routes.dto;

import java.util.HashMap;
import java.util.Map;
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
     * Commodities already sitting in the ship's cargo hold (commodity id -> quantity in tons).
     * These are credited toward outstanding demand instead of being re-purchased from a market.
     */
    private Map<Long, Integer> onboardCommodities = new HashMap<>();

    /**
     * How cargo should be routed with respect to a personal Fleet Carrier. Defaults to
     * {@link HaulingMode#DIRECT} (no carrier involvement) and is never {@code null}.
     */
    private HaulingMode mode = HaulingMode.DIRECT;

    /**
     * Snapshot of the Fleet Carrier to haul via, required for {@link HaulingMode#LOAD_CARRIER}
     * and {@link HaulingMode#UNLOAD_TO_SITE}. {@code null} when {@link #mode} is
     * {@link HaulingMode#DIRECT}.
     */
    private CarrierSnapshot carrier;

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

    /**
     * @return commodities already carried in the ship's cargo hold (commodity id -> tons)
     */
    public Map<Long, Integer> getOnboardCommodities() {
        return onboardCommodities;
    }

    /**
     * @param onboardCommodities commodities already carried in the ship's cargo hold (commodity id -> tons)
     */
    public void setOnboardCommodities(Map<Long, Integer> onboardCommodities) {
        this.onboardCommodities = onboardCommodities != null ? new HashMap<>(onboardCommodities) : new HashMap<>();
    }

    /**
     * @return how cargo should be routed with respect to a personal Fleet Carrier; never {@code null}
     */
    public HaulingMode getMode() {
        return mode;
    }

    /**
     * @param mode how cargo should be routed with respect to a personal Fleet Carrier;
     *             {@code null} is normalized to {@link HaulingMode#DIRECT}
     */
    public void setMode(HaulingMode mode) {
        this.mode = mode != null ? mode : HaulingMode.DIRECT;
    }

    /**
     * @return snapshot of the Fleet Carrier to haul via, or {@code null} when not applicable
     */
    public CarrierSnapshot getCarrier() {
        return carrier;
    }

    /**
     * @param carrier snapshot of the Fleet Carrier to haul via, or {@code null} when not applicable
     */
    public void setCarrier(CarrierSnapshot carrier) {
        this.carrier = carrier;
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
            && Objects.equals(constructionSiteId, that.constructionSiteId)
            && Objects.equals(onboardCommodities, that.onboardCommodities)
            && mode == that.mode
            && Objects.equals(carrier, that.carrier);
    }

    @Override
    public int hashCode() {
        return Objects.hash(constructionSiteId, cargoCapacityTons, maxMarketsPerRun,
            onboardCommodities, mode, carrier);
    }

    @Override
    public String toString() {
        return "RouteOptimizationRequest{"
            + "constructionSiteId=" + constructionSiteId
            + ", cargoCapacityTons=" + cargoCapacityTons
            + ", maxMarketsPerRun=" + maxMarketsPerRun
            + ", onboardCommodities=" + onboardCommodities
            + ", mode=" + mode
            + ", carrier=" + carrier
            + '}';
    }
}
