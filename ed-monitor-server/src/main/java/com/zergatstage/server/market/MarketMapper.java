package com.zergatstage.server.market;

import com.zergatstage.domain.dictionary.Commodity;
import com.zergatstage.domain.makret.Market;
import com.zergatstage.domain.makret.MarketItem;
import com.zergatstage.server.market.dto.CommodityDto;
import com.zergatstage.server.market.dto.MarketDto;
import com.zergatstage.server.market.dto.MarketItemDto;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class MarketMapper {

    public static Market toEntity(MarketDto dto, Map<Long, Commodity> commodityById) {
        Market market = Market.builder()
                .marketId(dto.getMarketId())
                .stationName(dto.getStationName())
                .stationType(dto.getStationType())
                .systemName(dto.getSystemName())
                .items(new HashMap<>())
                .build();

        for (MarketItemDto itemDto : dto.getItems()) {
            CommodityDto c = itemDto.getCommodity();
            Commodity commodity = commodityById.get(c.getId());
            MarketItem item = MarketItem.builder()
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

    public static MarketDto toDto(Market entity) {
        MarketDto dto = new MarketDto();
        dto.setMarketId(entity.getMarketId());
        dto.setStationName(entity.getStationName());
        dto.setStationType(entity.getStationType());
        dto.setSystemName(entity.getSystemName());
        List<MarketItemDto> items = entity.getItems().values().stream()
                .map(MarketMapper::toDto)
                .collect(Collectors.toList());
        dto.setItems(items);
        return dto;
    }

    public static MarketItemDto toDto(MarketItem item) {
        Commodity commodity = item.getCommodity();
        CommodityDto c = new CommodityDto(
                commodity.getId(),
                commodity.getName(),
                commodity.getNameLocalised(),
                commodity.getCategory(),
                commodity.getCategoryLocalised()
        );
        return new MarketItemDto(c, item.getBuyPrice(), item.getSellPrice(), item.getStock(), item.getDemand());
    }
}

