package com.zergatstage.domain.dictionary;

import jakarta.persistence.Column;
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
    private Long id;

    private String name;
    @Column(name = "Name_Localised")
    private String nameLocalised;
    private String category;
    @Column(name = "Category_Localised")
    private String categoryLocalised;
}
