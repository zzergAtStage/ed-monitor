package com.zergatstage.domain.carrier;

import com.zergatstage.domain.dictionary.Commodity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents one commodity stack currently sitting in a Fleet Carrier's cargo hold.
 */
@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CarrierHoldItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @ManyToOne
    @JoinColumn(name = "commodity_id")
    private Commodity commodity;

    private int quantity;
}
