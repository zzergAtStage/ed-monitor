package com.zergatstage.domain;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The MarketRepository class is responsible for persisting market data into an H2 database.
 * The connection parameters are injected via the constructor to allow flexible configuration.
 */
public class MarketRepository {

    private final String dbUrl;
    private final String user;
    private final String password;

    /**
     * Constructs a MarketRepository with the provided database connection parameters.
     *
     * @param dbUrl the JDBC URL for the H2 database.
     * @param user the username for the database.
     * @param password the password for the database.
     */
    public MarketRepository(String dbUrl, String user, String password) {
        this.dbUrl = dbUrl;
        this.user = user;
        this.password = password;
        initializeDatabase();
    }

    /**
     * Default constructor that uses production settings.
     */
    public MarketRepository() {
        // Production values can be loaded from configuration
        this("jdbc:h2:file:./data/marketdb", "sa", "");
    }

    /**
     * Initializes the database by creating the necessary tables if they do not exist.
     */
    private void initializeDatabase() {
        try (Connection connection = getConnection();
             Statement stmt = connection.createStatement()) {
            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS market (" +
                            "market_id VARCHAR(255) PRIMARY KEY, " +
                            "station_name VARCHAR(255), " +
                            "station_type VARCHAR(255), " +
                            "system_name VARCHAR(255)" +
                            ")"
            );
            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS market_item (" +
                            "id VARCHAR(255) PRIMARY KEY, " +
                            "market_id VARCHAR(255), " +
                            "name VARCHAR(255), " +
                            "category VARCHAR(255), " +
                            "buy_price INT, " +
                            "sell_price INT, " +
                            "stock INT, " +
                            "demand INT, " +
                            "FOREIGN KEY (market_id) REFERENCES market(market_id)" +
                            ")"
            );
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * Returns a connection to the H2 database.
     *
     * @return a Connection object.
     * @throws SQLException if a database access error occurs.
     */
    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(dbUrl, user, password);
    }

    public void saveOrUpdateMarket(Market market) {
        try (Connection connection = getConnection()) {
            connection.setAutoCommit(false);
            if (marketExists(connection, market.getMarketId())) {
                updateMarket(connection, market);
            } else {
                insertMarket(connection, market);
            }
            for (MarketItem item : market.getItems().values()) {
                if (marketItemExists(connection, item.getId(), market.getMarketId())) {
                    updateMarketItem(connection, item, market.getMarketId());
                } else {
                    insertMarketItem(connection, item, market.getMarketId());
                }
            }
            connection.commit();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private boolean marketExists(Connection connection, String marketId) throws SQLException {
        String query = "SELECT COUNT(*) FROM market WHERE market_id = ?";
        try (PreparedStatement ps = connection.prepareStatement(query)) {
            ps.setString(1, marketId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    private void insertMarket(Connection connection, Market market) throws SQLException {
        String sql = "INSERT INTO market (market_id, station_name, station_type, system_name) VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, market.getMarketId());
            ps.setString(2, market.getStationName());
            ps.setString(3, market.getStationType());
            ps.setString(4, market.getSystemName());
            ps.executeUpdate();
        }
    }

    private void updateMarket(Connection connection, Market market) throws SQLException {
        String sql = "UPDATE market SET station_name = ?, station_type = ?, system_name = ? WHERE market_id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, market.getStationName());
            ps.setString(2, market.getStationType());
            ps.setString(3, market.getSystemName());
            ps.setString(4, market.getMarketId());
            ps.executeUpdate();
            Map<String, MarketItem> items = market.getItems();
            if (!items.isEmpty()) {
                items.forEach( (e, marketItem) ->
                {
                    try {
                        updateMarketItem(connection, marketItem, market.getMarketId());
                    } catch (SQLException ex) {
                        throw new RuntimeException(ex);
                    }

                });
            }
        }
    }

    private boolean marketItemExists(Connection connection, String itemId, String markerId) throws SQLException {
        String query = "SELECT COUNT(*) FROM market_item WHERE id = ? AND market_id = ?";
        try (PreparedStatement ps = connection.prepareStatement(query)) {
            ps.setString(1, itemId);
            ps.setString(2, markerId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    private void insertMarketItem(Connection connection, MarketItem item, String marketId) throws SQLException {
        String sql = "INSERT INTO market_item (id, market_id, name, category, buy_price, sell_price, stock, demand) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, item.getId());
            ps.setString(2, marketId);
            ps.setString(3, item.getName());
            ps.setString(4, item.getCategory());
            ps.setInt(5, item.getBuyPrice());
            ps.setInt(6, item.getSellPrice());
            ps.setInt(7, item.getStock());
            ps.setInt(8, item.getDemand());
            ps.executeUpdate();
        }
    }

    private void updateMarketItem(Connection connection, MarketItem item, String marketId) throws SQLException {
        String sql = "UPDATE market_item SET  name = ?, category = ?, buy_price = ?, sell_price = ?, stock = ?, demand = ? " +
                "WHERE id = ? AND market_id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, item.getName());
            ps.setString(2, item.getCategory());
            ps.setInt(3, item.getBuyPrice());
            ps.setInt(4, item.getSellPrice());
            ps.setInt(5, item.getStock());
            ps.setInt(6, item.getDemand());
            ps.setString(7, item.getId());
            ps.setString(8, marketId);
            ps.executeUpdate();
        }
    }

    public Market getMarketById(String marketId) {
        try (Connection connection = getConnection()) {
            String sql = "SELECT * FROM market WHERE market_id = ?";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, marketId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        String stationName = rs.getString("station_name");
                        String stationType = rs.getString("station_type");
                        String systemName = rs.getString("system_name");
                        Market market = new Market(marketId, stationName, stationType, systemName);
                        String itemSql = "SELECT * FROM market_item WHERE market_id = ?";
                        try (PreparedStatement itemPs = connection.prepareStatement(itemSql)) {
                            itemPs.setString(1, marketId);
                            try (ResultSet itemRs = itemPs.executeQuery()) {
                                while (itemRs.next()) {
                                    String id = itemRs.getString("id");
                                    String name = itemRs.getString("name");
                                    String category = itemRs.getString("category");
                                    int buyPrice = itemRs.getInt("buy_price");
                                    int sellPrice = itemRs.getInt("sell_price");
                                    int stock = itemRs.getInt("stock");
                                    int demand = itemRs.getInt("demand");
                                    market.addItem(new MarketItem(id, name, category, buyPrice, sellPrice, stock, demand));
                                }
                            }
                        }
                        return market;
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public List<Market> getAllMarkets() {
        List<Market> markets = new ArrayList<>();
        try (Connection connection = getConnection();
             Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM market")) {
            while (rs.next()) {
                String marketId = rs.getString("market_id");
                String stationName = rs.getString("station_name");
                String stationType = rs.getString("station_type");
                String systemName = rs.getString("system_name");
                Market market = new Market(marketId, stationName, stationType, systemName);
                String itemSql = "SELECT * FROM market_item WHERE market_id = '" + marketId + "'";
                try (Statement itemStmt = connection.createStatement();
                     ResultSet itemRs = itemStmt.executeQuery(itemSql)) {
                    while (itemRs.next()) {
                        String id = itemRs.getString("id");
                        String name = itemRs.getString("name");
                        String category = itemRs.getString("category");
                        int buyPrice = itemRs.getInt("buy_price");
                        int sellPrice = itemRs.getInt("sell_price");
                        int stock = itemRs.getInt("stock");
                        int demand = itemRs.getInt("demand");
                        market.addItem(new MarketItem(id, name, category, buyPrice, sellPrice, stock, demand));
                    }
                }
                markets.add(market);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return markets;
    }
}
