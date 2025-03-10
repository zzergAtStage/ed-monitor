package com.zergatstage.domain;

public class MarketItem {
    private final String id;
    private final String name;
    private final String category;
    private final int buyPrice;
    private final int sellPrice;
    private final int stock;
    private final int demand;

    public MarketItem(String id, String name, String category, int buyPrice, int sellPrice, int stock, int demand) {
        this.id = id;
        this.name = name;
        this.category = category;
        this.buyPrice = buyPrice;
        this.sellPrice = sellPrice;
        this.stock = stock;
        this.demand = demand;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getCategory() {
        return category;
    }

    public int getBuyPrice() {
        return buyPrice;
    }

    public int getSellPrice() {
        return sellPrice;
    }

    public int getStock() {
        return stock;
    }

    public int getDemand() {
        return demand;
    }

    @Override
    public String toString() {
        return name + " (Buy: " + buyPrice + ", Sell: " + sellPrice + ", Stock: " + stock + ")";
    }
}
