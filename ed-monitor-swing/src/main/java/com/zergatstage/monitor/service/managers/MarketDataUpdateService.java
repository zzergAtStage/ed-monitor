package com.zergatstage.monitor.service.managers;

import com.zergatstage.domain.makret.Market;
import com.zergatstage.domain.makret.MarketItem;
import com.zergatstage.monitor.service.BaseManager;
import com.zergatstage.monitor.http.MarketDtoMapper;
import com.zergatstage.monitor.service.MarketDataHttpService;
import com.zergatstage.monitor.service.CommodityRegistry;
import lombok.extern.log4j.Log4j2;
import org.json.JSONException;

import java.util.LinkedHashMap;

/**
 * Service responsible for handling market data updates.
 * <p>
 * This service parses incoming JSON data using MarketDataParser and updates the MarketRepository.
 * It is designed to be used either as an event listener for market data update events or as a scheduled task.
 * </p>
 */

@Log4j2
public class MarketDataUpdateService extends BaseManager{

    private final CommodityRegistry commodityRegistry;
    private final MarketDataParser marketDataParser;
    private final LinkedHashMap<Long, Market> marketCache = new LinkedHashMap<>();
    private MarketDataHttpService httpService;

    /**
     * Constructs the MarketDataUpdateService with required dependencies.
     *
     * @param commodityRegistry the repository used to persist market and Commodity data.
     * @param marketDataParser  the parser for converting JSON market data into Market objects.
     */
    public MarketDataUpdateService(CommodityRegistry commodityRegistry, MarketDataParser marketDataParser) {
        this.commodityRegistry = commodityRegistry;
        this.marketDataParser = marketDataParser;
    }

    public void setHttpService(MarketDataHttpService httpService) {
        this.httpService = httpService;
    }

    /**
     * Loads markets from the server (if configured) and populates local cache.
     */
    public void refreshFromServer() {
        if (httpService == null) return;
        try {
            var dtos = httpService.getMarkets();
            if (dtos == null || dtos.isEmpty()) return;
            this.marketCache.clear();
            for (var dto : dtos) {
                Market m = MarketDtoMapper.fromDto(dto);
                this.marketCache.put(m.getMarketId(), m);
            }
            commodityRegistry.loadMarketData(marketCache);
            notifyListeners();
            log.info("Market cache refreshed from server: {} entries", marketCache.size());
        } catch (Exception e) {
            log.warn("Refresh from server failed: {}", e.getMessage());
        }
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
            Market parsedMarket = marketDataParser.parseMarketData(jsonData);
            //JSONObject json = new JSONObject(new JSONTokener(jsonData));//TODO: it doubles the work?
            if (parsedMarket.getItems().isEmpty()) {
                // Log a message when no data is found
                log.warn("No market data found from file update. (It's okay)");
                return;
            }
            marketCache.putFirst(parsedMarket.getMarketId(), parsedMarket);
            commodityRegistry.loadMarketData(marketCache);
            try {
                if (httpService != null) {
                    httpService.postMarkets(java.util.List.of(MarketDtoMapper.toDto(parsedMarket)));
                }
            } catch (Exception e) {
                log.warn("Server sync failed: {}", e.getMessage());
            }
            notifyListeners();
            log.info("Market data updated successfully.");
        } catch (JSONException e) {
            // Log the parsing error; consider using a logging framework in production
            log.error("Error parsing market data: {}", e.getMessage());
        }
    }

    /**
     * Returns the current stock of the given commodity at the market identified by siteId.
     * If parsing fails, market or commodity is missing, returns 0.
     */
    public int getStockForSite(long materialId) {
        Market market = marketCache.sequencedValues().getFirst();
        if (market == null || market.getItems() == null) {
            return 0;
        }

        return market.getItems().get(materialId).getStock();
    }

    /**
     * Returns the current stock of the given commodity at the market identified by siteId.
     * If parsing fails, market or commodity is missing, returns 0.
     *
     * @param materialId the name of the commodity to check stock for
     * @param siteId       the ID of the site to check stock at
     * @return the stock of the commodity at the specified site, or 0 if not found
     */
    public int getStockForSite(long materialId, long siteId) {
        Market market = marketCache.get(siteId);
        if (market == null || market.getItems() == null) {
            return 0;
        }
        //if we inspect our carrier, some of the materials are not in list
        return market.getItems().getOrDefault(materialId, new MarketItem()).getStock() ;
    }

    public Market[] getAllMarkets() {
        return this.marketCache.values().toArray(new Market[0]);
    }
}
