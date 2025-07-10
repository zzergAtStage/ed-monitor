package com.zergatstage.domain.makret;

import com.zergatstage.domain.makret.Market;
import com.zergatstage.domain.makret.MarketDataParser;
import com.zergatstage.domain.makret.MarketDataUpdateEvent;
import com.zergatstage.domain.makret.MarketRepository;
import lombok.extern.log4j.Log4j2;
import org.json.simple.parser.ParseException;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service responsible for handling market data updates.
 * <p>
 * This service parses incoming JSON data using MarketDataParser and updates the MarketRepository.
 * It is designed to be used either as an event listener for market data update events or as a scheduled task.
 * </p>
 */
@Service
@Log4j2
public class MarketDataUpdateService {

    private final MarketRepository repository;
    private final MarketDataParser marketDataParser;

    /**
     * Constructs the MarketDataUpdateService with required dependencies.
     *
     * @param repository      the repository used to persist market data.
     * @param marketDataParser the parser for converting JSON market data into Market objects.
     */
    public MarketDataUpdateService(MarketRepository repository, MarketDataParser marketDataParser) {
        this.repository = repository;
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
    @EventListener
    public void onMarketDataUpdate(MarketDataUpdateEvent event) {
        try {
            String jsonData = event.getJsonData();
            // Parse market data from JSON
            List<Market> parsedMarkets = marketDataParser.parseMarketData(jsonData);
            if (parsedMarkets.isEmpty()) {
                // Log a message when no data is found
                log.warn("No market data found from file update. (It's okay)");
                return;
            }

            // Save or update each parsed market in the repository
            parsedMarkets.forEach(repository::save);
            log.info("Market data updated successfully.");
        } catch (ParseException e) {
            // Log the parsing error; consider using a logging framework in production
            log.error("Error parsing market data: {}", e.getMessage());
        }
    }

    /**
     * (Optional) Scheduled method to refresh market data periodically.
     * <p>
     * Uncomment and configure the scheduling annotation if a periodic update is desired.
     * </p>
     */
    // @Scheduled(fixedDelay = 60000)
    // public void scheduledMarketDataUpdate() {
    //     // Retrieve JSON data from external source and update repository
    // }
}
