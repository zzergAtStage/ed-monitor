package com.zergatstage.domain.carrier;

import com.zergatstage.domain.dictionary.Commodity;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a Fleet Carrier with its current state, cargo hold, and tracking information.
 */
@Getter
@Setter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"hold"})
public class FleetCarrier {

    /**
     * The CarrierID from the journal CarrierStats event, which equals the carrier's MarketID.
     */
    @Id
    private long carrierId;

    private String callsign;
    private String name;
    private String currentSystem;

    /**
     * When true, CarrierJump/CarrierLocation events must NOT overwrite currentSystem.
     */
    private boolean systemManuallySet;

    /**
     * Total cargo capacity of the carrier. From CarrierStats.SpaceUsage.TotalCapacity.
     */
    private int totalCapacity;

    /**
     * Amount of cargo currently used. From CarrierStats.SpaceUsage.Cargo.
     */
    private int cargoUsed;

    /**
     * Free space available in cargo hold. From CarrierStats.SpaceUsage.FreeSpace.
     */
    private int freeSpace;

    /**
     * Timestamp of the last absolute hold snapshot read from the carrier's own Market.json.
     * CargoTransfer deltas older than this must be ignored.
     */
    private Instant holdSnapshotAt;

    /**
     * The list of commodity stacks currently in the carrier's hold.
     */
    @OneToMany(fetch = FetchType.EAGER, cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<CarrierHoldItem> hold = new ArrayList<>();

    /**
     * JPA optimistic locking version field.
     */
    @Version
    private long version;

    /**
     * Timestamp of the last update to this entity. Automatically set on persist and update.
     */
    @org.hibernate.annotations.ColumnDefault("CURRENT_TIMESTAMP")
    @Column(nullable = false, columnDefinition = "TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP")
    private Instant lastUpdated;

    /**
     * Returns the quantity of a specific commodity currently held, or 0 if not present.
     *
     * @param commodityId the commodity ID to search for
     * @return the quantity held, or 0 if not found
     */
    public int getHeldQuantity(long commodityId) {
        return hold.stream()
                .filter(item -> item.getCommodity() != null && item.getCommodity().getId() == commodityId)
                .mapToInt(CarrierHoldItem::getQuantity)
                .findFirst()
                .orElse(0);
    }

    /**
     * Updates the quantity of a commodity in the hold. If the commodity already exists in the hold,
     * its quantity is updated. If the new quantity is <= 0, the item is removed from the hold.
     * If the commodity does not exist, a new CarrierHoldItem is appended.
     *
     * @param commodity the commodity to update (must not be null)
     * @param quantity the new quantity
     * @throws NullPointerException if commodity is null
     */
    public void setHeldQuantity(Commodity commodity, int quantity) {
        if (commodity == null) {
            throw new NullPointerException("Commodity must not be null");
        }

        for (int i = 0; i < hold.size(); i++) {
            CarrierHoldItem item = hold.get(i);
            // Commodity#getId() is a boxed Long: compare by value, never by reference.
            if (item.getCommodity() != null && java.util.Objects.equals(item.getCommodity().getId(), commodity.getId())) {
                if (quantity <= 0) {
                    hold.remove(i);
                } else {
                    item.setQuantity(quantity);
                }
                return;
            }
        }

        // Commodity not found in hold; add new item if quantity > 0
        if (quantity > 0) {
            hold.add(CarrierHoldItem.builder()
                    .commodity(commodity)
                    .quantity(quantity)
                    .build());
        }
    }

    @PrePersist
    protected void onCreate() {
        lastUpdated = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        lastUpdated = Instant.now();
    }
}
