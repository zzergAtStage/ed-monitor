package com.zergatstage.monitor.http;

import com.zergatstage.domain.ConstructionSite;
import com.zergatstage.domain.MaterialRequirement;
import com.zergatstage.domain.dictionary.Commodity;
import com.zergatstage.monitor.http.dto.CommodityDto;
import com.zergatstage.monitor.http.dto.ConstructionSiteDto;
import com.zergatstage.monitor.http.dto.MaterialRequirementDto;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

public class ConstructionSiteDtoMapper {
    public static ConstructionSiteDto toDto(ConstructionSite site) {
        ConstructionSiteDto dto = new ConstructionSiteDto();
        dto.setMarketId(site.getMarketId());
        dto.setSiteId(site.getSiteId());
        List<MaterialRequirementDto> reqs = site.getRequirements().stream()
                .map(ConstructionSiteDtoMapper::toDto)
                .collect(Collectors.toList());
        dto.setRequirements(reqs);
        return dto;
    }

    public static MaterialRequirementDto toDto(MaterialRequirement r) {
        Commodity c = r.getCommodity();
        CommodityDto cd = c == null ? null : new CommodityDto(
                c.getId(), c.getName(), c.getNameLocalised(), c.getCategory(), c.getCategoryLocalised()
        );
        return new MaterialRequirementDto(r.getId(), cd, r.getRequiredQuantity(), r.getDeliveredQuantity());
    }

    public static ConstructionSite fromDto(ConstructionSiteDto dto) {
        ConstructionSite site = ConstructionSite.builder()
                .marketId(dto.getMarketId())
                .siteId(dto.getSiteId())
                .requirements(new CopyOnWriteArrayList<>())
                .build();
        if (dto.getRequirements() != null) {
            for (MaterialRequirementDto mrDto : dto.getRequirements()) {
                CommodityDto cd = mrDto.getCommodity();
                Commodity commodity = cd == null ? null : Commodity.builder()
                        .id(cd.getId())
                        .name(cd.getName())
                        .nameLocalised(cd.getNameLocalised())
                        .category(cd.getCategory())
                        .categoryLocalised(cd.getCategoryLocalised())
                        .build();
                MaterialRequirement mr = MaterialRequirement.builder()
                        .id(mrDto.getId())
                        .commodity(commodity)
                        .requiredQuantity(mrDto.getRequiredQuantity())
                        .deliveredQuantity(mrDto.getDeliveredQuantity())
                        .build();
                site.getRequirements().add(mr);
            }
        }
        return site;
    }
}

