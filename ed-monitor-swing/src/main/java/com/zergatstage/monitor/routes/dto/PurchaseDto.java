package com.zergatstage.monitor.routes.dto;

import java.util.Objects;

/**
 * Represents the purchase of a single material in tons at a specific leg.
 */
public class PurchaseDto {

    private String materialName;
    private double amountTons;
    private String category;

    /**
     * Creates an empty purchase entry.
     */
    public PurchaseDto() {
    }

    /**
     * Creates a fully populated purchase entry.
     *
     * @param materialName name of the purchased material
     * @param amountTons   amount of the material in tons
     */
    public PurchaseDto(String materialName, double amountTons) {
        this.materialName = materialName;
        this.amountTons = amountTons;
    }

    /**
     * Creates a fully populated purchase entry including the commodity's market category,
     * used to sort/group purchases in the UI the same way the in-game commodity market does.
     *
     * @param materialName name of the purchased material
     * @param amountTons   amount of the material in tons
     * @param category     commodity market category (e.g. "Chemicals", "Metals")
     */
    public PurchaseDto(String materialName, double amountTons, String category) {
        this.materialName = materialName;
        this.amountTons = amountTons;
        this.category = category;
    }

    /**
     * @return name of the purchased material
     */
    public String getMaterialName() {
        return materialName;
    }

    /**
     * @param materialName name of the purchased material
     */
    public void setMaterialName(String materialName) {
        this.materialName = materialName;
    }

    /**
     * @return amount purchased in tons
     */
    public double getAmountTons() {
        return amountTons;
    }

    /**
     * @param amountTons amount purchased in tons
     */
    public void setAmountTons(double amountTons) {
        this.amountTons = amountTons;
    }

    /**
     * @return commodity market category, or null if unknown
     */
    public String getCategory() {
        return category;
    }

    /**
     * @param category commodity market category
     */
    public void setCategory(String category) {
        this.category = category;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        PurchaseDto that = (PurchaseDto) o;
        return Double.compare(that.amountTons, amountTons) == 0
            && Objects.equals(materialName, that.materialName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(materialName, amountTons);
    }

    @Override
    public String toString() {
        return "PurchaseDto{"
            + "materialName='" + materialName + '\''
            + ", amountTons=" + amountTons
            + '}';
    }
}
