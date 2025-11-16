package com.zergatstage.monitor.routes.dto;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Represents a single market visit within a delivery run, including the purchases
 * performed in that leg.
 */
public class RunLegDto {

    private Long marketId;
    private String marketName;
    private List<PurchaseDto> purchases = new ArrayList<>();

    /**
     * Creates an empty leg with no purchases.
     */
    public RunLegDto() {
    }

    /**
     * Creates a fully specified leg.
     *
     * @param marketId   identifier of the market visited
     * @param marketName display name of the market visited
     * @param purchases  materials acquired during the leg
     */
    public RunLegDto(Long marketId, String marketName, List<PurchaseDto> purchases) {
        this.marketId = marketId;
        this.marketName = marketName;
        if (purchases != null) {
            this.purchases = new ArrayList<>(purchases);
        }
    }

    /**
     * @return identifier of the market visited during the leg
     */
    public Long getMarketId() {
        return marketId;
    }

    /**
     * @param marketId identifier of the market visited during the leg
     */
    public void setMarketId(Long marketId) {
        this.marketId = marketId;
    }

    /**
     * @return display name of the market visited
     */
    public String getMarketName() {
        return marketName;
    }

    /**
     * @param marketName display name of the market visited
     */
    public void setMarketName(String marketName) {
        this.marketName = marketName;
    }

    /**
     * @return purchases included in the leg
     */
    public List<PurchaseDto> getPurchases() {
        return purchases;
    }

    /**
     * @param purchases purchases included in the leg
     */
    public void setPurchases(List<PurchaseDto> purchases) {
        this.purchases = purchases != null ? new ArrayList<>(purchases) : new ArrayList<>();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        RunLegDto runLegDto = (RunLegDto) o;
        return Objects.equals(marketId, runLegDto.marketId)
            && Objects.equals(marketName, runLegDto.marketName)
            && Objects.equals(purchases, runLegDto.purchases);
    }

    @Override
    public int hashCode() {
        return Objects.hash(marketId, marketName, purchases);
    }

    @Override
    public String toString() {
        return "RunLegDto{"
            + "marketId=" + marketId
            + ", marketName='" + marketName + '\''
            + ", purchases=" + purchases
            + '}';
    }
}
