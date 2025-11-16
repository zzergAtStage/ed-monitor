package com.zergatstage.monitor.routes.dto;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Describes the result of a route optimization request for a single construction site,
 * including all delivery runs and the overall demand coverage achieved.
 */
public class RoutePlanDto {

    private Long constructionSiteId;
    private List<DeliveryRunDto> runs = new ArrayList<>();
    private double coverageFraction;

    /**
     * Creates an empty plan with no runs.
     */
    public RoutePlanDto() {
    }

    /**
     * Creates a plan populated with the supplied data.
     *
     * @param constructionSiteId identifier of the site covered by the plan
     * @param runs               ordered delivery runs that make up the plan
     * @param coverageFraction   fraction (0-1) of remaining demand covered by the plan
     */
    public RoutePlanDto(Long constructionSiteId, List<DeliveryRunDto> runs, double coverageFraction) {
        this.constructionSiteId = constructionSiteId;
        if (runs != null) {
            this.runs = new ArrayList<>(runs);
        }
        this.coverageFraction = coverageFraction;
    }

    /**
     * @return identifier of the construction site covered by the plan
     */
    public Long getConstructionSiteId() {
        return constructionSiteId;
    }

    /**
     * @param constructionSiteId identifier of the construction site covered by the plan
     */
    public void setConstructionSiteId(Long constructionSiteId) {
        this.constructionSiteId = constructionSiteId;
    }

    /**
     * @return ordered delivery runs included in this plan
     */
    public List<DeliveryRunDto> getRuns() {
        return runs;
    }

    /**
     * @param runs ordered delivery runs included in this plan
     */
    public void setRuns(List<DeliveryRunDto> runs) {
        this.runs = runs != null ? new ArrayList<>(runs) : new ArrayList<>();
    }

    /**
     * @return fraction (0-1) of outstanding demand covered by the plan
     */
    public double getCoverageFraction() {
        return coverageFraction;
    }

    /**
     * @param coverageFraction fraction (0-1) of outstanding demand covered by the plan
     */
    public void setCoverageFraction(double coverageFraction) {
        this.coverageFraction = coverageFraction;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        RoutePlanDto that = (RoutePlanDto) o;
        return Double.compare(that.coverageFraction, coverageFraction) == 0
            && Objects.equals(constructionSiteId, that.constructionSiteId)
            && Objects.equals(runs, that.runs);
    }

    @Override
    public int hashCode() {
        return Objects.hash(constructionSiteId, runs, coverageFraction);
    }

    @Override
    public String toString() {
        return "RoutePlanDto{"
            + "constructionSiteId=" + constructionSiteId
            + ", runs=" + runs
            + ", coverageFraction=" + coverageFraction
            + '}';
    }
}
