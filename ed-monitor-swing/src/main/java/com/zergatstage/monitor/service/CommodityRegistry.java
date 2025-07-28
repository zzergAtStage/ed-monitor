package com.zergatstage.monitor.service;

import com.zergatstage.domain.dictionary.Commodity;
import com.zergatstage.dto.CommodityDTO;
import com.zergatstage.dto.CommodityMapper;
import com.zergatstage.dto.CommodityMapperImpl;
import com.zergatstage.tools.CommodityHelper;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class CommodityRegistry {

    private static volatile  CommodityRegistry instance;
    // Multiple maps for fast lookups using different keys
    private final Map<Long, Commodity> lookupById = new HashMap<>();
    private final Map<String, Commodity> lookupByLocalisedName = new HashMap<>();
    private final Map<String, Commodity> lookupByNormalizedSystemName = new HashMap<>();

    public static synchronized CommodityRegistry getInstance() {
        if (instance == null) {
            synchronized(ConstructionSiteManager.class) {
                if (instance == null) {
                    instance = new CommodityRegistry();
                }
            }
        }
        return instance;
    }

    public void loadMarketData(JSONObject marketJson) throws JSONException {
        JSONArray items = marketJson.getJSONArray("Items");
        for (int i = 0; i < items.length(); i++) {
            JSONObject itemJson = items.getJSONObject(i);

            // Create your Commodity object
            Commodity commodity = Commodity.builder()
                    .id(itemJson.getLong("id"))
                    .name( itemJson.getString("Name"))
                    .nameLocalised(itemJson.getString("Name_Localised"))
                    .category(itemJson.getString("Category"))
                    .categoryLocalised(itemJson.getString("Category_Localised"))
                    .build();

            // Populate all lookup maps
            lookupById.put(commodity.getId(), commodity);
            lookupByLocalisedName.put(commodity.getNameLocalised().toLowerCase(), commodity);

            String normalizedKey = CommodityHelper.normalizeSystemName(commodity.getName());
            lookupByNormalizedSystemName.put(normalizedKey, commodity);
        }
    }

    /**
     * Finds a commodity's canonical ID using the best available information.
     * @param systemName The raw system name (from "Type" or "Name" field).
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

    public Commodity getCommodityById(long id){
        return lookupById.get(id);
    }

    public Map<Long, CommodityDTO> getAllCommodityDTO(){
        return  lookupById.values().stream()
                .map(CommodityMapper.INSTANCE::commodityToDTO)
                .collect(Collectors.toMap(CommodityDTO::getId, Function.identity()));
    }
}