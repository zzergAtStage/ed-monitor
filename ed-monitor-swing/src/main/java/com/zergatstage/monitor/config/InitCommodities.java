package com.zergatstage.monitor.config;

import com.zergatstage.domain.dictionary.Commodity;
import com.zergatstage.domain.makret.Market;
import com.zergatstage.monitor.service.managers.MarketDataParser;
import com.zergatstage.domain.makret.MarketItem;
import lombok.extern.log4j.Log4j2;
import org.json.JSONException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Log4j2
public class InitCommodities {
    List<Commodity> commodities = new ArrayList<>();

    public InitCommodities(MarketDataParser marketDataParser) {
        Path marketFile = Paths.get("Market.json");
        try {
            String content = new String(Files.readAllBytes(marketFile));
            Market parsedMarket = marketDataParser.parseMarketData(content);
            fetchCommodities(parsedMarket);
        } catch (IOException e) {
            log.warn("Error reading market file: {}", e.getMessage());
        } catch (JSONException e) {
            log.error("Error commodities processing in Market.json file: {}", e.getMessage());
        }
    }

    private void fetchCommodities(Market parsedMarket) {
        Map<Long, MarketItem> marketItems = parsedMarket.getItems();
        marketItems.forEach((key,item)  -> Commodity.builder()
                .id(item.getId())
                .name(item.getCommodity().getName())
        );
    }
}
