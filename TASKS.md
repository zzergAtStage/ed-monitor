# TASKS.md — Agent Task Register

Task log derived from the blueprint in `Docs/TASKS.md`. Use this file for the active workstream around server management tooling.

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

### T-101: Backend Lifecycle Service

**Description**: Provide a property-driven service that can start, stop, restart, and probe the Spring Boot backend without blocking the Swing UI.

**Acceptance Criteria**:
- [x] Loads defaults from `server-management.properties` and optional override path
- [x] Launches the jar asynchronously (java -jar …) and keeps track of the process handle
- [x] Implements stop/restart semantics with graceful timeout enforcement
- [x] Exposes `checkHealth()` returning `ServerHealthState` values

**Scope**:
- Files involved: `ed-monitor-swing/src/main/java/com/zergatstage/monitor/config/ServerManagementProperties.java`, `service/server/*`
- Modules: ed-monitor-swing
- Estimated complexity: Medium

**Constraints & Assumptions**:
- No blocking calls on EDT; rely on dedicated executor
- Health endpoint uses HTTP/JSON semantics

**Priority**: P1 (high)

**Notes**:
- Override path provided via `-Dserver.management.config` or `SERVER_MANAGEMENT_CONFIG`

---

### T-102: Server Management Menu

**Description**: Introduce a reusable component that builds the “Server management” menu with Start/Stop/Restart/Check Health actions and integrates it into `MonitorView`.

**Acceptance Criteria**:
- [x] Menu exposes Start backend, Stop backend, Restart backend, Check health entries
- [x] Each action delegates to `ServerLifecycleService` via asynchronous completables
- [x] User feedback delivered through Swing dialogs without blocking EDT
- [x] Menu constructor lives in `component` package for reuse

**Scope**:
- Files involved: `component/ServerManagementMenu.java`, `MonitorView.java`, `UiConstants.java`
- Modules: ed-monitor-swing
- Estimated complexity: Medium

**Constraints & Assumptions**:
- Menu must be embeddable into any `JMenuBar`
- Feedback messages should remain concise and actionable

**Priority**: P1 (high)

**Notes**:
- Uses the controller’s lifecycle service accessor

---

### T-103: Server Health Indicator

**Description**: Define a four-state health indicator class plus unit tests to document color semantics for Online (green), Unhealthy (yellow), Down (gray), and Error (red).

**Acceptance Criteria**:
- [x] `ServerHealthIndicator` maps every `ServerHealthState` to a color/label pair
- [x] Null states default to “Down” indicator
- [x] JUnit 5 tests cover every state and the null fallback behavior

**Scope**:
- Files involved: `service/server/ServerHealthIndicator.java`, corresponding test
- Modules: ed-monitor-swing
- Estimated complexity: Simple

**Constraints & Assumptions**:
- Indicator not yet wired into UI panels

**Priority**: P2 (medium)

**Notes**:
- Colors follow the palette noted in the feature request


---

### T-104: Spring Boot Health Endpoint (Server-Side)

**Description**: Implement a Spring Boot health check endpoint in `ed-monitor-server` using Spring Boot Actuator to expose application health status (UP, DOWN, DEGRADED). The Swing client (T-101) polls this endpoint via HTTP to determine backend availability.

**Acceptance Criteria**:
- [x] Enable Spring Boot Actuator dependency in `ed-monitor-server/pom.xml`
- [x] Configure `management.endpoints.web.exposure.include` to expose `/actuator/health` endpoint (read-only, no auth required for MVP)
- [x] Custom `HealthIndicator` component extends `AbstractHealthIndicator` and checks:
  - [x] Database connectivity (if persistence layer exists; else assume UP)
  - [x] External service dependencies (market data API, etc.; skip if not used)
  - [x] Application readiness (e.g., data cache loaded)
- [x] Response format: `{ "status": "UP", "components": { ... } }` (standard Actuator format)
- [x] HTTP response code: 200 (UP), 503 (DOWN/DEGRADED) per Spring conventions
- [x] Unit test: Mock dependencies, verify health states (UP, DOWN, DEGRADED map correctly)
- [x] Integration test: Start server, curl `/actuator/health`, verify response structure and codes

**Scope**:
- Files involved: 
  - `ed-monitor-server/pom.xml` (add actuator dependency)
  - `ed-monitor-server/src/main/java/com/zergatstage/monitor/server/health/CustomHealthIndicator.java` (new)
  - `ed-monitor-server/src/main/resources/application.yaml` (configure actuator exposure)
  - Test class: `CustomHealthIndicatorTest.java`
- Modules: ed-monitor-server
- Estimated complexity: Simple

**Constraints & Assumptions**:
- Spring Boot Actuator is standard; no custom HTTP server needed
- Health endpoint must be publicly readable (no auth) to support remote polling
- Response must include timestamp and consistent status values (UP, DOWN, DEGRADED)
- No blocking operations; health checks should complete in < 500ms

**Priority**: P1 (high)

**Notes**:
- Yes, this uses Spring Boot Actuator (`/actuator/health` is the standard endpoint)
- T-101 (Swing client) will HTTP GET this endpoint every N seconds to populate ServerHealthState
- If database/external services don't exist yet, health indicator can always return UP (with note in response)
- Actuator provides built-in indicators; custom ones extend `AbstractHealthIndicator` and override `doHealthCheck(Health.Builder builder)`
