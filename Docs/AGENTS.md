# AGENTS.md — Rules & Conventions for Agent-Driven Development

This document specifies the rules, patterns, and constraints agents must follow when working on ed-monitor code.

---

## 1. Code Style & Naming Conventions

### Java Standards
- **Language**: Java 11+ (uses var, lombok, lambda expressions)
- **Build Tool**: Maven (pom.xml)
- **Dependency Management**: Managed in parent pom.xml; submodules inherit
- **Lombok**: Heavy use of `@Slf4j`, `@Data`, `@Builder` — do not bypass

### Naming Rules
| Element | Rule | Example |
|---------|------|---------|
| **Classes** | PascalCase, suffixed by responsibility | `AsteroidManager`, `AsteroidProspectEventHandler`, `ConstructionSitePanel` |
| **Event Handlers** | `{Event}EventHandler` | `DroneLaunchEventHandler`, `CargoInventoryEventHandler` |
| **Managers** | `{Domain}Manager` | `DroneManager`, `AsteroidManager`, `ConstructionSiteManager` |
| **UI Panels** | `{Feature}Panel` | `ConstructionSitePanel`, `DroneProvisionerPanel` |
| **Interfaces** | Semantic (Observable, LogEventHandler) or component-based | N/A |
| **Methods** | camelCase, verb-first | `addListener()`, `notifyListeners()`, `getEventType()` |
| **Constants** | UPPER_SNAKE_CASE | `COLOR_GRAY`, `COLOR_ORANGE`, `TITLE` |

---

## 2. Module Boundaries & Responsibilities

### ed-monitor-core
- **Responsibility**: Domain entities (immutable or minimal logic)
- **Contains**: `ConstructionSite`, `Ship`, `Commodity`, `Material`, `Market` classes
- **Rule**: No dependencies on swing or spring (pure domain model)
- **When to change**: New entity types, new domain constraints

### ed-monitor-server
- **Responsibility**: Spring Boot REST API for market/construction site data
- **Contains**: Controllers, services, persistence layer
- **Rule**: Stateless; no UI code
- **When to change**: New REST endpoints, data sync logic

### ed-monitor-swing
- **Responsibility**: Desktop UI, event handlers, business logic (managers), service layer
- **Contains**: 
  - `component/`: JPanel subclasses (UI)
  - `handlers/`: LogEventHandler implementations
  - `service/managers/`: Business logic (Observer pattern)
  - `factory/`: DefaultManagerFactory
  - `config/`: UI constants, configurations
- **Rule**: All GUI updates happen on EDT (Swing thread); use `SwingUtilities.invokeLater()`
- **When to change**: New UI features, event handling, business logic

### Cross-Module Communication
```
[ed-monitor-core]  ←  [ed-monitor-server]  (uses entities)
       ↑
[ed-monitor-swing]  (uses core entities, ignores server at runtime)
```

---

## 3. Observer Pattern Enforcement

### BaseManager Contract
All business logic managers extend `BaseManager` and implement `Observable`:

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

### Rules
1. **Listener Registration**: UI components register with managers via `addListener(Runnable)`
2. **Notification**: When state changes, call `notifyListeners()` — dispatches to EDT
3. **No Direct State Access**: UI does NOT query manager state directly; listen and react
4. **Thread Safety**: Use CopyOnWriteArrayList; invocation always on EDT

### Example: DroneProvisionerPanel
```java
public DroneProvisionerPanel() {
    this.asteroidManager = DefaultManagerFactory.getInstance().getAsteroidManager();
    
    // Register as listener
    asteroidManager.addListener(() -> {
        double proportion = asteroidManager.getLastTritiumProportion();
        updateUI(proportion);
        repaint();
    });
}
```

---

## 4. Event Handler Registration & Dispatch Flow

### Handler Lifecycle
```
1. Create class implementing LogEventHandler
2. Register in HandlerConfiguration.getLogEventHandlers()
3. JournalLogMonitor invokes Dispatcher.dispatch(event, handler)
4. Handler calls manager methods (e.g., manager.updateXxx(event))
5. Manager notifies listeners (UI repaints)
```

### LogEventHandler Contract
```java
public interface LogEventHandler {
    default boolean isCargoRelated() { return false; }
    
    String getEventType();  // "ProspectedAsteroid", "LaunchDrone", etc.
    void handleEvent(JSONObject event);
}
```

### Rules for New Handlers
1. Return exact event type string (case-sensitive): `"ProspectedAsteroid"`, not `"prospected_asteroid"`
2. Wrap `handleEvent()` in try-catch; log errors
3. Extract JSON fields defensively: `event.optString("field", default)`
4. Call manager methods; do NOT update UI directly
5. Register in `HandlerConfiguration` (NOT auto-discovery unless explicitly enabled)

### Example: AsteroidProspectEventHandler
```java
public class AsteroidProspectEventHandler implements LogEventHandler {
    private final AsteroidManager asteroidManager;
    
    @Override
    public String getEventType() { return "ProspectedAsteroid"; }
    
    @Override
    public void handleEvent(JSONObject event) {
        try {
            if (event.has("Materials")) {
                asteroidManager.updateProspectingLabel(event);
            }
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
        }
    }
}
```

---

## 5. DefaultManagerFactory Usage

### Rule
Use `DefaultManagerFactory.getInstance()` to obtain singleton manager instances:

```java
DroneManager droneManager = DefaultManagerFactory.getInstance().getDroneManager();
AsteroidManager asteroidManager = DefaultManagerFactory.getInstance().getAsteroidManager();
```

### When NOT to use
- **Tests**: Use dependency injection or mocks instead
- **Backend (Spring)**: Inject via Spring DI; don't use factory

### Available Managers
| Method | Returns | Singleton? |
|--------|---------|-----------|
| `getDroneManager()` | DroneManager | Yes |
| `getAsteroidManager()` | AsteroidManager | Yes |
| `getCargoInventoryManager()` | CargoInventoryManager | Yes |
| `getConstructionSiteManager()` | ConstructionSiteManager | Yes |
| `getCommodityRegistry()` | CommodityRegistry | Yes |
| `getMarketDataParser()` | MarketDataParser | No (utility) |
| `getMarketDataUpdateService()` | MarketDataUpdateService | Yes |

---

## 6. UI Component Guidelines

### JPanel Subclass Rules
1. **Constructor**: Initialize UI, get manager instances, register listeners
2. **Layout**: Use BorderLayout, FlowLayout, GridLayout (avoid null layout)
3. **Thread Safety**: All Swing operations on EDT; use `SwingUtilities.invokeLater()` for background callbacks
4. **Repaint**: Call `repaint()` from listener callbacks, never from event threads
5. **No Blocking**: Never block EDT with network I/O or computation

### Example: DroneProvisionerPanel
```java
public class DroneProvisionerPanel extends JPanel {
    private final JComboBox<String> metalCombo;
    private final DrawingPanel drawingPanel;
    
    public DroneProvisionerPanel() {
        setLayout(new BorderLayout(6, 6));
        
        // Top: controls
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        metalCombo = new JComboBox<>(new String[]{"Tritium", "Platinum", "Painite"});
        top.add(metalCombo);
        add(top, BorderLayout.NORTH);
        
        // Center: drawing area
        drawingPanel = new DrawingPanel();
        add(drawingPanel, BorderLayout.CENTER);
        
        // Register listener
        asteroidManager.addListener(() -> drawingPanel.repaint());
    }
    
    // Inner class for custom rendering
    private class DrawingPanel extends JPanel {
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            // Custom rendering logic
        }
    }
}
```

---

## 7. JSON Event Parsing Rules

### Defensive Parsing
Always use `opt*()` methods to avoid JSONException:

```java
// Good
String eventType = event.optString("event", "Unknown");
JSONArray materials = event.optJSONArray("Materials");
double proportion = event.optDouble("Proportion", 0.0);

// Bad (throws exception if missing)
String eventType = event.getString("event");
```

### Event Type Detection
```java
String eventType = event.optString("event");
if ("ProspectedAsteroid".equals(eventType)) { ... }
```

### Materials Array Parsing
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

---

## 8. Testing Requirements

### Unit Testing
- Handlers: Mock manager, verify correct method calls
- Managers: Verify observer notification
- UI Panels: Use Headless AWT or mock SwingUtilities

### Integration Testing
- Use actual JournalLogMonitor with test log file
- Verify end-to-end flow: event → handler → manager → UI notification

### No Manual Testing in Production Code
- Do not add `main()` methods to handlers or managers
- Create separate test classes instead

---

## 9. Git & Branching Rules

### Branch Naming
```
feature/{feature-name}      # New features
bugfix/{bug-name}           # Bug fixes
refactor/{refactor-name}    # Code refactoring
docs/{doc-name}             # Documentation
```

### Commit Rules
- Atomic commits (one logical change per commit)
- Message format: `[ModuleName] Brief description`
  - Example: `[ed-monitor-swing] Add DroneProvisionerPanel observer pattern`
- Test before commit

### PR Requirements
- Link to TASKS.md task (if applicable)
- Acceptance criteria fulfilled
- No breaking API changes without deprecation

---

## 10. Common Pitfalls to Avoid

| Pitfall | Why It's Bad | Fix |
|---------|-------------|-----|
| Direct state query from UI | Race conditions, stale data | Always listen and react |
| Blocking EDT thread | Frozen UI | Use ExecutorService for background work |
| Hardcoded strings | Brittle event handling | Use constants in UiConstants or manager classes |
| Catching Exception (bare) | Silently hides bugs | Catch specific exceptions, log to slf4j |
| Circular manager dependencies | Deadlock risk | Use dependency injection or factory pattern |
| Multiple JPanel instances of same manager | Observer callback spam | Singleton managers only |

---

## 11. When to Ask for Clarification

Ask (don't guess) if:
1. Event structure (JSON fields) is undocumented → check EVENT_TYPES.md
2. Manager listener API unclear → check BaseManager and specific manager implementation
3. UI component layout conflicts → check MonitorView
4. Event handler priority/ordering matters → check HandlerConfiguration
5. Cross-module visibility needed → discuss in TASKS.md

---

## Quick Checklist for New Code

- [ ] Follows naming conventions (PascalCase classes, camelCase methods)
- [ ] Event handler returns exact event type string
- [ ] Manager extends BaseManager, notifies listeners on state change
- [ ] UI component registers as listener, never queries state directly
- [ ] JSON parsing uses defensive `opt*()` methods
- [ ] All Swing operations on EDT via SwingUtilities.invokeLater()
- [ ] No blocking operations in listeners
- [ ] Try-catch around event handler logic with logging
- [ ] TASKS.md updated with any new requirements
- [ ] No direct instantiation of managers (use DefaultManagerFactory)
