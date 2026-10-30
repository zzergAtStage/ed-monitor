package com.zergatstage.server.carrier;

import com.zergatstage.domain.carrier.CarrierHoldItem;
import com.zergatstage.domain.carrier.FleetCarrier;
import com.zergatstage.domain.dictionary.Commodity;
import com.zergatstage.server.carrier.dto.CarrierHoldItemDto;
import com.zergatstage.server.carrier.dto.FleetCarrierDto;
import com.zergatstage.server.market.dto.CommodityDto;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Converts between {@link FleetCarrier} entities and {@link FleetCarrierDto} transfer objects.
 */
public class FleetCarrierMapper {

    /**
     * Builds a {@link FleetCarrier} entity from an incoming DTO, resolving each hold item's
     * commodity from the supplied lookup map.
     *
     * @param dto            the incoming DTO
     * @param commodityById  a map of already-persisted commodities keyed by id, covering every
     *                       commodity referenced by {@code dto.getHold()}
     * @return the built entity, ready to be persisted
     */
    public static FleetCarrier toEntity(FleetCarrierDto dto, Map<Long, Commodity> commodityById) {
        FleetCarrier carrier = FleetCarrier.builder()
                .carrierId(dto.getCarrierId())
                .callsign(dto.getCallsign())
                .name(dto.getName())
                .currentSystem(dto.getCurrentSystem())
                .systemManuallySet(dto.isSystemManuallySet())
                .totalCapacity(dto.getTotalCapacity())
                .cargoUsed(dto.getCargoUsed())
                .freeSpace(dto.getFreeSpace())
                .holdSnapshotAt(dto.getHoldSnapshotAt())
                .hold(new ArrayList<>())
                .version(dto.getVersion())
                .build();

        if (dto.getHold() == null || dto.getHold().isEmpty()) {
            return carrier;
        }

        for (CarrierHoldItemDto itemDto : dto.getHold()) {
            if (itemDto == null) {
                continue;
            }
            CommodityDto c = itemDto.getCommodity();
            if (c == null || c.getId() == null) {
                continue;
            }
            Commodity commodity = commodityById.get(c.getId());
            if (commodity == null) {
                // As a safety net, skip items whose commodities were not materialized upstream
                continue;
            }
            carrier.getHold().add(CarrierHoldItem.builder()
                    .commodity(commodity)
                    .quantity(itemDto.getQuantity())
                    .build());
        }
        return carrier;
    }

    /**
     * Converts a {@link FleetCarrier} entity to its REST transfer representation.
     *
     * @param entity the entity to convert
     * @return the resulting DTO
     */
    public static FleetCarrierDto toDto(FleetCarrier entity) {
        FleetCarrierDto dto = new FleetCarrierDto();
        dto.setCarrierId(entity.getCarrierId());
        dto.setCallsign(entity.getCallsign());
        dto.setName(entity.getName());
        dto.setCurrentSystem(entity.getCurrentSystem());
        dto.setSystemManuallySet(entity.isSystemManuallySet());
        dto.setTotalCapacity(entity.getTotalCapacity());
        dto.setCargoUsed(entity.getCargoUsed());
        dto.setFreeSpace(entity.getFreeSpace());
        dto.setHoldSnapshotAt(entity.getHoldSnapshotAt());
        List<CarrierHoldItemDto> hold = entity.getHold().stream()
                .map(FleetCarrierMapper::toDto)
                .collect(Collectors.toList());
        dto.setHold(hold);
        dto.setVersion(entity.getVersion());
        dto.setLastUpdated(entity.getLastUpdated());
        return dto;
    }

    private static CarrierHoldItemDto toDto(CarrierHoldItem item) {
        Commodity commodity = item.getCommodity();
        CommodityDto c = new CommodityDto(
                commodity.getId(),
                commodity.getName(),
                commodity.getNameLocalised(),
                commodity.getCategory(),
                commodity.getCategoryLocalised()
        );
        return new CarrierHoldItemDto(c, item.getQuantity());
    }
}
