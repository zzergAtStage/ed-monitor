package com.zergatstage.dto;

import com.zergatstage.domain.MaterialRequirement;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(uses = CommodityMapper.class)
public interface MaterialRequirementMapper {
    MaterialRequirementMapper INSTANCE = org.mapstruct.factory.Mappers.getMapper(MaterialRequirementMapper.class);
    /**
     * Entity ➔ DTO
     *
     * @param entity the source MaterialRequirement
     * @return a new DTO with materialName ← entity.getCommodity().getName()
     */
    MaterialRequirementDTO toDto(MaterialRequirement entity);

    /**
     * DTO ➔ Entity
     *
     * @param dto the source DTO
     * @return a new MaterialRequirement whose {@code commodity.name} is set
     *         (other Commodity fields left null)
     */
    @Mapping(source = "commodity", target = "commodity")
    MaterialRequirement toEntity(MaterialRequirementDTO dto);
}
