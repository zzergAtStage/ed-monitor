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
}

