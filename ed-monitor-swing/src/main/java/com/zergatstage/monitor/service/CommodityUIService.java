package com.zergatstage.monitor.service;

import com.zergatstage.dto.CommodityDTO;

import java.util.*;


public class CommodityUIService {
    public static CommodityUIService instance;
    private final Map<Long, CommodityDTO> commodityMap;

    private CommodityUIService() {

        CommodityRegistry commodityRegistry = CommodityRegistry.getInstance();
        this.commodityMap = commodityRegistry.getAllCommodityDTO();

    }

    public synchronized static CommodityUIService getInstance() {
        if (instance == null) {
            return new CommodityUIService();
        }
        return instance;
    }

    /**
     * Get all Commodities for the Dictionary
     * @return List of Commodities
     */
    public List<CommodityDTO> getAll() {
        return commodityMap.values().stream().toList();
    }

    public String[] getAllNames(){
        return commodityMap.values().stream()
                .map(CommodityDTO::getNameLocalised)
                .toArray(String[]::new);
    }

    /**
     * Retrieves a commodity by ID or creates it if not found. Used by UI
     *
     * @param id       The unique ID of the commodity.
     * @param name     The name of the commodity (used only if new).
     * @param category The category of the commodity (used only if new).
     */

    public void getOrAddCommodity(long id, String name, String category) {
        commodityMap.computeIfAbsent(id, key ->
                CommodityDTO.builder()
                        .id(id)
                        .name(name)
                        .category(category)
                        .build()
        );
    }

    public void updateCommodity(CommodityDTO updated) {
        commodityMap.replace(updated.getId(), updated);
    }

    public void deleteCommodity(String id) {
        commodityMap.remove(id);
    }
}
