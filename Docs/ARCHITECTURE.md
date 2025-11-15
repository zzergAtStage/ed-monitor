# ARCHITECTURE.md — System Design Overview

Current architecture of ed-monitor as of Nov 2025.

---

## 1. High-Level System Design

### Three-Tier Architecture

```
┌─────────────────────────────────────────────────────────────┐
│  TIER 1: UI (Desktop Client)                                │
│  [MonitorView] ← JFrame with tabbed interface               │
│  ├─ [ConstructionSitePanel] (construction site tracking)    │
│  ├─ [DroneProvisionerPanel] (asteroid mineral analysis)     │
│  └─ [Other UI components]                                  │
└─────────────────────────────────────────────────────────────┘
                            ↑ Observer Pattern
┌─────────────────────────────────────────────────────────────┐
│  TIER 2: Business Logic & Event Processing (Swing Module)  │
│  [Managers] — extend BaseManager, implement Observer       │
│  ├─ DroneManager                                            │
│  ├─ AsteroidManager                                         │
│  ├─ CargoInventoryManager                                  │
│  ├─ ConstructionSiteManager                                │
│  └─ (others)                                                │
│                                                              │
│  [Event Handlers] — implement LogEventHandler              │
│  ├─ DroneLaunchEventHandler                                │
│  ├─ AsteroidProspectEventHandler                           │
│  ├─ CargoInventoryEventHandler                             │
│  └─ (10+ more)                                              │
└─────────────────────────────────────────────────────────────┘
                            ↑ (event dispatch)
┌─────────────────────────────────────────────────────────────┐
│  TIER 3: Journal Event Source                              │
│  [JournalLogMonitor] — reads Elite Dangerous journal logs  │
│  ├─ GenericFileMonitor (file system watching)              │
│  ├─ AppendFileReadStrategy (only new appended content)     │
│  └─ → JSONObject per line (event type extracted)           │
└─────────────────────────────────────────────────────────────┘
```

### Data Flow Example: Asteroid Prospect Event

```
Journal.log contains:
  { "timestamp": "...", "event": "ProspectedAsteroid", "Materials": [...] }
         ↓
JournalLogMonitor reads new line
         ↓
Parses JSON → { "event": "ProspectedAsteroid", ... }
         ↓
Looks up handler in Map<String, LogEventHandler>
         ↓
Dispatcher calls AsteroidProspectEventHandler.handleEvent(json)
         ↓
Handler extracts Materials, calls AsteroidManager.updateProspectingLabel(json)
         ↓
AsteroidManager.notifyListeners() (on EDT)
         ↓
DroneProvisionerPanel listener fires → repaint() with new colors
```

---

## 2. Module Structure

### ed-monitor-core
- **Maven Module**: `<artifactId>ed-monitor-core</artifactId>`
- **Responsibility**: Domain model (immutable entities)
- **Key Classes**:
  - `ConstructionSite` (site details, depot info, material requirements)
  - `Ship` (ship characteristics: cargo, modules)
  - `Commodity` (ID, category, name, price history)
  - `Material` (asteroid material type, proportion)
  - `Market` (market data, commodities)
- **Dependencies**: Only stdlib + lombok
- **No UI, No Swing, No Handlers**

### ed-monitor-server
- **Maven Module**: `<artifactId>ed-monitor-server</artifactId>`
- **Responsibility**: Spring Boot REST API backend
- **Key Classes**:
  - REST Controllers (market data, construction sites)
  - Services (market sync, site data)
  - Persistence (if any)
- **Dependencies**: Spring Boot, ed-monitor-core
- **Does NOT interact** with ed-monitor-swing (separate runtime)
- **Current Status**: Early stage, not used by desktop client

### ed-monitor-swing
- **Maven Module**: `<artifactId>ed-monitor-swing</artifactId>`
- **Responsibility**: Desktop UI, event processing, business logic
- **Package Structure**:

```
com.zergatstage.monitor/
├── MonitorView                   (main JFrame, tab layout)
├── MonitorController             (lifecycle control: start, stop, exit)
├── component/                    (JPanel subclasses)
│   ├── ConstructionSitePanel     (site progress UI)
│   ├── DroneProvisionerPanel      (asteroid color display)
│   ├── CommodityManagerComponent  (commodity table)
│   └── (others)
├── handlers/                     (LogEventHandler implementations)
│   ├── AsteroidProspectEventHandler
│   ├── DroneLaunchEventHandler
│   ├── CargoInventoryEventHandler
│   ├── CargoUpdateEventHandler
│   ├── LoadoutEventHandler
│   ├── LocationEventHandler
│   ├── DockedEventHandler
│   ├── MarketBuyEventHandler
│   ├── MarketSellEventHandler
│   ├── ColonisationContributionEventHandler
│   ├── ColonisationConstructionDepotEventHandler
│   └── HandlerConfiguration     (registry + factory)
├── service/                      (business logic layer)
│   ├── managers/                 (extend BaseManager)
│   │   ├── DroneManager
│   │   ├── AsteroidManager
│   │   ├── CargoInventoryManager
│   │   ├── ConstructionSiteManager
│   │   └── (others)
│   ├── JournalLogMonitor         (journal file reader)
│   ├── BaseManager               (abstract, Observer pattern)
│   ├── CommodityRegistry         (commodity data)
│   ├── MarketDataUpdateService   (HTTP market sync)
│   ├── ConstructionSiteManager   (site data & tracking)
│   └── StatusMonitor             (game status via local HTTP)
├── factory/                      (singletons)
│   └── DefaultManagerFactory     (provides manager instances)
├── config/                       (constants, UI properties)
│   ├── UiConstants               (menu strings, window titles)
│   ├── DisplayConfig             (layout sizes, positions)
│   └── LogMonitorConfig          (journal directory config)
└── (main app class, likely in App.java or similar)
```

- **Dependencies**: ed-monitor-core, Swing (JDK), JSON-org, Lombok, SLF4J, Spring Boot client libs
- **Thread Model**:
  - EDT (Swing thread) for all UI updates
  - ExecutorService for journal file parsing (non-blocking)
  - Managers use CopyOnWriteArrayList for thread-safe listener lists

---

## 3. Key Design Patterns

### Observer Pattern (Manager ↔ UI)

**Participants**:
- `Observable` interface: `addListener(Runnable)`, `removeListener(Runnable)`, `notifyListeners()`
- `BaseManager` (abstract): implements Observable, maintains listener list
- Concrete managers (DroneManager, AsteroidManager, etc.): extend BaseManager
- UI components (JPanel subclasses): register as listeners

**Example**:
```java
// In UI component
asteroidManager.addListener(() -> {
    // Called when state changes (on EDT)
    updateDisplay();
    repaint();
});

// In manager
void updateProspectingLabel(JSONObject event) {
    // ... update internal state ...
    notifyListeners(); // Triggers all registered listeners on EDT
}
```

### Factory Pattern (DefaultManagerFactory)

**Responsibility**: Singleton creation of all managers
**Usage**: `DefaultManagerFactory.getInstance().getDroneManager()`
**Current Limitation**: Hardcoded; could be refactored to use Spring or config file

### Strategy Pattern (FileReadStrategy)

**Participants**:
- `FileReadStrategy` interface: encodes how to read file (all vs. append-only)
- `AppendFileReadStrategy`: only reads new appended lines (efficient for journal logs)

**Usage**: JournalLogMonitor uses strategy to avoid re-reading entire log

### Handler Registry Pattern (Event Dispatch)

**Participants**:
- `LogEventHandler` interface: `getEventType()`, `handleEvent(JSONObject)`
- `HandlerConfiguration`: returns `Map<String, LogEventHandler>` keyed by event type
- `JournalLogMonitor`: looks up handler by event type, dispatches

**Flow**:
```
event JSON: { "event": "ProspectedAsteroid", ... }
  ↓
JournalLogMonitor queries handlers.get("ProspectedAsteroid")
  ↓
Gets AsteroidProspectEventHandler
  ↓
Calls handler.handleEvent(json)
```

---

## 4. Event Flow (Detailed Sequence)

### Phase 1: Journal Monitoring
```
1. MonitorController.startAll()
   ├─ JournalLogMonitor.startMonitoring()
   │  └─ GenericFileMonitor polls journal directory every N seconds
   │
2. GenericFileMonitor detects new file or appended content
   ├─ Invokes FileReadStrategy.readNewLines()
   │  └─ AppendFileReadStrategy returns only new appended lines
   │
3. JournalLogMonitor.processAppendedLines(lines)
   ├─ For each line:
   │  ├─ Parse JSON
   │  ├─ Extract event type: event.optString("event")
   │  └─ Submit to parsingPool (ExecutorService, non-blocking)
   │
4. JournalLogMonitor.processLine(line)
   ├─ Create JSONObject
   ├─ Look up handler: handlers.get(eventType)
   └─ Dispatcher.dispatch(json, handler) if found
```

### Phase 2: Event Handling
```
5. Dispatcher.dispatch(json, handler)
   └─ handler.handleEvent(json)
      │
      └─ AsteroidProspectEventHandler.handleEvent(json)
         ├─ Extract Materials array
         ├─ For each material: find desired metal (Tritium, etc.)
         └─ Call asteroidManager.updateProspectingLabel(json)

6. Manager updates state
   └─ AsteroidManager.updateProspectingLabel(json)
      ├─ Parse materials
      ├─ Update internal state (lastProportions, etc.)
      └─ Call notifyListeners()

7. Manager notifies all listeners (on EDT)
   └─ SwingUtilities.invokeLater()
      └─ For each Runnable listener: listener.run()
```

### Phase 3: UI Update
```
8. UI listener executes (on EDT)
   └─ DroneProvisionerPanel listener: () -> { updateUI(); repaint(); }
      ├─ Read latest asteroid data from manager
      ├─ Determine color based on material percentage
      └─ Schedule repaint()

9. JPanel.paintComponent(Graphics g)
   ├─ Draw background
   ├─ Draw 3 colored rectangles (most recent first, top to bottom)
   ├─ Draw labels (metal name, percentage)
   └─ Update display
```

---

## 5. Concurrency Model

### Swing EDT (Event Dispatch Thread)
- All UI updates happen here
- No blocking operations allowed
- Managers notify listeners via `SwingUtilities.invokeLater()`

### Journal Parsing (ExecutorService)
- Parses JSON in background thread
- Does NOT update UI directly
- Calls manager methods (thread-safe via CopyOnWriteArrayList)

### Listener Invocation
- Listeners are `Runnable` objects
- Always invoked on EDT via `SwingUtilities.invokeLater()`
- Safe for UI operations

### Thread Safety
- Managers use `CopyOnWriteArrayList` for listener lists (thread-safe iteration)
- No synchronized blocks; prefer immutable state
- Managers should be treated as singletons (DefaultManagerFactory enforces this)

---

## 6. Current Component Dependencies

```
MonitorView
├─ MonitorController (controls lifecycle)
├─ ConstructionSitePanel
│  ├─ ConstructionSiteManager (observer)
│  ├─ CargoInventoryManager (observer)
│  └─ CommodityRegistry
│
└─ DroneProvisionerPanel
   ├─ DroneManager (observer)
   └─ AsteroidManager (observer)

JournalLogMonitor
├─ GenericFileMonitor
├─ AppendFileReadStrategy
├─ Dispatcher (callback)
└─ HandlerConfiguration (provides handlers)
   ├─ DroneLaunchEventHandler
   │  └─ DroneManager
   ├─ AsteroidProspectEventHandler
   │  └─ AsteroidManager
   ├─ CargoInventoryEventHandler
   │  └─ CargoInventoryManager
   ├─ CargoUpdateEventHandler
   │  └─ ConstructionSiteManager
   └─ (8 more handlers...)

DefaultManagerFactory (Singleton)
├─ DroneManager
├─ AsteroidManager
├─ CargoInventoryManager
├─ ConstructionSiteManager
├─ CommodityRegistry
├─ MarketDataParser
└─ MarketDataUpdateService
```

---

## 7. Current Limitations & Known Issues

| Issue | Impact | Root Cause |
|-------|--------|-----------|
| Hardcoded handler registration | Difficult to extend | HandlerConfiguration manually lists handlers instead of using discovery |
| No persistence | Data lost on restart | Managers are in-memory only; no database |
| Market data double-parsing | Performance | JSON parsed twice (once for structure, once for data) |
| No async market fetch | UI freezes briefly | MarketDataUpdateService likely synchronous |
| Cargo merge logic incomplete | Feature incomplete | TODO in CargoUpdateEventHandler (manual merge stub) |
| Construction site WIP | Feature incomplete | TODO in ConstructionSiteManager |
| No event filtering/priority | All events processed equally | No event queue or prioritization |
| Circular dependencies possible | Risk of deadlock | Managers can reference each other; no DI framework |

---

## 8. Test Architecture (Current)

- **Unit Tests**: Located in `src/test/java/` per module
- **Integration Tests**: Full JournalLogMonitor with test log file (not present yet)
- **No E2E Tests**: Desktop app tested manually

### Test Example Needed
```java
@Test
void testAsteroidProspectEventHandler() {
    AsteroidProspectEventHandler handler = new AsteroidProspectEventHandler(mockManager);
    JSONObject event = new JSONObject("{ ... }");
    handler.handleEvent(event);
    verify(mockManager).updateProspectingLabel(event);
}
```

---

## 9. Integration with ed-monitor-server (Future)

**Current Status**: Server exists but desktop client does NOT use it

**Planned Integration**:
```
ed-monitor-swing (desktop)
   ↓ (REST calls)
ed-monitor-server (Spring Boot API)
   ↓ (queries)
ed-monitor-core (domain model)
```

**Not yet implemented**: Market data fetch, construction site sync via server

---

## 10. Configuration Files

| File | Location | Purpose |
|------|----------|---------|
| `pom.xml` | Repository root | Parent Maven build config |
| `pom.xml` | Each module | Module-specific dependencies, build plugins |
| `logback.xml` | `ed-monitor-swing/src/main/resources/` | SLF4J logging configuration |
| `application.yaml` | `ed-monitor-swing/src/main/resources/` | App properties (dev/test/prod profiles) |
| `UiConstants.java` | `com.zergatstage.monitor.config` | UI strings, window titles, menu labels |
| `DisplayConfig.java` | `com.zergatstage.monitor.config` | Layout sizes, positions, colors |

---

## 11. Build & Deployment

### Build
```bash
mvn clean install  # All modules
mvn -DskipTests clean install  # Skip tests
```

### JAR Output
- `ed-monitor-swing/target/ed-monitor-swing-1.0-SNAPSHOT.jar` (desktop client)
- `ed-monitor-server/target/ed-monitor-server-1.0-SNAPSHOT.jar` (REST API server)

### Runtime (Desktop)
```bash
java -jar ed-monitor-swing-1.0-SNAPSHOT.jar
```
Requires: JDK 11+, Elite Dangerous journal.log in standard location

---

## Quick Reference: Where to Add/Change

| Requirement | File(s) to Modify |
|-------------|-------------------|
| **New event handler** | Create `handlers/{EventName}EventHandler.java`, register in `HandlerConfiguration.getLogEventHandlers()` |
| **New manager** | Create `service/managers/{Domain}Manager.java` extending `BaseManager`, add to `DefaultManagerFactory` |
| **New UI panel** | Create `component/{Feature}Panel.java` extending `JPanel`, add to `MonitorView.buildUI()` |
| **New menu item** | Edit `MonitorView.createMenuBar()` |
| **New constant** | Add to `config/UiConstants.java` or `config/DisplayConfig.java` |
| **Listener API** | Edit `service/BaseManager` (affects all managers) |
| **Event structure** | Document in `docs/EVENT_TYPES.md` |
