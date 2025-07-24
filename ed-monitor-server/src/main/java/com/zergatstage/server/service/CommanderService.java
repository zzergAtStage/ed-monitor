package com.zergatstage.server.service;

import com.zergatstage.domain.commander.Commander;
import com.zergatstage.domain.commander.CommanderDTO;
import com.zergatstage.domain.commander.CommanderMapper;
import com.zergatstage.server.repository.CommanderRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class CommanderService {

    private final CommanderRepository repository;



    public CommanderService(CommanderRepository repository) {
        this.repository = repository;
    }

    /**
     * Saves a new Commander based on DTO input.
     *
     * @param dto the incoming CommanderDTO
     * @return the persisted Commander entity
     */
    @Transactional
    public CommanderDTO saveOrUpdateCommander(CommanderDTO dto) {
        Commander update = CommanderMapper.toEntity(dto);

        if (repository.existsByFID(dto.getFID())) {
            throw new DataIntegrityViolationException("FID already in use: " + dto.getFID());
        }
        return CommanderMapper.toDTO(repository.save(update));
    }

    public CommanderDTO getCommander(String FID){
        return CommanderMapper.toDTO(repository.findByFID(FID).orElseThrow());
    }
    @Cacheable
    public List<CommanderDTO> getCommanders() {
        return repository.findAll().stream()
                .map(CommanderMapper::toDTO)
                .toList();
    }

    public CommanderDTO softDeleteCommander(Long commanderId){
        Commander commander = repository.findById(commanderId).orElseThrow();
        commander.setDeleted(true);
        repository.save(commander);
        return CommanderMapper.toDTO(commander);
    }

    @Transactional
    public Optional<CommanderDTO> updateCommander(Long id, CommanderDTO dto) {
        Commander exist = repository.findById(id).orElseThrow();
        exist.setName(dto.getName());
        exist.setDeleted(dto.isDeleted());
        Commander updated = repository.save(exist);
        return Optional.ofNullable(CommanderMapper.toDTO(updated));
    }

    public Optional<CommanderDTO> getCommanderById(Long id) {
        Commander exist = repository.findById(id).orElseThrow();
        return Optional.ofNullable(CommanderMapper.toDTO(exist));
    }
}
