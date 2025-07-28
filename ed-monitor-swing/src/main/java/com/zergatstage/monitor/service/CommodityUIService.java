package com.zergatstage.monitor.service;

import com.zergatstage.dto.CommodityDTO;

import java.util.*;


public class CommodityUIService {
    public static CommodityUIService instance;
    private final Map<String, CommodityDTO> commodityMap;
    private final CommodityRegistry commodityRegistry;

    private CommodityUIService() {

        this.commodityRegistry = CommodityRegistry.getInstance();
        this.commodityMap = new HashMap<>();

    }

    public synchronized static CommodityUIService getInstance() {
        if (instance == null) {
            return new CommodityUIService();
        }
        return instance;
    }

    /**
     * Retrieves a commodity by ID. Throws if not found.
     *
     * @param name The commodity name.
     * @return The commodity.
     * @throws NoSuchElementException If not found.
     */
    public CommodityDTO getCommodityByName(String name) {
        return commodityMap.getOrDefault(name, CommodityDTO.builder().build());
    }

    /**
     * Retrieves a commodity by ID. Throws if not found.
     *
     * @param id The commodity ID.
     * @return The commodity.
     * @throws NoSuchElementException If not found.
     */
    public CommodityDTO getCommodity(String id) {
        return commodityMap.values().stream()
                .filter( commodity -> commodity.getId() == Long.getLong(id))
                .findFirst().orElseThrow();
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
                .map(CommodityDTO::getName)
                .toArray(String[]::new);
    }

    /**
     * Retrieves a commodity by ID or creates it if not found.
     *
     * @param id       The unique ID of the commodity.
     * @param name     The name of the commodity (used only if new).
     * @param category The category of the commodity (used only if new).
     * @return Existing or newly created Commodity.
     */

    public CommodityDTO getOrAddCommodity(long id, String name, String category) {
        return commodityMap.computeIfAbsent(name, key ->
                CommodityDTO.builder().id(id).name(name).category(category).build()
        );
    }

    public void updateCommodity(CommodityDTO updated) {
        commodityMap.replace(updated.getName(), updated);
    }

    public void deleteCommodity(String id) {
        commodityMap.remove(id);
    }
}
