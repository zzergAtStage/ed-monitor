package com.zergatstage.domain.dictionary;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;

/**
 * Represents a unique commodity entity.
 * <p>
 * This entity holds commodity-specific data that is shared across multiple markets.
 * It is intended to be stored in its own table to enforce uniqueness.
 * </p>
 */

@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
@Table(name = "commodity")
public class Commodity {

    @Id
    private String id;

    private String name;
    private String category;    
}
