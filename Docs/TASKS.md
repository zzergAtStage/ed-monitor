# TASKS.md — Task Registry & Backlog

Task-driven development registry. Each task includes scope, acceptance criteria, and priority.

---

## Task Template

```markdown
### T-XXX: [Task Title]

**Description**: Brief what needs doing and why.

**Acceptance Criteria**:
- [ ] Criterion 1: specific, testable
- [ ] Criterion 2
- [ ] Criterion 3

**Scope**:
- Files involved: [list or search pattern]
- Modules: [core/server/swing]
- Estimated complexity: Simple | Medium | Complex

**Constraints & Assumptions**:
- Any limitations or dependencies

**Priority**: P0 (critical) | P1 (high) | P2 (medium) | P3 (low)

**Notes**:
- Additional context for agent/developer
```

---

## Active Tasks

### T-001: Complete DroneProvisionerPanel Implementation

**Description**: DroneProvisionerPanel UI component needs event handler wiring and observer pattern integration so asteroid material data flows from journal events to colored rectangle display.

**Acceptance Criteria**:
- [ ] AsteroidProspectEventHandler calls `DroneProvisionerPanel.onAsteroidProspected(event, tritiumProportion)`
- [ ] DroneProvisionerPanel registers as listener with AsteroidManager
- [ ] Combobox (Tritium, Platinum, Painite) selection controls which material is tracked
- [ ] Three stacked rectangles update color based on selected material proportion:
  - < 30% → gray (#B0B0B0)
  - 30–43% → orange (#FFA500)
  - > 43% → violet (#8A2BE2)
- [ ] Most recent prospect appears at top of stack
- [ ] DroneProvisionerPanel.onDroneLaunched() displays "Drone Launched: Yes" for 3 seconds, then resets
- [ ] Unit test for color mapping (e.g., 0.15 → gray, 0.37 → orange, 0.50 → violet)

**Scope**:
- Files: `ed-monitor-swing/src/main/java/com/zergatstage/monitor/component/DroneProvisionerPanel.java`
- Modules: ed-monitor-swing
- Complexity: Medium

**Constraints**:
- Use BaseManager observer pattern (addListener/notifyListeners)
- All Swing operations on EDT via SwingUtilities.invokeLater()
- No blocking operations in listeners

**Priority**: P1 (high)

**Notes**:
- AsteroidProspectEventHandler already extracts Tritium; adapt handler to pass all material data
- See AGENTS.md section 3 (Observer Pattern) and ARCHITECTURE.md section 4 (Event Flow)
- Check EVENT_TYPES.md for ProspectedAsteroid JSON structure

---

### T-002: Wire AsteroidProspectEventHandler to Managers and UI

**Description**: AsteroidProspectEventHandler currently only updates a label; it should update AsteroidManager state and trigger observer notifications to UI components.

**Acceptance Criteria**:
- [ ] AsteroidProspectEventHandler extracts all materials from event (not just Tritium)
- [ ] Handler calls `asteroidManager.updateMaterials(event)` (or similar method)
- [ ] AsteroidManager stores last 3 prospected asteroids internally
- [ ] AsteroidManager.notifyListeners() called after state update
- [ ] DroneProvisionerPanel receives notification and updates display
- [ ] No hardcoding of "Tritium" in handler; handler is material-agnostic
- [ ] Integration test: mock event → handler → manager → panel update

**Scope**:
- Files: 
  - `handlers/AsteroidProspectEventHandler.java`
  - `service/managers/AsteroidManager.java`
  - `component/DroneProvisionerPanel.java`
- Modules: ed-monitor-swing
- Complexity: Medium

**Constraints**:
- Handler must not know about UI component directly
- Manager must not know about UI component directly
- Communication only via BaseManager observer pattern

**Priority**: P1 (high)

**Notes**:
- Related to T-001; both tasks should be done together
- Decouple event parsing (handler) from state management (manager) from UI display (panel)

---

### T-003: Implement Cargo Merge Logic in CargoUpdateEventHandler

**Description**: CargoUpdateEventHandler has TODO for manual merge when cargo is delivered across multiple construction sites/depots. Need to track cargo per depot and merge intelligently.

**Acceptance Criteria**:
- [ ] CargoUpdateEventHandler.handleEvent() processes all transfers, not just first one
- [ ] For each transfer, ConstructionSiteManager.addCargo(site, material, quantity) called
- [ ] If cargo exceeds single depot capacity, distribute to next depot (manual merge)
- [ ] Handler logs warnings if depot not found or cargo rejected
- [ ] Unit test: 50 units Tritium to site with 3 depots (20 max each) → distributed [20, 20, 10]
- [ ] No silent failures; all merge decisions logged

**Scope**:
- Files: `handlers/CargoUpdateEventHandler.java`, `service/managers/ConstructionSiteManager.java`
- Modules: ed-monitor-swing
- Complexity: Medium

**Constraints**:
- Must handle depot capacity limits
- Must not lose cargo data
- Must maintain order of transfers (FIFO)

**Priority**: P2 (medium)

**Notes**:
- See CargoUpdateEventHandler line 223–264 for TODOs
- Merge logic is domain-specific; consider adding to ConstructionSiteManager

---

### T-004: Complete Construction Site Status UI (WIP)

**Description**: ConstructionSitePanel is partially implemented (WIP) with TODOs for depot filtering and commodity selection UI.

**Acceptance Criteria**:
- [ ] ConstructionSiteManger.updateSite() is fully functional (not WIP)
- [ ] ConstructionSitePanel.refresh() updates all depot rows with current Delivered/Total/Percentage
- [ ] Add checkbox "Hide Completed Depots" (see TODO line 407, 424)
  - When checked: filter out depots with Percentage = 100.0
  - When unchecked: show all depots
- [ ] Commodity dropdown in delivery form populated from CommodityRegistry (not dummy Commodity.builder())
- [ ] Form validation: reject negative Delivered quantities (currently no validation)
- [ ] Integration test: add 50 Tritium → site updates, percentage recalculated, UI refreshes

**Scope**:
- Files: 
  - `component/ConstructionSitePanel.java`
  - `service/managers/ConstructionSiteManager.java`
  - `service/CommodityRegistry.java`
- Modules: ed-monitor-swing
- Complexity: Complex

**Constraints**:
- Use observer pattern (listeners notify ConstructionSitePanel on state change)
- Form submission must be non-blocking

**Priority**: P2 (medium)

**Notes**:
- Multiple TODOs scattered in code; consolidate into single handler flow
- See ARCHITECTURE.md section 7 (Limitations) for current state

---

### T-005: Implement Async Market Data Fetch

**Description**: MarketDataUpdateService likely blocks EDT when fetching market data from server. Need async fetch to avoid UI freeze.

**Acceptance Criteria**:
- [ ] MarketDataUpdateService.fetchMarketData() runs on background thread (ExecutorService)
- [ ] EDT is never blocked by HTTP request
- [ ] On success: manager.notifyListeners() called on EDT
- [ ] On failure: error logged, graceful fallback (cached data or retry)
- [ ] UI shows loading indicator during fetch
- [ ] Timeout after 5 seconds with user-facing error message

**Scope**:
- Files: `service/MarketDataUpdateService.java`, possibly UI components that display market data
- Modules: ed-monitor-swing
- Complexity: Medium

**Constraints**:
- No blocking operations on EDT
- All Swing updates on EDT (SwingUtilities.invokeLater)

**Priority**: P2 (medium)

**Notes**:
- Related to server integration (ARCHITECTURE.md section 9)
- Use same ExecutorService pattern as JournalLogMonitor

---

### T-006: Refactor Handler Registration to Use Discovery/Factory

**Description**: HandlerConfiguration.getLogEventHandlers() is hardcoded; makes it difficult for agents to add new handlers. Should use ServiceLoader or Spring discovery.

**Acceptance Criteria**:
- [ ] HandlerConfiguration uses Java ServiceLoader to discover handlers on classpath
- [ ] OR: Add handlers to application.yaml and load via Spring (if server module integrated)
- [ ] Existing hardcoded handlers still work (backwards compatible)
- [ ] Documentation updated: AGENTS.md explains how to add new handler (just create class + register)
- [ ] Integration test: new test handler discovered and called automatically

**Scope**:
- Files: `handlers/HandlerConfiguration.java`, possibly `META-INF/services/` for ServiceLoader
- Modules: ed-monitor-swing
- Complexity: Medium

**Constraints**:
- Must not break existing handler registration
- Must work in JAR deployment (ServiceLoader resource loading)

**Priority**: P3 (low)

**Notes**:
- Future work when adding many custom handlers
- Current hardcoding is acceptable for 11 handlers

---

### T-007: Add Event Filtering & Prioritization

**Description**: Currently all events are processed in order received. Some events (e.g., LaunchDrone) should be prioritized; some events may be filtered (e.g., duplicate scans).

**Acceptance Criteria**:
- [ ] JournalLogMonitor supports event priority levels (HIGH, MEDIUM, LOW)
- [ ] HIGH priority events processed immediately; others queued
- [ ] Event deduplication: ignore duplicate ProspectedAsteroid on same asteroid within 1 second
- [ ] Configuration: priority mapping in LogMonitorConfig (YAML)
- [ ] UI shows "Processing queue: N events" when backlog exists

**Scope**:
- Files: `service/JournalLogMonitor.java`, `config/LogMonitorConfig.java`, `MonitorView.java` (status label)
- Modules: ed-monitor-swing
- Complexity: Complex

**Constraints**:
- Must maintain event order within same priority level (stable sort)
- Deduplication must be configurable (turn on/off per event type)

**Priority**: P3 (low)

**Notes**:
- Future performance optimization
- Not needed for current feature set

---

## Completed Tasks

*None yet — backlog is for future development*

---

## Task Selection for Agents

### Quick Start (New Agent Onboarding)
Start with these in order:
1. **Read**: AGENTS.md → ARCHITECTURE.md → EVENT_TYPES.md
2. **Implement**: T-001 (DroneProvisionerPanel) — touches all 3 tiers (event → manager → UI)
3. **Implement**: T-002 (Wire AsteroidProspectEventHandler) — reinforces observer pattern

### For Continuous Integration
- **T-001** and **T-002**: Must pass unit tests before merge
- **T-003**: Next highest priority after core event flow works
- **T-004**: Unlocks construction site feature completeness

### For Long-Term (When Adding Features)
- **T-006**: Do before adding 20+ custom handlers
- **T-007**: Do only if event processing becomes bottleneck
- **T-005**: Do when market data fetch is integrated

---

## Acceptance Criteria Rules

**Each criterion must be**:
- ✅ Specific (testable, not vague like "looks good")
- ✅ Atomic (one thing per bullet)
- ✅ Measurable (can verify with unit test or code review)
- ✅ Realistic (achievable in 1–2 days per task)

**Example GOOD**:
- [ ] Unit test: testColorMapping_LowTritium_ReturnsGray() passes

**Example BAD**:
- [ ] Make it work

---

## Task Metadata

| Task | Status | Assignee | ETC | Priority |
|------|--------|----------|-----|----------|
| T-001 | Backlog | — | 1 day | P1 |
| T-002 | Backlog | — | 1 day | P1 |
| T-003 | Backlog | — | 1 day | P2 |
| T-004 | Backlog | — | 2 days | P2 |
| T-005 | Backlog | — | 1 day | P2 |
| T-006 | Backlog | — | 1 day | P3 |
| T-007 | Backlog | — | 2 days | P3 |

---

## Adding New Tasks

When creating a new task:

1. Assign next T-NNN number (check end of TASKS.md)
2. Copy task template from top of this document
3. Write description (2–3 sentences, user-focused)
4. Write 3–5 acceptance criteria (specific, testable)
5. List files and modules affected
6. Set priority: P0 (critical), P1 (high), P2 (medium), P3 (low)
7. Add notes for context
8. Update task table at bottom

---

## Notes on Task Estimation

- **Simple** (1–4 hours): File changes, single class, well-defined scope
- **Medium** (1 day): Multiple files, manager + handler + UI, moderate complexity
- **Complex** (2+ days): Multiple modules, refactoring, intricate logic

Current backlog is 9 person-days total at nominal velocity.
