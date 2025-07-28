package com.zergatstage.domain.dictionary;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CargoItem {
    private long id;
    private int count;
    private int stolen;
}
