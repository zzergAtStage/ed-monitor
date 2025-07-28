package com.zergatstage.domain.commander;

public class CommanderMapper {
    public static CommanderDTO toDTO(Commander commander){
        return CommanderDTO.builder()
                .id(commander.getId())
                .name(commander.getName())
                .isDeleted(commander.isDeleted())
                .FID(commander.getFID())
                .build();
    }

    public static Commander toEntity(CommanderDTO commanderDTO) {
        return Commander.builder()
                .id(commanderDTO.getId())
                .isDeleted(commanderDTO.isDeleted())
                .name(commanderDTO.getName())
                .FID(commanderDTO.getFID())
                .build();
    }
}
