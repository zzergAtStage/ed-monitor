package com.zergatstage.server.web;

import com.zergatstage.domain.commander.CommanderDTO;
import com.zergatstage.server.service.CommanderService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.util.List;

/**
 * REST controller for managing commanders.
 */
@RestController
@RequestMapping("/api/v1/commanders")
public class CommanderRestController {

    private final CommanderService commanderService;

    public CommanderRestController(CommanderService commanderService) {
        this.commanderService = commanderService;
    }

    /**
     * Returns all commanders.
     *
     * @return list of commanders
     */
    @GetMapping
    public ResponseEntity<List<CommanderDTO>> getAllCommanders() {
        List<CommanderDTO> commanders = commanderService.getCommanders();
        return ResponseEntity.ok(commanders);
    }

    /**
     * Returns a commander by id.
     *
     * @param id commander id
     * @return commander or 404 if not found
     */
    @GetMapping("/{id}")
    public ResponseEntity<CommanderDTO> getCommander(@PathVariable("id") Long id) {
        return commanderService.getCommanderById(id)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> notFound("/api/commanders/" + id));
    }

    /**
     * Creates a new commander.
     *
     * @param dto commander data
     * @return created commander and location header
     */
    @PostMapping
    public ResponseEntity<CommanderDTO> createCommander(@Valid @RequestBody CommanderDTO dto) {
        CommanderDTO created = commanderService.saveOrUpdateCommander(dto);
        // Build location header according to REST best practices
        URI location = URI.create(String.format("/api/commanders/%d", created.getId()));
        return ResponseEntity.created(location).body(created);
    }
    @PutMapping("/{id}")
    public ResponseEntity<CommanderDTO> updateCommander(
            @PathVariable("id") Long id,
            @Valid @RequestBody CommanderDTO dto) {

        if (dto.getId() != null && !dto.getId().equals(id)) {
            return ResponseEntity.badRequest().build();
        }
        return commanderService.updateCommander(id, dto)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> notFound("/api/commanders/" + id));
    }
    /**
     * Deletes a commander by id (soft delete).
     *
     * @param id commander id
     * @return 204 if deleted, 404 if not found
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCommander(@PathVariable Long id) {
        boolean deleted = commanderService.softDeleteCommander(id).isDeleted();
        if (deleted) {
            return ResponseEntity.noContent().build();
        } else {
            throw notFound("/api/commanders/" + id);
        }
    }

    /**
     * Helper to return Problem Details (RFC 7807) with 404 status.
     */
    private ResponseStatusException notFound(String instance) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Commander not found" + instance);
    }
}
