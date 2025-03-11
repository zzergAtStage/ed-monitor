package com.zergatstage.domain;

import com.zergatstage.domain.makret.Market;
import com.zergatstage.domain.makret.MarketItem;
import com.zergatstage.domain.makret.MarketRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)

@DataJpaTest
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
        Market market = new Market("M1", "Station Alpha", "Orbital", "System A", new HashMap<>());
        MarketItem item = new MarketItem(new Commodity("I1", "Steel", "Metals"), market
                , 100, 90, 1000, 50);
        market.addItem(item);

        repository.saveAndFlush(market);

        Market retrieved = repository.findById("M1").orElseThrow();
        assertNotNull(retrieved);
        assertEquals("Station Alpha", retrieved.getStationName());
        assertTrue(retrieved.getItems().containsKey("Steel"));
    }

    @Test
    @Order(2)
    public void testUpdateMarket() {
        Market market = new Market("M2", "Station Beta", "Outpost", "System B"
                , Map.of());
        MarketItem item = new MarketItem(new Commodity("I2", "Copper", "Metals"), market
                , 50, 45, 500, 20);
        market.addItem(item);
        repository.saveAndFlush(market);

        Market updatedMarket = new Market("M2", "Station Beta Updated", "Outpost", "System B",  new HashMap<>());
        MarketItem updatedItem = new MarketItem(new Commodity("I2", "Copper", "Metals"), market
                , 55, 50, 600, 25);
        updatedMarket.addItem(updatedItem);
        repository.save(updatedMarket);

        Market retrieved = repository.findById("M2").orElseThrow();
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
        Market market1 = new Market("M3", "Station Gamma", "Orbital", "System C",  new HashMap<>());
        market1.addItem(new MarketItem(new Commodity("I3", "Aluminum", "Metals"), market1
                , 80, 75, 800, 30));
        repository.save(market1);

        Market market2 = new Market("M4", "Station Delta", "Outpost", "System D",  new HashMap<>());
        market2.addItem(new MarketItem(new Commodity("I4", "Biowaste", "Waste"), market2, 30, 25, 300, 10));
        repository.save(market2);

        List<Market> markets = repository.findAll();
        assertEquals(2, markets.size());
    }

    @Test
    @Order(4)
    public void testGetAllItems_WhenAddMarket() {
        Market market1 = new Market("M3", "Station Gamma", "Orbital", "System C",  new HashMap<>());
        market1.addItem(new MarketItem(new Commodity("I3", "Aluminum", "Metals"),market1, 80, 75, 800, 30));
        market1.addItem(new MarketItem(new Commodity("I5", "Selenium", "Metals"),market1, 95, 75, 800, 30));
        repository.save(market1);

        Market market = repository.findById("M3").orElseThrow();
        Map<String, MarketItem> marketItems = market.getItems();
        assertEquals(2, marketItems.size());
    }

    @Test
    @Order(5)
    public void testUpdateMarketItem_WhenMarketUpdated_ItemPersists() {
        Market market1 = new Market("M3", "Station Gamma", "Orbital", "System C",  new HashMap<>());
        market1.addItem(new MarketItem( new Commodity("I3", "Aluminum", "Metals"),market1, 80, 75, 800, 30));
        MarketItem item = new MarketItem( new Commodity("I5", "Selenium", "Metals"),market1, 95, 75, 800, 30);
        market1.addItem(item);

        Market market2 = new Market("M4", "Station Gamma", "Orbital", "System C",  new HashMap<>());
        market2.addItem(new MarketItem( new Commodity("I3", "Aluminum", "Metals"),market2, 82, 75, 800, 30));
        market2.addItem(new MarketItem( new Commodity("I5", "Selenium", "Metals"),market2 , 97, 75, 800, 30));

        Market marketUpdated = new Market("M3", "Station Gamma", "Orbital", "System C",  new HashMap<>());
        marketUpdated.addItem(new MarketItem( new Commodity("I3", "Aluminum", "Metals"),marketUpdated, 84, 75, 800, 30));
        marketUpdated.addItem(new MarketItem( new Commodity("I5", "Selenium", "Metals"),marketUpdated, 99, 75, 800, 30));

        repository.save(market1);
        repository.save(market2);

        repository.saveAndFlush(marketUpdated);
        assertEquals(market2, repository.findById("M4"));
        assertEquals(market2.getItems(), repository.findById("M4").orElseThrow().getItems());
        MarketItem testItem = repository.findById("M3").orElseThrow().getItem("Selenium");
        assertNotEquals(market1.getItem("Selenium"), testItem);

    }

}
