package com.zergatstage.server.construction;

import com.zergatstage.domain.MaterialRequirement;
import com.zergatstage.domain.ConstructionSite;
import com.zergatstage.domain.dictionary.Commodity;
import com.zergatstage.server.construction.dto.ConstructionSiteDto;
import com.zergatstage.server.construction.dto.MaterialRequirementDto;
import com.zergatstage.server.market.dto.CommodityDto;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

public class ConstructionSiteMapper {

    public static ConstructionSite toEntity(ConstructionSiteDto dto, Map<Long, Commodity> commodityById) {
        ConstructionSite site = ConstructionSite.builder()
                .marketId(dto.getMarketId())
                .siteId(dto.getSiteId())
                .requirements(new CopyOnWriteArrayList<>())
                .version(dto.getVersion() == null ? 0L : dto.getVersion())
                .lastUpdated(dto.getLastUpdated())
                .build();

        if (dto.getRequirements() != null) {
            for (MaterialRequirementDto r : dto.getRequirements()) {
                CommodityDto cd = r.getCommodity();
                Commodity commodity = cd != null ? commodityById.get(cd.getId()) : null;
                MaterialRequirement mr = MaterialRequirement.builder()
                        .id(r.getId())
                        .commodity(commodity)
                        .requiredQuantity(r.getRequiredQuantity())
                        .deliveredQuantity(r.getDeliveredQuantity())
                        .build();
                site.getRequirements().add(mr);
            }
        }
        return site;
    }

    public static ConstructionSiteDto toDto(ConstructionSite entity) {
        ConstructionSiteDto dto = new ConstructionSiteDto();
        dto.setMarketId(entity.getMarketId());
        dto.setSiteId(entity.getSiteId());
        List<MaterialRequirementDto> reqs = entity.getRequirements().stream()
                .map(ConstructionSiteMapper::toDto)
                .collect(Collectors.toList());
        dto.setRequirements(reqs);
        dto.setVersion(entity.getVersion());
        dto.setLastUpdated(entity.getLastUpdated());
        return dto;
    }

    public static MaterialRequirementDto toDto(MaterialRequirement mr) {
        Commodity c = mr.getCommodity();
        CommodityDto cd = c == null ? null : new CommodityDto(
                c.getId(), c.getName(), c.getNameLocalised(), c.getCategory(), c.getCategoryLocalised()
        );
        return new MaterialRequirementDto(mr.getId(), cd, mr.getRequiredQuantity(), mr.getDeliveredQuantity());
    }
}
