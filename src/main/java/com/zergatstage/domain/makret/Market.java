package com.zergatstage.domain.makret;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import lombok.*;
import jakarta.persistence.Id;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Market {

    @Id
    private String marketId;

    private String stationName;
    private String stationType;
    private String systemName;

    @OneToMany(mappedBy = "market", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private Map<String, MarketItem> items = new HashMap<>();

    /**
     * Adds a market item to the market.
     *
     * @param item The market item to add.
     */
    public void addItem(MarketItem item) {
        // Ensure that the commodity is not null and then add the item keyed by its unique commodity ID.
        if (item.getCommodity() != null) {
            items.put(item.getCommodity().getId(), item);
        }
    }

    /**
     * Returns an unmodifiable view of the market items.
     *
     * @return An unmodifiable map of market items.
     */
    public Map<String, MarketItem> getItems() {
        return Collections.unmodifiableMap(items);
    }

    /**
     * Retrieves a market item based on the commodity ID.
     *
     * @param commodityId The unique identifier of the commodity.
     * @return The corresponding market item, or null if not found.
     */
    public MarketItem getItem(String commodityId) {
        return items.get(commodityId);
    }
}