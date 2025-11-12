package com.zergatstage.server.web;

import com.zergatstage.server.market.dto.MarketDto;
import com.zergatstage.server.service.MarketService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/markets")
public class MarketController {

    private final MarketService service;

    public MarketController(MarketService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<List<MarketDto>> upsert(@Valid @RequestBody List<MarketDto> payload) {
        List<MarketDto> saved = service.upsertAll(payload);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @GetMapping
    public List<MarketDto> all() {
        return service.findAllDto();
    }

    @GetMapping("/{id}")
    public ResponseEntity<MarketDto> one(@PathVariable Long id) {
        return service.findDtoById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<MarketDto> update(@PathVariable Long id, @Valid @RequestBody MarketDto dto) {
        return service.updateDto(id, dto)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}

