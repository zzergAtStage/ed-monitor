package com.zergatstage.server.web;

import com.zergatstage.server.construction.dto.ConstructionSiteDto;
import com.zergatstage.server.service.ConstructionSiteService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/construction-sites")
public class ConstructionSiteController {

    private final ConstructionSiteService service;

    public ConstructionSiteController(ConstructionSiteService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<List<ConstructionSiteDto>> upsert(@Valid @RequestBody List<ConstructionSiteDto> payload) {
        List<ConstructionSiteDto> saved = service.upsertAll(payload);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @GetMapping
    public List<ConstructionSiteDto> all(@RequestParam(name = "includeCompleted", defaultValue = "false") boolean includeCompleted) {
        return service.findAllDto(includeCompleted);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ConstructionSiteDto> update(@PathVariable Long id, @Valid @RequestBody ConstructionSiteDto dto) {
        return service.updateDto(id, dto)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
