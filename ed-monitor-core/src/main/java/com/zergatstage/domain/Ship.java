package com.zergatstage.domain;

import com.zergatstage.domain.dictionary.CargoItem;
import lombok.*;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Ship {
    int shipId;
    String ship;
    int cargoCapacity;
    String shipName;
    private final ConcurrentMap<Long, CargoItem> commodities = new ConcurrentHashMap<>(); //id, Cargo
    @Synchronized
    public boolean isCargoStateKnown() {
        return isCargoStateKnown;
    }
    @Synchronized
    public void setCargoStateKnown(boolean cargoStateKnown) {
        isCargoStateKnown = cargoStateKnown;
    }

    volatile boolean isCargoStateKnown;


    public void clearCargo() {
        if (!commodities.isEmpty()) {
            commodities.clear();
        }
    }

    public int getCurrentCargoCount() {
        return commodities.values().stream().mapToInt(CargoItem::getCount).sum();
    }
}
