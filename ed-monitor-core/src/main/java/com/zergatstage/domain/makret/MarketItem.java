package com.zergatstage.domain.makret;

import com.zergatstage.domain.dictionary.Commodity;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "market_item")
public class MarketItem {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "commodity_id", nullable = false)
    private Commodity commodity;

    @ManyToOne
    @JoinColumn(name = "market_id", nullable = false)
    private Market market;

    private int buyPrice;
    private int sellPrice;
    private int stock;
    private int demand;

    @Override
    public String toString() {
        // Custom toString method for better logging and debugging.
        return commodity.getName() + " (Buy: " + buyPrice + ", Sell: " + sellPrice + ", Stock: " + stock + ")";
    }
}