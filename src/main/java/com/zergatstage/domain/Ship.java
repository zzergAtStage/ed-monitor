package com.zergatstage.domain;

import com.zergatstage.domain.dictionary.Commodity;
import lombok.Data;

import java.util.Map;

@Data
public class Ship {
    int shipId;
    String ship;
    int cargoCapacity;
    String shipName;
    Map<Commodity, Integer> commodities;
}
