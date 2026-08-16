# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

```powershell
# Full build (required before running a single module in isolation)
.\mvnw clean install -DskipTests

# Run Swing desktop client (from repo root)
.\mvnw -pl ed-monitor-swing -am exec:java

# Build fat jar
.\mvnw -pl ed-monitor-swing -am -q -DskipTests package
java -Ded.server.baseUrl=http://localhost:50080 -jar ed-monitor-swing/target/ed-monitor-swing-0.3.1.jar

# Run all tests
.\mvnw test

# Run tests for one module only
.\mvnw -pl ed-monitor-swing test

# Run a single test class
.\mvnw -pl ed-monitor-swing -Dtest=ColonisationConstructionDepotTest test

# Start backend server
.\mvnw -pl ed-monitor-server spring-boot:run -Dspring-boot.run.profiles=prod
```

> `-pl ed-monitor-swing exec:java` alone fails if `ed-monitor-core` is not yet in the local Maven repo. Always use `-am` or run `install` first.

## Architecture in One Pass

See `Docs/ARCHITECTURE.md` and `.github/copilot-instructions.md` for full detail. The short version:

**Data flow**: `Journal.log` → `JournalLogMonitor` (polls every 1 s, reads only new bytes via `AppendFileReadStrategy`) → `parsingPool` (single thread) → `LogEventHandler.handleEvent()` → Manager singleton → `notifyListeners()` (EDT) → UI repaint.

**Module rule**: `ed-monitor-core` has zero runtime dependencies. `ed-monitor-swing` depends on core. `ed-monitor-server` depends on core. Swing never imports server; server never imports swing.

**Singleton access**: all manager instances come from `DefaultManagerFactory.getInstance()`. Never `new` a manager.

**Adding a handler**: implement `LogEventHandler`, return exact journal event string from `getEventType()`, register in `HandlerConfiguration.getLogEventHandlers()`. Handlers must not touch Swing directly — call a manager, which calls `notifyListeners()`.

## Non-Obvious Behaviours

### Journal replay on startup
`AppendFileReadStrategy` starts from byte offset 0 when `previousState` is null (first poll). The entire existing journal is replayed every time the app starts. This is intentional — the commodity registry and construction sites are rebuilt from scratch on each launch.

### Commodity registry must be populated before journal events
`MonitorController` calls `initCommodityRegisrtyOverMarketDataIOService()` synchronously in its constructor, loading `Market-default.json` (classpath resource, 371 commodities). This happens before `startAll()` kicks off journal monitoring, so `CommodityRegistry` is ready when the first `ColonisationConstructionDepot` event fires. If a commodity is not in the registry, `findCommodityId()` returns `-1` — the event handler skips that commodity with a WARN log rather than throwing.

### ColonisationConstructionDepot deduplication
`ColonisationConstructionDepot` handler computes a CRC32 over `ResourcesRequired` JSON + `MarketID`. Identical consecutive events are skipped. The fingerprint map is in-memory and resets on app restart, so the first event in a run is always processed.

`updateSite` uses **SET semantics** (`setDeliveredQuantity(providedAmount)`) — `ProvidedAmount` in the event is the cumulative total already delivered. Do not change this to accumulate.

### Server sync and optimistic locking
`ConstructionSiteManager.syncWithServer()` runs every 10 s (initial delay 5 s):
1. Flushes `dirtySites` via PUT first.
2. Pulls server list; replaces local only when `serverVersion > localVersion` **and** the site is not dirty.

`createConstructionSite()` sets `version = 99` so a freshly journal-derived site beats stale server data on first push. Version conflicts during flush are resolved by re-reading the server's current version and retrying.

`addSite()` (called by `DockedEventHandler`) **does not replace an existing tracked site** — it only updates `siteId`. This prevents the MapStruct `@Mapping(target = "version", ignore = true)` from resetting version to null and losing sync state.

### MapStruct mapper caveat
`ConstructionSiteMapper.constructionSiteDtoToEntity()` intentionally ignores the `version` field (JPA `@Version`). The entity's `version` field has no `@Setter`. If you need to transfer version across a DTO boundary in the Swing client, do it before calling the mapper or work around it in the calling code.

### WSL path for Elite Dangerous journal
The game runs on Windows; the journal is at:
```
/mnt/c/Users/<username>/Saved Games/Frontier Developments/Elite Dangerous/
```
`LogMonitorConfig` resolves this at runtime. The Python analysis script in `scripts/analyze_depot_events.py` hardcodes this path and can be run directly for offline journal inspection.
