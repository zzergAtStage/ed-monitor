package com.zergatstage.dto;

import com.zergatstage.domain.ConstructionSite;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

@Mapper(uses = MaterialRequirementMapper.class)
public interface ConstructionSiteMapper {
    ConstructionSiteMapper INSTANCE = Mappers.getMapper( ConstructionSiteMapper.class);

    //@Mapping(source = "marketId", target = "marketId")
    ConstructionSiteDTO constructionSiteToDto(ConstructionSite site);

    ConstructionSite constructionSiteDtoToEntity(ConstructionSiteDTO dto);
}
