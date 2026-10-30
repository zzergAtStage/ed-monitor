package com.zergatstage.server.web;

import com.zergatstage.server.carrier.dto.FleetCarrierDto;
import com.zergatstage.server.service.FleetCarrierService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/carriers")
public class FleetCarrierController {

    private final FleetCarrierService service;

    public FleetCarrierController(FleetCarrierService service) {
        this.service = service;
    }

    @GetMapping
    public List<FleetCarrierDto> all() {
        return service.findAllDto();
    }

    @GetMapping("/{id}")
    public ResponseEntity<FleetCarrierDto> one(@PathVariable Long id) {
        return service.findDtoById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Creates or fully replaces a Fleet Carrier. The path variable {@code id} always wins over
     * any {@code carrierId} present in the request body.
     *
     * @param id  the carrier id from the URL
     * @param dto the incoming carrier state
     * @return 200 with the saved carrier, or 409 with the currently stored carrier if the
     * incoming version conflicts with a concurrent update
     */
    @PutMapping("/{id}")
    public ResponseEntity<FleetCarrierDto> update(@PathVariable Long id, @Valid @RequestBody FleetCarrierDto dto) {
        dto.setCarrierId(id);
        try {
            FleetCarrierDto saved = service.upsert(dto);
            return ResponseEntity.ok(saved);
        } catch (ObjectOptimisticLockingFailureException ex) {
            return service.findDtoById(id)
                    .map(current -> ResponseEntity.status(HttpStatus.CONFLICT).body(current))
                    .orElse(ResponseEntity.status(HttpStatus.CONFLICT).build());
        }
    }
}
