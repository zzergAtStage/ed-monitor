package com.zergatstage.monitor.http;

import com.zergatstage.domain.makret.Market;
import com.zergatstage.domain.makret.MarketItem;
import com.zergatstage.monitor.http.dto.CommodityDto;
import com.zergatstage.monitor.http.dto.MarketDto;
import com.zergatstage.monitor.http.dto.MarketItemDto;

import java.util.List;
import java.util.stream.Collectors;

public class MarketDtoMapper {
    public static MarketDto toDto(Market market) {
        MarketDto dto = new MarketDto();
        dto.setMarketId(market.getMarketId());
        dto.setStationName(market.getStationName());
        dto.setStationType(market.getStationType());
        dto.setSystemName(market.getSystemName());
        List<MarketItemDto> items = market.getItems().values().stream()
                .map(MarketDtoMapper::toDto)
                .collect(Collectors.toList());
        dto.setItems(items);
        return dto;
    }

    private static MarketItemDto toDto(MarketItem item) {
        CommodityDto c = new CommodityDto(
                item.getCommodity().getId(),
                item.getCommodity().getName(),
                item.getCommodity().getNameLocalised(),
                item.getCommodity().getCategory(),
                item.getCommodity().getCategoryLocalised()
        );
        return new MarketItemDto(c, item.getBuyPrice(), item.getSellPrice(), item.getStock(), item.getDemand());
    }

    public static Market fromDto(MarketDto dto) {
        Market market = Market.builder()
                .marketId(dto.getMarketId())
                .stationName(dto.getStationName())
                .stationType(dto.getStationType())
                .systemName(dto.getSystemName())
                .items(new java.util.HashMap<>())
                .build();
        for (MarketItemDto itemDto : dto.getItems()) {
            var c = itemDto.getCommodity();
            var commodity = com.zergatstage.domain.dictionary.Commodity.builder()
                    .id(c.getId())
                    .name(c.getName())
                    .nameLocalised(c.getNameLocalised())
                    .category(c.getCategory())
                    .categoryLocalised(c.getCategoryLocalised())
                    .build();
            var item = MarketItem.builder()
                    .commodity(commodity)
                    .market(market)
                    .buyPrice(itemDto.getBuyPrice())
                    .sellPrice(itemDto.getSellPrice())
                    .stock(itemDto.getStock())
                    .demand(itemDto.getDemand())
                    .build();
            market.addItem(item);
        }
        return market;
    }
}
