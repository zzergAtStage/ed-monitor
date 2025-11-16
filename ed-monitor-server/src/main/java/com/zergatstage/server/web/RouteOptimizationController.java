package com.zergatstage.server.web;

import com.zergatstage.server.routes.dto.RouteOptimizationRequestDto;
import com.zergatstage.server.routes.dto.RoutePlanDto;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Placeholder REST endpoint for future server-side route plan generation.
 * <p>
 * The Swing client currently computes routes locally; this contract makes it possible to
 * migrate the algorithm to the server without changing clients. For now the controller
 * responds with HTTP 501 (Not Implemented).
 * </p>
 */
@RestController
@RequestMapping("/api/v1/construction-sites")
public class RouteOptimizationController {

    /**
     * Accepts a route planning request for the specified construction site and will return
     * {@link HttpStatus#NOT_IMPLEMENTED} until server-side optimization is implemented.
     *
     * @param constructionSiteId site identifier supplied via path
     * @param request            optimization parameters mirroring the client DTO
     * @return HTTP 501 with an empty body to indicate the feature is pending
     */
    @PostMapping("/{constructionSiteId}/route-plan")
    public ResponseEntity<RoutePlanDto> buildRoutePlan(@PathVariable Long constructionSiteId,
                                                       @RequestBody RouteOptimizationRequestDto request) {
        // TODO(route-optimizer-v1): implement server-side planner once algorithm migrates.
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }
}
