package com.zergatstage.monitor.service;

import com.zergatstage.domain.dictionary.Commodity;
import com.zergatstage.domain.makret.Market;
import com.zergatstage.domain.makret.MarketItem;
import com.zergatstage.dto.CommodityDTO;
import com.zergatstage.dto.CommodityMapper;
import com.zergatstage.tools.CommodityHelper;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
public class CommodityRegistry {

    private static volatile CommodityRegistry instance;
    // Multiple maps for fast lookups using different keys
    private final Map<Long, Commodity> lookupById = new HashMap<>();
    private final Map<String, Commodity> lookupByLocalisedName = new HashMap<>();
    private final Map<String, Commodity> lookupByNormalizedSystemName = new HashMap<>();

    public static synchronized CommodityRegistry getInstance() {
        if (instance == null) {
            synchronized (ConstructionSiteManager.class) {
                if (instance == null) {
                    instance = new CommodityRegistry();
                }
            }
        }
        return instance;
    }

    public void loadMarketData(Map<Long, Market> markets) throws JSONException {
        if (markets == null || markets.isEmpty()) {
            log.debug("Markets are empty, return without processing");
            return;
        }
        for (Market market: markets.values()){
            processCommodities(market.getItems());
        }
    }

    private void processCommodities(Map<Long, MarketItem> items) {
        if (items.isEmpty()) return;
        items.values()
                .forEach( marketItem -> {
                    Commodity commodity =  marketItem.getCommodity();
                    lookupById.put(commodity.getId(), commodity);//not null safe!
                    lookupByLocalisedName.put(commodity.getNameLocalised().toLowerCase(), commodity);
                    String normalizedKey = CommodityHelper.normalizeSystemName(commodity.getName());
                    lookupByNormalizedSystemName.put(normalizedKey, commodity);
                });
    }

    /**
     * Finds a commodity's canonical ID using the best available information.
     *
     * @param systemName    The raw system name (from "Type" or "Name" field).
     * @param localisedName The localised name (from "Type_Localised" or "Name_Localised"). Can be null.
     * @return The commodity ID, or a default/error ID if not found.
     */
    public long findCommodityId(String systemName, String localisedName) {
        // 1. Try to find by the most reliable key: the localised name.
        if (localisedName != null && !localisedName.isEmpty()) {
            Commodity found = lookupByLocalisedName.get(localisedName.toLowerCase());
            if (found != null) {
                return found.getId();
            }
        }

        // 2. Fallback: Try to find by the normalized system name.
        String normalizedKey = CommodityHelper.normalizeSystemName(systemName);
        Commodity found = lookupByNormalizedSystemName.get(normalizedKey);
        if (found != null) {
            return found.getId();
        }

        return -1; // Not found
    }

    public Commodity getCommodityById(long id) {
        return lookupById.get(id);
    }

    /**
     * Used commonly in the UI to get all commodities as DTOs.
     * This method converts all commodities to DTOs and returns them in a map.
     * @return Map[Long, CommodityDTO]
     */
    public Map<Long, CommodityDTO> getAllCommodityDTO() {
        return lookupById.values().stream()
                .map(CommodityMapper.INSTANCE::commodityToDTO)
                .collect(Collectors.toMap(CommodityDTO::getId, Function.identity()));
    }

    public String[] getAllNames() {
        return lookupByLocalisedName.keySet().toArray(new String[0]);
    }

}