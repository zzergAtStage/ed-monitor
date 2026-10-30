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

        // Incoming payloads are derived from the game's Market.json and never carry the
        // player-set supplyHub flag. Load the currently-stored markets in one batch (avoiding
        // N+1 lookups) so that flag can be preserved across this full-entity replace instead of
        // being silently wiped back to false on every refresh.
        Set<Long> incomingIds = incoming.stream()
                .filter(Objects::nonNull)
                .map(MarketDto::getMarketId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, Market> existingById = marketRepository.findAllById(incomingIds).stream()
                .collect(Collectors.toMap(Market::getMarketId, m -> m));

        List<Market> toSave = incoming.stream()
                .map(dto -> {
                    Market entity = MarketMapper.toEntity(dto, commodityById);
                    preserveSupplyHub(entity, dto, existingById.get(dto.getMarketId()));
                    return entity;
                })
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
        // Same rationale as upsertAll: don't let a Market.json-derived update wipe a
        // previously-set supplyHub flag just because the incoming DTO defaults it to false.
        preserveSupplyHub(merged, dto, marketRepository.findById(id).orElse(null));
        Market saved = marketRepository.save(merged);
        return Optional.of(MarketMapper.toDto(saved));
    }

    /**
     * Sets the player-controlled supplyHub flag on a market. This is the only path that may
     * change the flag — {@link #upsertAll} and {@link #updateDto} always preserve the stored
     * value instead.
     *
     * @param id    the market id
     * @param value the new supplyHub value
     * @return the updated market DTO, or empty if no market with that id exists
     */
    @Transactional
    public Optional<MarketDto> setSupplyHub(Long id, boolean value) {
        return marketRepository.findById(id).map(market -> {
            market.setSupplyHub(value);
            return MarketMapper.toDto(marketRepository.save(market));
        });
    }

    /**
     * Preserves the stored supplyHub flag across a full-entity replace triggered by a
     * Market.json refresh. Market.json never contains this player-set metadata, so a DTO's
     * default {@code false} must never clobber a previously stored {@code true}; only an
     * explicit {@code true} on the incoming DTO is allowed to change the flag here.
     *
     * @param entity   the entity about to be saved
     * @param dto      the incoming DTO
     * @param existing the currently-stored market, or {@code null} if this is a new market
     */
    private void preserveSupplyHub(Market entity, MarketDto dto, Market existing) {
        boolean preserved = dto.isSupplyHub() || (existing != null && existing.isSupplyHub());
        entity.setSupplyHub(preserved);
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

