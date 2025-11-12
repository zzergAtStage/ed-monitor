package com.zergatstage.server.service;

import com.zergatstage.domain.dictionary.Commodity;
import com.zergatstage.domain.dictionary.CommodityRepository;
import com.zergatstage.domain.ConstructionSite;
import com.zergatstage.server.construction.ConstructionSiteMapper;
import com.zergatstage.server.construction.dto.ConstructionSiteDto;
import com.zergatstage.server.construction.dto.MaterialRequirementDto;
import com.zergatstage.server.market.dto.CommodityDto;
import com.zergatstage.server.repository.ConstructionSiteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class ConstructionSiteService {

    private final ConstructionSiteRepository siteRepository;
    private final CommodityRepository commodityRepository;

    public ConstructionSiteService(ConstructionSiteRepository siteRepository, CommodityRepository commodityRepository) {
        this.siteRepository = siteRepository;
        this.commodityRepository = commodityRepository;
    }

    @Transactional
    public List<ConstructionSiteDto> upsertAll(List<ConstructionSiteDto> incoming) {
        Map<Long, Commodity> commodityById = ensureCommodities(incoming);
        // Preserve existing versions when client doesn't provide them (bulk sync compatibility)
        Map<Long, Long> existingVersions = new HashMap<>();
        List<Long> ids = incoming.stream().map(ConstructionSiteDto::getMarketId).toList();
        if (!ids.isEmpty()) {
            siteRepository.findAllById(ids).forEach(s -> existingVersions.put(s.getMarketId(), s.getVersion()));
        }

        List<ConstructionSite> toSave = incoming.stream()
                .map(dto -> {
                    if (dto.getVersion() == null && existingVersions.containsKey(dto.getMarketId())) {
                        dto.setVersion(existingVersions.get(dto.getMarketId()));
                    }
                    return ConstructionSiteMapper.toEntity(dto, commodityById);
                })
                .toList();
        List<ConstructionSite> saved = siteRepository.saveAll(toSave);
        return saved.stream().map(ConstructionSiteMapper::toDto).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ConstructionSiteDto> findAllDto(boolean includeCompleted) {
        return siteRepository.findAll().stream()
                .filter(site -> includeCompleted || hasRemainingRequirements(site))
                .map(ConstructionSiteMapper::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<ConstructionSiteDto> findDtoById(Long id) {
        return siteRepository.findById(id).map(ConstructionSiteMapper::toDto);
    }

    @Transactional
    public Optional<ConstructionSiteDto> updateDto(Long id, ConstructionSiteDto dto) {
        // ensure path id wins
        dto.setMarketId(id);
        Map<Long, Commodity> commodityById = ensureCommodities(List.of(dto));
        ConstructionSite merged = ConstructionSiteMapper.toEntity(dto, commodityById);
        ConstructionSite saved = siteRepository.save(merged);
        return Optional.of(ConstructionSiteMapper.toDto(saved));
    }

    private Map<Long, Commodity> ensureCommodities(List<ConstructionSiteDto> payload) {
        Map<Long, Commodity> result = new HashMap<>();
        Set<Long> ids = payload.stream()
                .filter(Objects::nonNull)
                .flatMap(s -> Optional.ofNullable(s.getRequirements()).orElse(List.of()).stream())
                .map(MaterialRequirementDto::getCommodity)
                .filter(Objects::nonNull)
                .map(CommodityDto::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) return result;

        List<Commodity> existing = commodityRepository.findAllById(ids);
        for (Commodity c : existing) {
            result.put(c.getId(), c);
        }
        // Create missing commodities
        List<Commodity> toCreate = payload.stream()
                .flatMap(s -> Optional.ofNullable(s.getRequirements()).orElse(List.of()).stream())
                .map(MaterialRequirementDto::getCommodity)
                .filter(Objects::nonNull)
                .filter(c -> !result.containsKey(c.getId()))
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

    private boolean hasRemainingRequirements(com.zergatstage.domain.ConstructionSite site) {
        if (site.getRequirements() == null || site.getRequirements().isEmpty()) return false;
        return site.getRequirements().stream()
                .anyMatch(r -> Math.max(r.getRequiredQuantity() - r.getDeliveredQuantity(), 0) > 0);
    }
}
