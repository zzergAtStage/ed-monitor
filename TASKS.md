
# TASKS — ED-Monitor Route Optimizer (v1)

Project: **ED-Monitor — Route Optimizer (v1)**  
Goal: Implement a local greedy optimizer that builds delivery runs for supplying materials from Markets to a single Construction Site, minimizing the number of travels (runs and legs), using existing `ed-monitor-server` API data.

---

### T-100: Introduce Route Optimization DTOs (client-side)

**Description**:  
Add DTO classes for route optimization results and requests on the Swing client side. These DTOs will be used by the optimizer service and the UI, and will later match a potential server REST contract.

**Acceptance Criteria**:
- [x] New DTOs created in `ed-monitor-swing` under package `com.zergatstage.monitor.routes.dto` (or equivalent routes-related package):
  - [x] `RouteOptimizationRequest`
  - [x] `RoutePlanDto`
  - [x] `DeliveryRunDto`
  - [x] `RunLegDto`
  - [x] `PurchaseDto`
- [x] `RouteOptimizationRequest` contains at least:
  - [x] `Long constructionSiteId`
  - [x] `double cargoCapacityTons`
  - [x] `int maxMarketsPerRun` with default value `2` (e.g. via constructor or builder)
- [x] `RoutePlanDto` contains at least:
  - [x] `Long constructionSiteId`
  - [x] `List<DeliveryRunDto> runs` (initialized to empty list)
  - [x] `double coverageFraction` (0–1 of remaining demand covered by plan)
- [x] `DeliveryRunDto` contains at least:
  - [x] `int runIndex`
  - [x] `List<RunLegDto> legs`
  - [x] `double totalTonnage`
  - [x] `Map<String, Double> materialsSummaryTons` (key = materialName)
- [x] `RunLegDto` contains at least:
  - [x] `Long marketId`
  - [x] `String marketName`
  - [x] `List<PurchaseDto> purchases`
- [x] `PurchaseDto` contains at least:
  - [x] `String materialName`
  - [x] `double amountTons`
- [x] All DTOs are simple POJOs (getters/setters, equals/hashCode/toString where useful) with no business logic.
- [x] All DTO classes and public fields/methods have JavaDoc comments explaining their purpose.
- [x] DTOs do **not** depend on Swing UI classes.

**Scope**:
- Files involved: `ed-monitor-swing/src/main/java/com/zergatstage/monitor/routes/dto/*.java`
- Modules: `ed-monitor-swing`
- Estimated complexity: **Simple**

**Constraints & Assumptions**:
- This is a client-side model for now; later it may be mirrored on the server.
- DTOs are designed to be serializable if needed (e.g. Jackson annotations can be added later).

**Priority**: **P1**

**Notes**:
- These DTOs are the contract between the greedy optimizer and the UI and will later align with a REST API in `ed-monitor-server`.

---

### T-110: Define RouteOptimizationService interface (client-side domain boundary)

**Description**:  
Introduce a `RouteOptimizationService` interface as the abstraction for building route plans. This service encapsulates the optimization algorithm behind a stable boundary so that v1 can run in the Swing client, while v2+ can be moved to the server without changing the UI.

**Acceptance Criteria**:
- [x] New interface `RouteOptimizationService` created in `ed-monitor-swing` under a domain/service package, e.g. `com.zergatstage.monitor.routes.service`.
- [x] Interface contains at least:
  - [x] `RoutePlanDto buildRoutePlan(RouteOptimizationRequest request);`
- [x] JavaDoc on the interface explains:
  - [x] Purpose: build near-optimal delivery routes for a single Construction Site.
  - [x] Current assumptions: uniform leg cost, single Construction Site, offline accumulated market data.
- [x] JavaDoc on the method documents:
  - [x] Input parameters.
  - [x] Behavior when there are no remaining requirements or no suitable markets (returns empty plan).
  - [x] That it is *pure* with respect to input data (no side effects).
- [x] No Swing UI classes are referenced from the interface.

**Scope**:
- Files involved:  
  - `ed-monitor-swing/src/main/java/com/zergatstage/monitor/routes/service/RouteOptimizationService.java`
- Modules: `ed-monitor-swing`
- Estimated complexity: **Simple**

**Constraints & Assumptions**:
- For v1 the implementation will live in the Swing client; interface location is chosen to avoid circular dependencies with existing modules.

**Priority**: **P1**

**Notes**:
- This interface will be implemented by the greedy planner in T-130.

---

### T-120: Implement data access facade for optimizer (client-side)

**Description**:  
Create a small data access facade that hides details of calling `ed-monitor-server` APIs and provides the optimizer with a clean in-memory view of Construction Sites, required materials, and candidate markets with their commodity stocks.

**Acceptance Criteria**:
- [x] New interface `RouteOptimizerDataProvider` created under e.g. `com.zergatstage.monitor.routes.spi`.
- [x] Interface provides at least:
  - [x] Method to load a `ConstructionSiteDto` by `constructionSiteId`.
  - [x] Method to load a list of candidate markets that sell at least one required material for that construction site, returning market + commodity information.
- [x] A default implementation is created in `ed-monitor-swing` that uses existing REST client / service classes to call the server.
- [x] If there is no dedicated “candidate markets” endpoint yet, implementation:
  - [x] Uses available server data (e.g. all markets + their commodities) and filters client-side.
  - [x] Has clear TODO comments pointing to a future dedicated endpoint task.
- [x] All methods have JavaDoc describing their role and assumptions (offline accumulated data, no real-time refresh during optimization run).
- [x] No optimization logic is implemented inside this facade; it only fetches and adapts data.

**Scope**:
- Files involved:
  - `RouteOptimizerDataProvider.java`
  - `DefaultRouteOptimizerDataProvider.java` (name may vary, but intent must be clear)
- Modules: `ed-monitor-swing`
- Estimated complexity: **Medium**

**Constraints & Assumptions**:
- Uses existing DTOs coming from `ed-monitor-server` (`ConstructionSiteDto`, `MarketDto`, etc.).
- Network errors are handled by propagating a checked/unchecked exception up to the controller (no retries here).

**Priority**: **P1**

**Notes**:
- This facade decouples the greedy algorithm from HTTP details and will be reused when/if the optimizer is moved server-side.

---

### T-130: Implement greedy RouteOptimizationService (single construction site, client-side)

**Description**:  
Implement a greedy algorithm for route planning for a single Construction Site using the design described in the ADR: prioritize scarce materials, limit max markets per run, and build runs until there is no demand or no stock left.

**Acceptance Criteria**:
- [x] New class `GreedyRouteOptimizationService` implements `RouteOptimizationService` in `ed-monitor-swing` (e.g. package `com.zergatstage.monitor.routes.service`).
- [x] Implementation uses `RouteOptimizerDataProvider` to load:
  - [x] Construction Site requirements.
  - [x] Candidate markets and their commodity stocks.
- [x] Algorithm behavior:
  - [x] Works only for a single `constructionSiteId` from `RouteOptimizationRequest`.
  - [x] Obeys cargo capacity `C = cargoCapacityTons` and `maxMarketsPerRun` from the request.
  - [x] Computes remaining requirements `remaining[m]` for all materials with remaining > 0.
  - [x] Classifies materials by scarcity based on seller count (few sellers = scarce).
  - [x] Iteratively builds runs while there is remaining demand and markets with stock.
  - [x] In each run:
    - [x] Chooses a primary market using a scoring function (coverage + scarcity).
    - [x] Fills cargo greedily at the primary market, respecting capacity and stock.
    - [x] Optionally adds secondary markets up to `maxMarketsPerRun` to fill remaining capacity.
  - [x] Stops when either demand is fully covered, or no more stock is available.
- [x] Produces a `RoutePlanDto` with:
  - [x] `constructionSiteId` set.
  - [x] `runs` containing one `DeliveryRunDto` per run, with ordered `RunLegDto` list ending at the Construction Site.
  - [x] `coverageFraction` between 0 and 1 representing share of remaining demand covered.
- [x] JavaDoc on the class and main methods describes:
  - [x] The high-level greedy strategy.
  - [x] Known limitations (heuristic, not globally optimal).
  - [x] Complexity expectations (tens of markets, up to ~20 materials).
- [x] No Swing UI classes are referenced.
- [x] No network calls are made directly; all external data is obtained through `RouteOptimizerDataProvider`.

**Scope**:
- Files involved:  
  - `GreedyRouteOptimizationService.java`
- Modules: `ed-monitor-swing`
- Estimated complexity: **Complex**

**Constraints & Assumptions**:
- Cost per leg is uniform; distance/time is ignored in v1.
- Only one Construction Site is supported; multi-site will be handled in later versions.

**Priority**: **P0 (critical)**

**Notes**:
- Implementation should favor readability and traceability over micro-optimizations.

---

### T-140: Add unit tests for GreedyRouteOptimizationService

**Description**:  
Cover the greedy optimizer with unit tests using in-memory stub data providers to ensure predictable behavior and protect against regressions.

**Acceptance Criteria**:
- [x] Test class `GreedyRouteOptimizationServiceTest` (or equivalent) created under `src/test/java` in `ed-monitor-swing`.
- [x] Tests use a fake or stub implementation of `RouteOptimizerDataProvider` with in-memory data (no real HTTP calls).
- [x] Test cases cover at least:
  - [x] Single market providing all materials, capacity large enough → single run with one leg + site.
  - [x] Multiple markets, scarce material available in only one market → optimizer chooses that market as primary in the first run.
  - [x] Capacity smaller than total remaining demand → multiple runs created.
  - [x] Market stock exhausted before demand → remaining requirements not fully covered; `coverageFraction < 1.0`.
  - [x] No candidate markets → plan with `runs.isEmpty()` and `coverageFraction == 0.0`.
- [x] Assertions verify:
  - [x] Number of runs.
  - [x] Sequence of markets in each run.
  - [x] Total tonnage per run.
  - [x] Materials distribution matches expectations.
- [x] Tests are stable (no randomness) and run successfully via Maven (`mvn test`) without external dependencies.

**Scope**:
- Files involved: `GreedyRouteOptimizationServiceTest.java` (plus any small helper test fixtures).
- Modules: `ed-monitor-swing`
- Estimated complexity: **Medium**

**Constraints & Assumptions**:
- JUnit and existing test stack are reused (no new test framework introduced).
- No network, database, or file system I/O in tests.

**Priority**: **P1**

**Notes**:
- These tests describe the intended behavior of the heuristic and serve as executable documentation.

---

### T-150: Introduce RouteOptimizerModel and Controller (Swing client)

**Description**:  
Add a dedicated MVC model and controller for the Route Optimizer in the Swing application. This layer coordinates UI state, calls the optimizer service, and exposes data for rendering, without embedding business logic in UI components.

**Acceptance Criteria**:
- [ ] New class `RouteOptimizerModel` created under a UI/model package, e.g. `com.zergatstage.monitor.routes.ui`.
  - [ ] Holds:
    - [ ] Current `ConstructionSiteDto` (or its id).
    - [ ] Current `RouteOptimizationRequest` parameters (capacity, maxMarketsPerRun, etc.).
    - [ ] Latest `RoutePlanDto` result.
  - [ ] Provides getters/setters and listener or event mechanism compatible with existing UI patterns (e.g. `PropertyChangeSupport` or custom listeners).
- [ ] New class `RouteOptimizerController` created in the same package.
  - [ ] Depends on:
    - [ ] `RouteOptimizerDataProvider`
    - [ ] `RouteOptimizationService`
    - [ ] `RouteOptimizerModel`
  - [ ] Responsibilities:
    - [ ] Load Construction Site and candidate markets from server via data provider.
    - [ ] Construct and update `RouteOptimizationRequest` based on UI inputs.
    - [ ] Invoke `RouteOptimizationService.buildRoutePlan(...)` and store the result in the model.
    - [ ] Handle errors (e.g. show error dialogs via existing UI error reporting pattern, or propagate status).
- [ ] No route-planning heuristic is implemented in the controller or model (it lives only in the service).
- [ ] JavaDoc documents responsibilities and collaboration between model and controller.

**Scope**:
- Files involved:
  - `RouteOptimizerModel.java`
  - `RouteOptimizerController.java`
- Modules: `ed-monitor-swing`
- Estimated complexity: **Medium**

**Constraints & Assumptions**:
- Must follow existing Swing UI architectural style used in ED-Monitor (no new UI framework).
- Threading must be considered: long-running operations use background threads / workers, not the EDT (details can be filled according to current app conventions).

**Priority**: **P1**

**Notes**:
- This task prepares the ground for the dedicated Route Optimizer view in T-160.

---

### T-160: Implement Route Optimizer Swing UI and integrate with Construction Sites tab

**Description**:  
Create a Swing UI for the Route Optimizer (view) and integrate it with the existing Construction Sites tab – for example, as a “Plan Route…” action/dialog. The UI allows the user to configure parameters and inspect the generated runs.

**Acceptance Criteria**:
- [ ] New Swing view panel/dialog created, e.g. `RouteOptimizerDialog` or `RouteOptimizerPanel` in `com.zergatstage.monitor.routes.ui`.
- [ ] UI elements include at least:
  - [ ] Display of selected Construction Site (name, summary of delivered/remaining tonnage).
  - [ ] Field (spinner/text) for cargo capacity in tons.
  - [ ] Field (spinner) for `maxMarketsPerRun` (default 2).
  - [ ] Table of planned runs:
    - [ ] Columns like: `Run`, `Route (Markets → Site)`, `Total t`, `Materials summary`.
  - [ ] Detail view for selected run:
    - [ ] Table of legs with columns: `Leg`, `Market`, `Material`, `t`.
  - [ ] A visual indicator (e.g. label or progress bar) for `coverageFraction` (“Plan covers 85% of remaining demand”).
  - [ ] Buttons: `Recalculate`, `Close` (and optionally `Export` in future).
- [ ] View is wired to `RouteOptimizerModel` and `RouteOptimizerController` from T-150:
  - [ ] When user changes capacity or `maxMarketsPerRun`, controller is invoked to recompute plan.
  - [ ] When a new `RoutePlanDto` is available in the model, UI refreshes tables and summary.
- [ ] Integration with Construction Sites tab:
  - [ ] A button or menu item (e.g. “Plan Route…”) is added and enabled when a Construction Site is selected.
  - [ ] Clicking it opens the Route Optimizer view pre-loaded with that site.
- [ ] No business logic in Swing components beyond simple formatting.
- [ ] JavaDoc and/or inline comments describe key UI behaviors.

**Scope**:
- Files involved:
  - `RouteOptimizerDialog.java` / `RouteOptimizerPanel.java`
  - Modifications in existing Construction Sites view/controller to add entry point.
- Modules: `ed-monitor-swing`
- Estimated complexity: **Medium**

**Constraints & Assumptions**:
- Respect existing look & feel and localization strategy (if any).
- Long-running recalculation must not block the EDT.

**Priority**: **P2**

**Notes**:
- Further UX improvements (sorting, filtering, export) can be handled in follow-up tasks.

---

### T-170: Prepare server-side contract for future route planning endpoint (design-only)

**Description**:  
Define server-side DTOs and REST endpoint signature that mirrors the client-side optimizer DTOs, without implementing server-side optimization logic yet. This makes a future migration of the algorithm to `ed-monitor-server` straightforward.

**Acceptance Criteria**:
- [ ] New DTOs created in `ed-monitor-server` (package name to match project conventions, e.g. `com.zergatstage.monitor.routes.dto`):
  - [ ] `RouteOptimizationRequestDto`
  - [ ] `RoutePlanDto`
  - [ ] `DeliveryRunDto`
  - [ ] `RunLegDto`
  - [ ] `PurchaseDto`
- [ ] DTOs are structurally aligned with client-side equivalents created in T-100 (names and semantics match as much as possible).
- [ ] New Spring MVC controller skeleton added, e.g. `RouteOptimizationController` with:
  - [ ] `@PostMapping("/api/v1/construction-sites/{id}/route-plan")`
  - [ ] Method signature accepting `@PathVariable Long id` and `@RequestBody RouteOptimizationRequestDto` and returning `RoutePlanDto`.
  - [ ] JavaDoc clearly states that for now this endpoint is **not implemented** and should return HTTP 501 (Not Implemented) or similar.
- [ ] No business logic implemented; only contract and placeholder response with clear TODO.
- [ ] Controller and DTOs are covered by minimal Spring MVC test or at least compile and are picked up by the application context.

**Scope**:
- Files involved:
  - `RouteOptimizationController.java`
  - DTO files under `ed-monitor-server` routes package
- Modules: `ed-monitor-server`
- Estimated complexity: **Medium**

**Constraints & Assumptions**:
- Follows existing `ed-monitor-server` REST conventions (versioning, base path, error handling).
- Actual optimization will still run client-side until a dedicated task migrates it.

**Priority**: **P3 (low)**

**Notes**:
- This is a preparatory design step that allows OpenAI Codex or developers to later move the optimizer to the server without breaking the Swing client.
