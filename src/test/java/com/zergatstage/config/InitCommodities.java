package com.zergatstage.config;

import com.zergatstage.domain.dictionary.Commodity;
import com.zergatstage.domain.makret.Market;
import com.zergatstage.domain.makret.MarketDataParser;
import com.zergatstage.domain.makret.MarketItem;
import lombok.extern.log4j.Log4j2;
import org.json.JSONObject;
import org.json.simple.parser.ParseException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Log4j2
public class InitCommodities {
    private final Path marketFile;
    private final MarketDataParser marketDataParser;
    List<Commodity> commodities = new ArrayList<>();

    public InitCommodities(MarketDataParser marketDataParser) {
        this.marketDataParser = marketDataParser;
        this.marketFile = Paths.get("Market.json");
        try {
            String content = new String(Files.readAllBytes(marketFile));
            List<Market> parsedMarkets = marketDataParser.parseMarketData(content);
            fetchCommodities(parsedMarkets);
        } catch (IOException e) {
            log.warn("Error reading market file: {}", e.getMessage());
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }
    }

    private void fetchCommodities(List<Market> parsedMarkets) {
        Map<String, MarketItem> marketItems = parsedMarkets.stream().findFirst().orElseThrow().getItems();
        marketItems.forEach((key,item)  -> Commodity.builder()
                .id(item.getId().toString())
                .name(item.getCommodity().getName())
        );
    }
}
