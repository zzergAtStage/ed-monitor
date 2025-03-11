package com.zergatstage.domain.makret;

import com.zergatstage.domain.Commodity;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

// Service for parsing market data
public class MarketDataParser {
    public static List<Market> parseMarketData(String jsonData) throws ParseException {
        List<Market> markets = new ArrayList<>();

        JSONParser parser = new JSONParser();
        JSONObject jsonObject = (JSONObject) parser.parse(jsonData);

        String marketId = jsonObject.get("MarketID").toString();
        String stationName = (String) jsonObject.get("StationName");
        String stationType = (String) jsonObject.get("StationType");
        String systemName = (String) jsonObject.get("StarSystem");

        Market market = Market.builder()
                .marketId(marketId)
                .stationName(stationName)
                .stationType(stationType)
                .systemName(systemName)
                .build();

        JSONArray items = (JSONArray) jsonObject.get("Items");
        for (Object itemObj : items) {
            JSONObject item = (JSONObject) itemObj;

            String id = item.get("id").toString();
            String name = (String) item.get("Name_Localised");
            String category = (String) item.get("Category_Localised");

            long buyPrice = (long) item.get("BuyPrice");
            long sellPrice = (long) item.get("SellPrice");
            long stock = (long) item.get("Stock");
            long demand = (long) item.get("Demand");
            MarketItem marketItem = MarketItem.builder()
                    .commodity(Commodity.builder()
                            .id(id)
                            .name(name)
                            .category(category)
                            .build())
                    .buyPrice((int) buyPrice)
                    .sellPrice((int) sellPrice)
                    .stock((int) stock)
                    .demand((int) demand)
                    .build();
            market.addItem(marketItem);
        }

        markets.add(market);
        return markets;
    }

    public static List<Market> parseMarketDataFromFile(String filePath) {
        try {
            String content = new String(Files.readAllBytes(Paths.get(filePath)));
            return parseMarketData(content);
        } catch (Exception e) {
            System.err.println("Error parsing market data: " + e.getMessage());
            e.printStackTrace();
            return new ArrayList<>();
        }
    }
}
