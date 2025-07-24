package com.zergatstage.dto;

import com.zergatstage.domain.MaterialRequirement;
import org.mapstruct.Mapper;

@Mapper
public interface MaterialRequirementMapper {
    MaterialRequirementMapper INSTANCE = org.mapstruct.factory.Mappers.getMapper(MaterialRequirementMapper.class);
    MaterialRequirementDTO materialRequirementToDto(MaterialRequirement materialRequirement);
    MaterialRequirement materialRequirementDtoToEntity(MaterialRequirementDTO dto);
}
