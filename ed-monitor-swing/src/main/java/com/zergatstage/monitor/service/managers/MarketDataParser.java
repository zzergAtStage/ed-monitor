package com.zergatstage.monitor.service.managers;

import java.util.HashMap;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.zergatstage.domain.dictionary.Commodity;
import com.zergatstage.domain.makret.Market;
import com.zergatstage.domain.makret.MarketItem;


public class MarketDataParser {


    public Market parseMarketData(String jsonData) throws JSONException {


        JSONObject marketJSONObject = new JSONObject(jsonData);

        Long marketId = marketJSONObject.getLong("MarketID");
        String stationName = marketJSONObject.getString("StationName");
        String stationType = marketJSONObject.getString("StationType");
        String systemName = marketJSONObject.getString("StarSystem");

        Market market = Market.builder()
                .marketId(marketId)
                .stationName(stationName)
                .stationType(stationType)
                .systemName(systemName)
                .items(new HashMap<>())
                .build();

        JSONArray items = marketJSONObject.getJSONArray("Items");
        for (int i = 0; i < items.length(); i++) {


            JSONObject item = items.optJSONObject(i);

            long id = item.getLong("id");
            String name = item.getString("Name");
            String nameLocalised = item.getString("Name_Localised");
            String category = item.getString("Category");
            String categoryLocalised = item.getString("Category_Localised");

            int buyPrice = item.getInt("BuyPrice");
            int sellPrice = item.getInt("SellPrice");
            int stock = item.getInt("Stock");
            int demand = item.getInt("Demand");
            Commodity commodity = Commodity.builder()
                    .id(id)
                    .name(name)
                    .nameLocalised(nameLocalised)
                    .category(category)
                    .categoryLocalised(categoryLocalised)
                    .build();

            MarketItem marketItem = MarketItem.builder()
                    .commodity(commodity)
                    .market(market)
                    .buyPrice((int) buyPrice)
                    .sellPrice((int) sellPrice)
                    .stock((int) stock)
                    .demand((int) demand)
                    .build();
            market.addItem(marketItem);
        }

        return market;
    }
}
