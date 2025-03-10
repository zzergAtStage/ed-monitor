package com.zergatstage.domain;

import org.h2.tools.Server;
import org.junit.jupiter.api.*;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class MarketRepositoryTest {

    private static final String TEST_DB_URL = "jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1"; // In-memory DB
    private static final String USER = "sa";
    private static final String PASSWORD = "";
    private MarketRepository repository;

    @BeforeEach
    public void setup() throws Exception {
        repository = new MarketRepository(TEST_DB_URL, USER, PASSWORD);
    }

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
        Market market = new Market("M1", "Station Alpha", "Orbital", "System A");
        MarketItem item = new MarketItem("I1", "Steel", "Metals", 100, 90, 1000, 50);
        market.addItem(item);

        repository.saveOrUpdateMarket(market);

        Market retrieved = repository.getMarketById("M1");
        assertNotNull(retrieved);
        assertEquals("Station Alpha", retrieved.getStationName());
        assertTrue(retrieved.getItems().containsKey("Steel"));
    }

    @Test
    @Order(2)
    public void testUpdateMarket() {
        Market market = new Market("M2", "Station Beta", "Outpost", "System B");
        MarketItem item = new MarketItem("I2", "Copper", "Metals", 50, 45, 500, 20);
        market.addItem(item);
        repository.saveOrUpdateMarket(market);

        Market updatedMarket = new Market("M2", "Station Beta Updated", "Outpost", "System B");
        MarketItem updatedItem = new MarketItem("I2", "Copper", "Metals", 55, 50, 600, 25);
        updatedMarket.addItem(updatedItem);
        repository.saveOrUpdateMarket(updatedMarket);

        Market retrieved = repository.getMarketById("M2");
        assertNotNull(retrieved);
        assertEquals("Station Beta Updated", retrieved.getStationName());
        MarketItem retrievedItem = retrieved.getItems().get("Copper");
        assertNotNull(retrievedItem);
        assertEquals(55, retrievedItem.getBuyPrice());
        assertEquals(600, retrievedItem.getStock());
    }

    @Test
    @Order(3)
    public void testGetAllMarkets() {
        Market market1 = new Market("M3", "Station Gamma", "Orbital", "System C");
        market1.addItem(new MarketItem("I3", "Aluminum", "Metals", 80, 75, 800, 30));
        repository.saveOrUpdateMarket(market1);

        Market market2 = new Market("M4", "Station Delta", "Outpost", "System D");
        market2.addItem(new MarketItem("I4", "Biowaste", "Waste", 30, 25, 300, 10));
        repository.saveOrUpdateMarket(market2);

        List<Market> markets = repository.getAllMarkets();
        assertEquals(2, markets.size());
    }

    @Test
    @Order(4)
    public void testGetAllItems_WhenAddMarket() {
        Market market1 = new Market("M3", "Station Gamma", "Orbital", "System C");
        market1.addItem(new MarketItem("I3", "Aluminum", "Metals", 80, 75, 800, 30));
        market1.addItem(new MarketItem("I5", "Selenium", "Metals", 95, 75, 800, 30));
        repository.saveOrUpdateMarket(market1);

        Market market = repository.getMarketById("M3");
        Map<String, MarketItem> marketItems = market.getItems();
        assertEquals(2, marketItems.size());
    }

    @Test
    @Order(5)
    public void testUpdateMarketItem_WhenMarketUpdated_ItemPersists() {
        Market market1 = new Market("M3", "Station Gamma", "Orbital", "System C");
        market1.addItem(new MarketItem("I3", "Aluminum", "Metals", 80, 75, 800, 30));
        MarketItem item = new MarketItem("I5", "Selenium", "Metals", 95, 75, 800, 30);
        market1.addItem(item);

        Market market2 = new Market("M4", "Station Gamma", "Orbital", "System C");
        market2.addItem(new MarketItem("I3", "Aluminum", "Metals", 82, 75, 800, 30));
        market2.addItem(new MarketItem("I5", "Selenium", "Metals", 97, 75, 800, 30));

        Market marketUpdated = new Market("M3", "Station Gamma", "Orbital", "System C");
        marketUpdated.addItem(new MarketItem("I3", "Aluminum", "Metals", 84, 75, 800, 30));
        marketUpdated.addItem(new MarketItem("I5", "Selenium", "Metals", 99, 75, 800, 30));

        repository.saveOrUpdateMarket(market1);
        repository.saveOrUpdateMarket(market2);

        repository.saveOrUpdateMarket(marketUpdated);
        assertEquals(market2, repository.getMarketById("M4"));
        assertEquals(market2.getItems(), repository.getMarketById("M4").getItems());
        MarketItem testItem = repository.getMarketById("M3").getItem("Selenium");
        assertNotEquals(market1.getItem("Selenium"), testItem);

    }

}
