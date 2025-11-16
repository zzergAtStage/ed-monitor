package com.zergatstage.monitor.routes.dto;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Represents a single delivery run that may include several market legs and summarizes
 * the tonnage delivered during that run.
 */
public class DeliveryRunDto {

    private int runIndex;
    private List<RunLegDto> legs = new ArrayList<>();
    private double totalTonnage;
    private Map<String, Double> materialsSummaryTons = new HashMap<>();

    /**
     * Creates an empty run with no legs or summary data.
     */
    public DeliveryRunDto() {
    }

    /**
     * Creates a fully populated run.
     *
     * @param runIndex             positional index of the run in the overall plan
     * @param legs                 ordered legs making up the run
     * @param totalTonnage         tonnage delivered during the run
     * @param materialsSummaryTons aggregated tonnage per material delivered in the run
     */
    public DeliveryRunDto(int runIndex,
                          List<RunLegDto> legs,
                          double totalTonnage,
                          Map<String, Double> materialsSummaryTons) {
        this.runIndex = runIndex;
        if (legs != null) {
            this.legs = new ArrayList<>(legs);
        }
        this.totalTonnage = totalTonnage;
        if (materialsSummaryTons != null) {
            this.materialsSummaryTons = new HashMap<>(materialsSummaryTons);
        }
    }

    /**
     * @return positional index of the run
     */
    public int getRunIndex() {
        return runIndex;
    }

    /**
     * @param runIndex positional index of the run
     */
    public void setRunIndex(int runIndex) {
        this.runIndex = runIndex;
    }

    /**
     * @return ordered legs that make up the run
     */
    public List<RunLegDto> getLegs() {
        return legs;
    }

    /**
     * @param legs ordered legs that make up the run
     */
    public void setLegs(List<RunLegDto> legs) {
        this.legs = legs != null ? new ArrayList<>(legs) : new ArrayList<>();
    }

    /**
     * @return tonnage delivered during the run
     */
    public double getTotalTonnage() {
        return totalTonnage;
    }

    /**
     * @param totalTonnage tonnage delivered during the run
     */
    public void setTotalTonnage(double totalTonnage) {
        this.totalTonnage = totalTonnage;
    }

    /**
     * @return map summarizing delivered tonnage per material
     */
    public Map<String, Double> getMaterialsSummaryTons() {
        return materialsSummaryTons;
    }

    /**
     * @param materialsSummaryTons map summarizing delivered tonnage per material
     */
    public void setMaterialsSummaryTons(Map<String, Double> materialsSummaryTons) {
        this.materialsSummaryTons =
            materialsSummaryTons != null ? new HashMap<>(materialsSummaryTons) : new HashMap<>();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        DeliveryRunDto that = (DeliveryRunDto) o;
        return runIndex == that.runIndex
            && Double.compare(that.totalTonnage, totalTonnage) == 0
            && Objects.equals(legs, that.legs)
            && Objects.equals(materialsSummaryTons, that.materialsSummaryTons);
    }

    @Override
    public int hashCode() {
        return Objects.hash(runIndex, legs, totalTonnage, materialsSummaryTons);
    }

    @Override
    public String toString() {
        return "DeliveryRunDto{"
            + "runIndex=" + runIndex
            + ", legs=" + legs
            + ", totalTonnage=" + totalTonnage
            + ", materialsSummaryTons=" + materialsSummaryTons
            + '}';
    }
}
