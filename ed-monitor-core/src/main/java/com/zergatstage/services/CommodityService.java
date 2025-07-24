package com.zergatstage.services;

import com.zergatstage.domain.dictionary.Commodity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CommodityService {
    public Commodity getOrAddCommodity(String id, String name, String category) {
        return null;
    }

    public Commodity getCommodityByName(String commodityName) {
        return null;
    }
}
