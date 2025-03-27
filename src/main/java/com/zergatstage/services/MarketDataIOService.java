package com.zergatstage.services;

import com.zergatstage.domain.makret.MarketDataUpdateEvent;
import java.io.IOException;
import java.nio.file.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Service that monitors the Market.json file for changes.
 * When a change is detected, it publishes a MarketDataUpdateEvent.
 */
@Service
public class MarketDataIOService {

    private static final Logger logger = LoggerFactory.getLogger(MarketDataIOService.class);

    private final Path marketFile;
    private String lastContent;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Constructs the MarketDataIOService.
     * The file path is constructed based on the user's home directory.
     *
     * @param eventPublisher the Spring event publisher used to publish market update events
     */
    public MarketDataIOService(ApplicationEventPublisher eventPublisher) {
        this.marketFile = Paths.get(
                System.getProperty("user.home"),
                "Saved Games",
                "Frontier Developments",
                "Elite Dangerous",
                "Market.json"
        );
        this.eventPublisher = eventPublisher;
        this.lastContent = "";
    }

    /**
     * Periodically checks the market file for changes.
     * This method is automatically invoked by Spring every 1 second.
     */
    @Scheduled(fixedDelay = 1000)
    public void checkMarketFile() {
        try {
            // Read the current content of the market file.
            String content = new String(Files.readAllBytes(marketFile));
            // If content has changed, update the lastContent and publish an event.
            if (!content.equals(lastContent)) {
                lastContent = content;
                eventPublisher.publishEvent(new MarketDataUpdateEvent(this, content));
                logger.info("Published market data update event.");
            }
        } catch (IOException e) {
            logger.error("Error reading market file: {}", e.getMessage(), e);
        }
    }
}
