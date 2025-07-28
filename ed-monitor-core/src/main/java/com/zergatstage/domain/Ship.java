package com.zergatstage.domain;

import com.zergatstage.domain.dictionary.CargoItem;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Ship {
    int shipId;
    String ship;
    int cargoCapacity;
    String shipName;
    Map<Long, CargoItem> commodities; //id, Cargo
    boolean isCargoStateKnown;

    public void clearCargo() {
        if (commodities != null) {
            commodities.clear();
        }
    }

    public int getCurrentCargoCount() {
        return commodities.values().stream().mapToInt(CargoItem::getCount).sum();
    }
}
