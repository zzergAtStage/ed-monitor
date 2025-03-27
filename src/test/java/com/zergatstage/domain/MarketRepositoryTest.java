package com.zergatstage.domain;

import com.zergatstage.config.TestConfig;
import com.zergatstage.domain.dictionary.Commodity;
import com.zergatstage.domain.makret.Market;
import com.zergatstage.domain.makret.MarketItem;
import com.zergatstage.domain.makret.MarketRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DataJpaTest
@ContextConfiguration(classes = TestConfig.class)
@ActiveProfiles("test")
public class MarketRepositoryTest {

    private static final String TEST_DB_URL = "jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1"; // In-memory DB
    private static final String USER = "sa";
    private static final String PASSWORD = "";

    @Autowired
    private MarketRepository repository;

    @AfterEach
    public void cleanup() throws Exception {
        try (Connection connection = DriverManager.getConnection(TEST_DB_URL, USER, PASSWORD);
             Statement stmt = connection.createStatement()) {
            stmt.execute("DROP ALL OBJECTS");
        }
    }

    @Test
    @Order(1)
    public void testInsertMarket() {
        // Create market with one item
        Market market = new Market("M1", "Station Alpha", "Orbital", "System A", new HashMap<>());
        MarketItem item = createItem(market, "I1", "Selenium", "Metals");
        market.addItem(item);

        // Save to repository
        repository.save(market);

        // Retrieve and verify
        Market retrieved = repository.findById("M1").orElseThrow();
        assertNotNull(retrieved);
        assertEquals("Station Alpha", retrieved.getStationName());
        assertTrue(retrieved.getItems().containsKey("Selenium"), "Market should contain Selenium item");
        assertEquals(1, retrieved.getItems().size(), "Market should have exactly one item");
    }

    @Test
    @Order(2)
    public void testUpdateMarket() {
        // Create initial market
        Market market = new Market("M2", "Station Beta", "Outpost", "System B", new HashMap<>());
        MarketItem item = createItem(market, "I2", "Copper", "Metals", 100, 200, 50, 75);
        market.addItem(item);
        repository.saveAndFlush(market);

        // Create updated market with same ID
        Market updatedMarket = new Market("M2", "Station Beta Updated", "Outpost", "System B", new HashMap<>());
        MarketItem updatedItem = createItem(updatedMarket, "I2", "Copper", "Metals", 150, 300, 55, 80);
        updatedMarket.addItem(updatedItem);
        repository.saveAndFlush(updatedMarket);

        // Retrieve and verify
        Market retrieved = repository.findById("M2").orElseThrow();
        assertNotNull(retrieved);
        assertEquals("Station Beta Updated", retrieved.getStationName());

        MarketItem retrievedItem = retrieved.getItems().get("Copper");
        assertNotNull(retrievedItem);
        assertEquals(55, retrievedItem.getBuyPrice());
        assertEquals(300, retrievedItem.getStock());
    }

    @Test
    @Order(3)
    public void testGetAllMarkets() {
        // Create two markets
        Market market1 = new Market("M3", "Station Gamma", "Orbital", "System C", new HashMap<>());
        market1.addItem(createItem(market1, "I3", "Aluminum", "Metals"));
        repository.save(market1);

        Market market2 = new Market("M4", "Station Delta", "Outpost", "System D", new HashMap<>());
        market2.addItem(createItem(market2, "I4", "Biowaste", "Waste"));
        repository.save(market2);

        // Verify both markets are retrieved
        List<Market> markets = repository.findAll();
        assertEquals(2, markets.size());
    }

    @Test
    @Order(4)
    public void testGetAllItems_WhenAddMarket() {
        // Create market with two items
        Market market1 = new Market("M5", "Station Gamma", "Orbital", "System C", new HashMap<>());
        market1.addItem(createItem(market1, "I3", "Aluminum", "Metals"));
        market1.addItem(createItem(market1, "I5", "Selenium", "Metals"));
        repository.save(market1);

        // Verify market has two items
        Market retrieved = repository.findById("M5").orElseThrow();
        Map<String, MarketItem> marketItems = retrieved.getItems();
        assertEquals(2, marketItems.size());
        assertTrue(marketItems.containsKey("Aluminum"));
        assertTrue(marketItems.containsKey("Selenium"));
    }

    @Test
    @Order(5)
    public void testUpdateMarketItem_WhenMarketUpdated() {
        // Create initial market
        Market market1 = new Market("M6", "Station Epsilon", "Orbital", "System E", new HashMap<>());
        market1.addItem(createItem(market1, "I6", "Platinum", "Metals", 1000, 500, 900, 50));
        repository.saveAndFlush(market1);

        // Retrieve the saved market
        Market retrieved1 = repository.findById("M6").orElseThrow();
        MarketItem originalItem = retrieved1.getItems().get("Platinum");
        int originalStock = originalItem.getStock();

        // Update the market with new item values
        Market marketUpdated = new Market("M6", "Station Epsilon", "Orbital", "System E", new HashMap<>());
        marketUpdated.addItem(createItem(marketUpdated, "I6", "Platinum", "Metals", 1200, 300, 1100, 60));
        repository.saveAndFlush(marketUpdated);

        // Verify the item was updated
        Market retrievedUpdated = repository.findById("M6").orElseThrow();
        MarketItem updatedItem = retrievedUpdated.getItems().get("Platinum");

        assertEquals(300, updatedItem.getStock());
        assertEquals(1100, updatedItem.getSellPrice());
        assertNotEquals(originalStock, updatedItem.getStock());
    }

    @Test
    @Order(6)
    public void testMultipleMarketsIndependence() {
        // Create two different markets
        Market market1 = new Market("M7", "Station Zeta", "Orbital", "System F", new HashMap<>());
        market1.addItem(createItem(market1, "I7", "Gold", "Metals", 5000, 200, 4800, 100));
        repository.saveAndFlush(market1);

        Market market2 = new Market("M8", "Station Theta", "Outpost", "System G", new HashMap<>());
        market2.addItem(createItem(market2, "I8", "Silver", "Metals", 2500, 400, 2400, 80));
        repository.saveAndFlush(market2);

        // Update one market
        Market updatedMarket1 = new Market("M7", "Station Zeta Updated", "Orbital", "System F", new HashMap<>());
        updatedMarket1.addItem(createItem(updatedMarket1, "I7", "Gold", "Metals", 5200, 150, 5000, 110));
        repository.saveAndFlush(updatedMarket1);

        // Verify only the first market was updated
        Market retrievedMarket1 = repository.findById("M7").orElseThrow();
        Market retrievedMarket2 = repository.findById("M8").orElseThrow();

        assertEquals("Station Zeta Updated", retrievedMarket1.getStationName());
        assertEquals("Station Theta", retrievedMarket2.getStationName());

        assertEquals(150, retrievedMarket1.getItems().get("Gold").getStock());
        assertEquals(400, retrievedMarket2.getItems().get("Silver").getStock());
    }

    /**
     * Creates a market item with controlled values for testing
     */
    private static MarketItem createItem(Market market, String id, String name, String category) {
        // Default values for predictable tests
        return createItem(market, id, name, category, 1000, 500, 900, 50);
    }

    /**
     * Creates a market item with specified values for testing
     */
    private static MarketItem createItem(Market market, String id, String name, String category,
                                         int sellPrice, int stock, int buyPrice, int demand) {
        return new MarketItem(
                UUID.randomUUID(),
                new Commodity(id, name, category),
                market,
                stock,
                sellPrice,
                buyPrice,
                demand
        );
    }
}