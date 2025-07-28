package com.zergatstage.domain;

import com.zergatstage.domain.dictionary.Commodity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;

/**
 * Represents a material requirement for a construction site.
 */
@Data
@Entity
@Builder
@RequiredArgsConstructor
@AllArgsConstructor
public class MaterialRequirement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @ManyToOne
    @JoinColumn(name = "commodity_id")
    private Commodity commodity;

    private int requiredQuantity;
    private int deliveredQuantity;


    // Getters and setters omitted for brevity

    /**
     * Adds delivered quantity to the current requirement.
     *
     * @param quantity Quantity to add.
     */
    public void addDeliveredQuantity(int quantity) {
        this.deliveredQuantity += quantity;
    }

    /**
     * Calculates the remaining quantity needed.
     *
     * @return remaining quantity.
     */
    public int getRemainingQuantity() {
        return Math.max(requiredQuantity - deliveredQuantity, 0);
    }
}
