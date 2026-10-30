package com.zergatstage.server.service;

import com.zergatstage.domain.carrier.FleetCarrier;
import com.zergatstage.domain.dictionary.Commodity;
import com.zergatstage.domain.dictionary.CommodityRepository;
import com.zergatstage.server.carrier.FleetCarrierMapper;
import com.zergatstage.server.carrier.dto.CarrierHoldItemDto;
import com.zergatstage.server.carrier.dto.FleetCarrierDto;
import com.zergatstage.server.market.dto.CommodityDto;
import com.zergatstage.server.repository.FleetCarrierRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class FleetCarrierService {

    private final FleetCarrierRepository fleetCarrierRepository;
    private final CommodityRepository commodityRepository;

    public FleetCarrierService(FleetCarrierRepository fleetCarrierRepository, CommodityRepository commodityRepository) {
        this.fleetCarrierRepository = fleetCarrierRepository;
        this.commodityRepository = commodityRepository;
    }

    /**
     * Returns every persisted Fleet Carrier as a DTO.
     *
     * @return the list of known carriers
     */
    public List<FleetCarrierDto> findAllDto() {
        return fleetCarrierRepository.findAll().stream().map(FleetCarrierMapper::toDto).toList();
    }

    /**
     * Looks up a single Fleet Carrier by id.
     *
     * @param id the carrier id
     * @return the carrier DTO, or empty if no carrier with that id exists
     */
    public Optional<FleetCarrierDto> findDtoById(Long id) {
        return fleetCarrierRepository.findById(id).map(FleetCarrierMapper::toDto);
    }

    /**
     * Creates or fully replaces a Fleet Carrier. Hold items reference {@link Commodity} rows
     * that may not exist yet on the server; any missing commodities are created before the
     * carrier itself is persisted.
     *
     * @param dto the incoming carrier state
     * @return the saved carrier DTO
     */
    @Transactional
    public FleetCarrierDto upsert(FleetCarrierDto dto) {
        Map<Long, Commodity> commodityById = ensureCommodities(dto);
        FleetCarrier entity = FleetCarrierMapper.toEntity(dto, commodityById);
        FleetCarrier saved = fleetCarrierRepository.save(entity);
        return FleetCarrierMapper.toDto(saved);
    }

    private Map<Long, Commodity> ensureCommodities(FleetCarrierDto dto) {
        Map<Long, Commodity> result = new HashMap<>();
        List<CarrierHoldItemDto> hold = Optional.ofNullable(dto.getHold()).orElseGet(List::of);

        Set<Long> ids = hold.stream()
                .filter(Objects::nonNull)
                .map(CarrierHoldItemDto::getCommodity)
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
        List<Commodity> toCreate = hold.stream()
                .filter(Objects::nonNull)
                .map(CarrierHoldItemDto::getCommodity)
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
