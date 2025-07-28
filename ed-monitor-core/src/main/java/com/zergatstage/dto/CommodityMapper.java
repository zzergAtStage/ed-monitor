package com.zergatstage.dto;

import com.zergatstage.domain.dictionary.Commodity;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

@Mapper
public interface CommodityMapper {

    CommodityMapper INSTANCE = Mappers.getMapper( CommodityMapper.class );


    CommodityDTO commodityToDTO(Commodity commodity);

    Commodity commodityDtoToEntity(CommodityDTO dto);
}
