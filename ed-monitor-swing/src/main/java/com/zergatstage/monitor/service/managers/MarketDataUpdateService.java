package com.zergatstage.monitor.service.managers;

import com.zergatstage.domain.makret.Market;
import com.zergatstage.monitor.service.CommodityRegistry;
import lombok.extern.log4j.Log4j2;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.util.List;

/**
 * Service responsible for handling market data updates.
 * <p>
 * This service parses incoming JSON data using MarketDataParser and updates the MarketRepository.
 * It is designed to be used either as an event listener for market data update events or as a scheduled task.
 * </p>
 */

@Log4j2
public class MarketDataUpdateService {

    private final CommodityRegistry commodityRegistry;
    private final MarketDataParser marketDataParser;

    /**
     * Constructs the MarketDataUpdateService with required dependencies.
     *
     * @param commodityRegistry     the repository used to persist market and Commodity data.
     * @param marketDataParser the parser for converting JSON market data into Market objects.
     */
    public MarketDataUpdateService(CommodityRegistry commodityRegistry, MarketDataParser marketDataParser) {
        this.commodityRegistry = commodityRegistry;
        this.marketDataParser = marketDataParser;
    }

    /**
     * Handles market data update events by parsing the JSON data and updating the repository.
     * <p>
     * This method follows the Single Responsibility Principle by only focusing on data ingestion.
     * </p>
     *
     * @param event the market data update event containing JSON data.
     */

    public void onMarketDataUpdate(MarketDataUpdateEvent event) {
        try {
            String jsonData = event.getJsonData();
            // Parse market data from JSON
            List<Market> parsedMarkets = marketDataParser.parseMarketData(jsonData);
            JSONObject json = new JSONObject(new JSONTokener(jsonData));
            commodityRegistry.loadMarketData(json);
            if (parsedMarkets.isEmpty()) {
                // Log a message when no data is found
                log.warn("No market data found from file update. (It's okay)");
                return;
            }
            log.info("Market data updated successfully.");
        } catch (JSONException e) {
            // Log the parsing error; consider using a logging framework in production
            log.error("Error parsing market data: {}", e.getMessage());
        }
    }
}
