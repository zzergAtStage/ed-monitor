package com.zergatstage.domain.commander;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "commander")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Commander {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Builder.Default
    private boolean isDeleted = false;
    private String name;

    @Column(nullable = false, unique = true)
    private String FID;
}
