package com.zergatstage.server.service;

import com.zergatstage.domain.dictionary.Commodity;
import com.zergatstage.domain.dictionary.CommodityRepository;
import com.zergatstage.domain.makret.Market;
import com.zergatstage.server.market.MarketMapper;
import com.zergatstage.server.market.dto.CommodityDto;
import com.zergatstage.server.market.dto.MarketDto;
import com.zergatstage.server.market.dto.MarketItemDto;
import com.zergatstage.server.repository.MarketRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class MarketService {

    private final MarketRepository marketRepository;
    private final CommodityRepository commodityRepository;

    public MarketService(MarketRepository marketRepository, CommodityRepository commodityRepository) {
        this.marketRepository = marketRepository;
        this.commodityRepository = commodityRepository;
    }

    @Transactional
    public List<MarketDto> upsertAll(List<MarketDto> incoming) {
        Map<Long, Commodity> commodityById = ensureCommodities(incoming);
        List<Market> toSave = incoming.stream()
                .map(dto -> MarketMapper.toEntity(dto, commodityById))
                .toList();
        List<Market> saved = marketRepository.saveAll(toSave);
        return saved.stream().map(MarketMapper::toDto).collect(Collectors.toList());
    }

    public List<MarketDto> findAllDto() {
        return marketRepository.findAll().stream().map(MarketMapper::toDto).toList();
    }

    public Optional<MarketDto> findDtoById(Long id) {
        return marketRepository.findById(id).map(MarketMapper::toDto);
    }

    @Transactional
    public Optional<MarketDto> updateDto(Long id, MarketDto dto) {
        // Force id consistency
        dto.setMarketId(id);
        Map<Long, Commodity> commodityById = ensureCommodities(List.of(dto));
        Market merged = MarketMapper.toEntity(dto, commodityById);
        Market saved = marketRepository.save(merged);
        return Optional.of(MarketMapper.toDto(saved));
    }

    private Map<Long, Commodity> ensureCommodities(List<MarketDto> payload) {
        Map<Long, Commodity> result = new HashMap<>();
        Set<Long> ids = payload.stream()
                .filter(Objects::nonNull)
                .flatMap(m -> Optional.ofNullable(m.getItems()).orElseGet(List::of).stream())
                .map(MarketItemDto::getCommodity)
                .filter(Objects::nonNull)
                .map(CommodityDto::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) return result;

        List<Commodity> existing = commodityRepository.findAllById(ids);
        for (Commodity c : existing) {
            result.put(c.getId(), c);
        }
        // Create missing
        List<Commodity> toCreate = payload.stream()
                .filter(Objects::nonNull)
                .flatMap(m -> Optional.ofNullable(m.getItems()).orElseGet(List::of).stream())
                .map(MarketItemDto::getCommodity)
                .filter(Objects::nonNull)
                .filter(c -> c.getId() != null && !result.containsKey(c.getId()))
                .map(c -> Commodity.builder()
                        .id(c.getId())
                        .name(c.getName())
                        .nameLocalised(c.getNameLocalised())
                        .category(c.getCategory())
                        .categoryLocalised(c.getCategoryLocalised())
                        .build())
                .toList();
        if (!toCreate.isEmpty()) {
            List<Commodity> created = commodityRepository.saveAll(toCreate);
            for (Commodity c : created) {
                result.put(c.getId(), c);
            }
        }
        return result;
    }
}

