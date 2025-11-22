# Copilot Instructions for ed-monitor

**ed-monitor** is a Java 21 multi-module desktop + server application for Elite Dangerous mission tracking, market data management, and route optimization. This guide accelerates AI agent productivity by documenting architecture, patterns, and critical workflows.

## Architecture Overview

### Three-Tier System
1. **UI Tier (ed-monitor-swing)**: Desktop client with tabbed interface, event handlers, managers
2. **Business Logic (BaseManager + Observer)**: Swing-based singletons notifying UI via listeners
3. **Event Source (JournalLogMonitor)**: Reads Elite Dangerous JSON logs, dispatches to handlers
4. **Backend (ed-monitor-server)**: Optional Spring Boot REST API for market persistence (H2)
5. **Domain (ed-monitor-core)**: Pure immutable entities (no dependencies)

**Data Flow**: `Journal.log` → `JournalLogMonitor` → `LogEventHandler` → `Manager.notifyListeners()` → `UI repaint()`

### Module Boundaries
- **ed-monitor-core**: Only domain entities (Commodity, Material, Market, ConstructionSite, Ship). Zero dependencies on Swing or Spring. Pure DTOs + mappers.
- **ed-monitor-server**: Stateless Spring Boot REST API, H2 persistence. Never references ed-monitor-swing at runtime.
- **ed-monitor-swing**: Desktop UI, all event handling, business logic managers, JournalLogMonitor file watching. Depends on ed-monitor-core only.

**Critical Rule**: No circular dependencies. Swing imports core; core never imports swing.

## Build & Development Workflows

### Standard Commands (PowerShell)
```powershell
mvn clean install              # Build all modules (parent + 3 submodules)
mvn clean test -Ptest          # Run tests with H2 in-memory DB
mvn exec:java                  # Run Swing client from workspace
mvn -q -DskipTests package     # Build fat jar (swing module)
java -Ded.server.baseUrl=http://localhost:8080 -jar target/ed-monitor-swing-0.3.0.jar  # Run with server
```

### Server (ed-monitor-server)
```powershell
cd ed-monitor-server
mvn spring-boot:run -Dspring-boot.run.profiles=prod  # Start REST API on :8080
# Health endpoints: GET /api/health, POST /api/markets, GET /api/markets/{id}
```

### Naming Conventions (Strictly Enforced)
| Category | Pattern | Example |
|----------|---------|---------|
| Event Handlers | `{Event}EventHandler` | `AsteroidProspectEventHandler` |
| Managers | `{Domain}Manager` | `DroneManager`, `ConstructionSiteManager` |
| UI Panels | `{Feature}Panel` | `DroneProvisionerPanel`, `ConstructionSitePanel` |
| Constants | `UPPER_SNAKE_CASE` | `COLOR_ORANGE`, `MAX_CARGO_CAPACITY` |
| Methods | camelCase, verb-first | `addListener()`, `notifyListeners()` |

## Observer Pattern (Core Design)

### BaseManager Contract (Every manager extends this)
```java
public abstract class BaseManager implements Observable {
    protected final List<Runnable> listeners = new CopyOnWriteArrayList<>();
    
    @Override
    public void addListener(Runnable listener) { listeners.add(listener); }
    
    @Override
    public void notifyListeners() {
        SwingUtilities.invokeLater(() -> listeners.forEach(Runnable::run));
    }
}
```

### UI → Manager → Listener Flow (Mandatory)
1. UI component (JPanel) gets manager singleton: `DefaultManagerFactory.getInstance().getAsteroidManager()`
2. Register listener in constructor: `asteroidManager.addListener(() -> { repaint(); })`
3. **NEVER query state directly**; always listen and react
4. When manager state changes, it calls `notifyListeners()` → EDT dispatches
5. Listener callback runs on EDT (thread-safe)

**Anti-pattern**: UI calling `manager.getCurrentState()` — leads to race conditions.

## Event Handler Registration & Dispatch

### Handler Lifecycle
1. Create class: `public class MyEventHandler implements LogEventHandler`
2. Return exact event type: `getEventType()` returns string like `"ProspectedAsteroid"` (case-sensitive)
3. Parse JSON defensively: use `event.optString()`, `event.optJSONArray()`, never `getString()`
4. Call manager method: handler does NOT modify UI directly
5. Register in `HandlerConfiguration.getLogEventHandlers()` (Map<String, LogEventHandler>)
6. JournalLogMonitor invokes: `dispatcher.dispatch(eventJson, handler)`

### Template: Add a New Event Handler
```java
public class ExampleEventHandler implements LogEventHandler {
    private final ExampleManager manager = DefaultManagerFactory.getInstance().getExampleManager();
    
    @Override
    public String getEventType() { return "YourEventType"; }  // Exact match to journal JSON
    
    @Override
    public void handleEvent(JSONObject event) {
        try {
            String field = event.optString("field", "default");
            manager.updateState(field);  // Manager notifies listeners
        } catch (Exception e) {
            System.err.println("Error in ExampleEventHandler: " + e.getMessage());
        }
    }
}
```

### Register in HandlerConfiguration
```java
@Override
public Map<String, LogEventHandler> getLogEventHandlers() {
    Map<String, LogEventHandler> handlers = new HashMap<>();
    handlers.put("YourEventType", new ExampleEventHandler());
    // ... other handlers
    return handlers;
}
```

## JSON Parsing Rules

**Always use defensive parsing** to avoid JSONException and silent failures:
```java
// ✓ Good
String eventType = event.optString("event", "Unknown");
JSONArray materials = event.optJSONArray("Materials");  // Returns null if missing
double proportion = event.optDouble("Proportion", 0.0);

// ✗ Bad (throws exception if missing)
String eventType = event.getString("event");
```

### Materials Array Example
```java
if (event.has("Materials")) {
    JSONArray materials = event.getJSONArray("Materials");
    for (int i = 0; i < materials.length(); i++) {
        JSONObject mat = materials.getJSONObject(i);
        String name = mat.optString("Name", "");
        double proportion = mat.optDouble("Proportion", 0.0);
    }
}
```

## DefaultManagerFactory Usage (Singleton Access)

### When to Use
- Desktop UI components: always use factory
- Event handlers: always use factory
- Tests: inject or mock instead

### Available Managers
```java
DroneManager dm = DefaultManagerFactory.getInstance().getDroneManager();
AsteroidManager am = DefaultManagerFactory.getInstance().getAsteroidManager();
CargoInventoryManager cm = DefaultManagerFactory.getInstance().getCargoInventoryManager();
ConstructionSiteManager csm = DefaultManagerFactory.getInstance().getConstructionSiteManager();
CommodityRegistry cr = DefaultManagerFactory.getInstance().getCommodityRegistry();
MarketDataUpdateService mdus = DefaultManagerFactory.getInstance().getMarketDataUpdateService();
```

## UI Component Guidelines (Swing)

### JPanel Subclass Rules
1. **Constructor**: Get managers, register listeners, layout GUI
2. **Thread Safety**: All Swing operations on EDT via `SwingUtilities.invokeLater()`
3. **No Blocking**: Never call blocking I/O or CPU-intensive code on EDT
4. **Listeners Fire repaint()**: Callback should call `repaint()` after updating state, never directly paint
5. **Avoid null Layout**: Use BorderLayout, FlowLayout, GridLayout

### Template: New UI Panel
```java
public class ExamplePanel extends JPanel {
    private final ExampleManager manager = DefaultManagerFactory.getInstance().getExampleManager();
    private JLabel statusLabel;
    
    public ExamplePanel() {
        setLayout(new BorderLayout(6, 6));
        statusLabel = new JLabel("Idle");
        add(statusLabel, BorderLayout.CENTER);
        
        // Register listener
        manager.addListener(() -> {
            statusLabel.setText("Updated: " + manager.getStatus());
            repaint();
        });
    }
}
```

## File Monitoring & Journal Log Reading

- **JournalLogMonitor**: Watches Elite Dangerous journal directory (via NIO WatchService)
- **AppendFileReadStrategy**: Only reads newly appended lines (efficient, avoids re-parsing)
- **Event Parsing**: Each new line is parsed as JSON; event type extracted
- **Dispatch**: Handler lookup by event type; invoked with JSON object

### Configuration
- Journal path: typically `%APPDATA%\Roaming\Frontier Developments\Elite Dangerous\Logs`
- Monitored via WatchService; on file change, new content parsed incrementally

## Graceful Shutdown & Lifecycle (TASKS.md Focus)

**Current Limitation**: App exit does not reliably stop the backend server. Known issues:
- `MonitorController.stopAll()` doesn't track external server instances
- Health-check-detected servers aren't captured for later termination
- Daemon lifecycle executor can exit JVM before stop completes

**Planned Fixes** (from TASKS.md):
1. Harden shutdown: await backend stop with timeouts
2. Track external instances: capture PID when health-check detects already-running server
3. Multi-instance stop: terminate all ed-monitor-server JVMs (graceful + forcible kill)
4. Surface stop progress to UI
5. Add regression tests for lifecycle start/stop

**Impact for Agents**: If modifying lifecycle code, ensure `System.exit()` waits for backend termination.

## Testing Requirements

### Unit Tests
- Handlers: mock manager, verify correct method calls + JSON parsing
- Managers: verify listener notification on state change
- Utilities: standard JUnit 5 assertions

### Integration Tests
- Use actual JournalLogMonitor with test log file
- Verify event → handler → manager → UI listener flow

### Test Profile
```powershell
mvn clean test -Ptest  # Uses H2 in-memory DB, no Spring server startup
```

## Project Structure Quick Reference

```
ed-monitor/
├── pom.xml (parent, Maven 3+, Java 21)
├── README.md (user-facing overview)
├── TASKS.md (active work items, lifecycle bugfix plan)
├── Docs/
│   ├── AGENTS.md (detailed conventions, anti-patterns, checklist)
│   ├── ARCHITECTURE.md (system design, patterns, module details)
│   ├── EVENT_TYPES.md (Elite Dangerous event JSON structure)
│   └── (other design docs)
├── ed-monitor-core/
│   ├── pom.xml (domain model only)
│   └── src/main/java/com/zergatstage/ (entities: Commodity, Material, Market, ConstructionSite, Ship)
├── ed-monitor-server/
│   ├── pom.xml (Spring Boot 3.5.7)
│   ├── src/main/java/ (REST API, persistence)
│   └── src/main/resources/ (application*.yaml, Market.json sample)
├── ed-monitor-swing/
│   ├── pom.xml (fat jar config)
│   ├── src/main/java/com/zergatstage/monitor/
│   │   ├── MonitorView (main JFrame, tabs)
│   │   ├── MonitorController (lifecycle start/stop)
│   │   ├── component/ (UI panels)
│   │   ├── handlers/ (LogEventHandler impls + HandlerConfiguration)
│   │   ├── service/
│   │   │   ├── managers/ (BaseManager subclasses)
│   │   │   ├── JournalLogMonitor (file watching)
│   │   │   └── StatusMonitor (health checks)
│   │   ├── factory/ (DefaultManagerFactory)
│   │   ├── theme/ (FlatLaf, dark/light, ThemeManager)
│   │   └── config/ (UiConstants, DisplayConfig)
│   └── src/test/java/com/zergatstage/monitor/ (unit & integration tests)
└── slim-runtime/ (embedded JVM for packaging, rarely touched)
```

## Quick Start for Agents

**New Feature (e.g., new event type)**:
1. Create `NewThingEventHandler implements LogEventHandler`
2. Register in `HandlerConfiguration.getLogEventHandlers()`
3. Create/update `NewThingManager extends BaseManager`
4. Create UI panel (e.g., `NewThingPanel extends JPanel`), register listener
5. Add unit test for handler + manager
6. Update TASKS.md with completion

**Bug Fix**:
1. Reproduce via test or find existing test file
2. Add failing test case
3. Fix manager/handler logic
4. Ensure listener chain still works (manager → UI)
5. Run `mvn clean test -Ptest` before committing

**UI Tweak**:
1. Edit component in `component/` package
2. Ensure all Swing operations use `SwingUtilities.invokeLater()`
3. Test with both dark/light themes (ThemeManager)

## Key Files to Know

- **Docs/AGENTS.md**: Expanded rules, anti-patterns, checklist (11 sections)
- **Docs/ARCHITECTURE.md**: Detailed design patterns, module deep-dive (4+ sections)
- **Docs/EVENT_TYPES.md**: Elite Dangerous JSON event schemas
- **TASKS.md**: Current work items (lifecycle bugfix is active focus)
- **MonitorView.java**: Main JFrame, tab orchestration
- **DefaultManagerFactory.java**: Singleton manager access
- **HandlerConfiguration.java**: Event handler registry
- **BaseManager.java**: Observer pattern base class

## Version & Tools

- **Java 21** (LTS, required)
- **Maven 3.8+** (parent aggregator)
- **Spring Boot 3.5.7** (server module only)
- **Lombok 1.18.36** (heavy use: @Slf4j, @Data, @Builder)
- **FlatLaf** (modern Swing themes)
- **H2 Database** (persistence, both in-memory + file modes)
- **JUnit 5** + Mockito (testing)
- **OkHttp** (market data HTTP sync)
- **SLF4J** (logging)

---

**Last Updated**: Nov 2025  
**Branch**: bugfix/stop-server-gracefully (lifecycle improvements in progress)
