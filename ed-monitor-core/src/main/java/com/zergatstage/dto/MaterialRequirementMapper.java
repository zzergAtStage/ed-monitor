package com.zergatstage.dto;

import com.zergatstage.domain.MaterialRequirement;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper
public interface MaterialRequirementMapper {
    MaterialRequirementMapper INSTANCE = org.mapstruct.factory.Mappers.getMapper(MaterialRequirementMapper.class);
    @Mapping(source = "materialName", target = "materialName")
    MaterialRequirementDTO materialRequirementToDto(MaterialRequirement materialRequirement);
    @Mapping(source = "dto.materialName", target = "name")
    MaterialRequirement materialRequirementDtoToEntity(MaterialRequirementDTO dto);
}
