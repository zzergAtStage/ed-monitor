# EVENT_TYPES.md — Journal Event Catalog

Comprehensive reference of Elite Dangerous journal events handled by ed-monitor.

---

## Event Structure

Each journal log line is a JSON object with common fields:

```json
{
  "timestamp": "2025-11-13T12:34:56Z",
  "event": "EventType",
  ... (event-specific fields)
}
```

**Rule**: Always use defensive parsing with `event.optString("field", default)` to handle missing fields.

---

## Handled Events

### 1. ProspectedAsteroid

**Handler**: `AsteroidProspectEventHandler`  
**Manager**: `AsteroidManager`  
**UI Component**: `DroneProvisionerPanel`

**JSON Structure**:
```json
{
  "timestamp": "2025-11-13T12:34:56Z",
  "event": "ProspectedAsteroid",
  "Materials": [
    {
      "Name": "Tritium",
      "Proportion": 0.35
    },
    {
      "Name": "Platinum",
      "Proportion": 0.12
    },
    {
      "Name": "Painite",
      "Proportion": 0.08
    }
  ]
}
```

**Key Fields**:
| Field | Type | Optional | Notes |
|-------|------|----------|-------|
| `timestamp` | string | No | ISO-8601 timestamp |
| `event` | string | No | Always "ProspectedAsteroid" |
| `Materials` | array | No | List of { Name, Proportion } objects |
| `Materials[].Name` | string | No | Material type (Tritium, Platinum, Painite, etc.) |
| `Materials[].Proportion` | number | No | Proportion as decimal 0.0–1.0 (may be 0–100%) |

**Parsing Example**:
```java
if (event.has("Materials")) {
    JSONArray materials = event.getJSONArray("Materials");
    for (int i = 0; i < materials.length(); i++) {
        JSONObject mat = materials.getJSONObject(i);
        String name = mat.optString("Name", "");
        double proportion = mat.optDouble("Proportion", 0.0);
        // proportion may be 0..1 or 0..100; normalize in handler
        if (proportion > 1.0) proportion /= 100.0;
    }
}
```

**Color Coding (DroneProvisionerPanel)**:
- Proportion < 0.30 (30%) → Gray (#B0B0B0)
- Proportion 0.30–0.43 → Orange (#FFA500)
- Proportion > 0.43 (>43%) → Violet (#8A2BE2)

---

### 2. LaunchDrone

**Handler**: `DroneLaunchEventHandler`  
**Manager**: `DroneManager`  
**UI Component**: `DroneProvisionerPanel` (label update)

**JSON Structure**:
```json
{
  "timestamp": "2025-11-13T12:35:00Z",
  "event": "LaunchDrone",
  "SID": 123456,
  "Type": "Collection"
}
```

**Key Fields**:
| Field | Type | Optional | Notes |
|-------|------|----------|-------|
| `timestamp` | string | No | ISO-8601 timestamp |
| `event` | string | No | Always "LaunchDrone" |
| `SID` | number | Yes | Drone serial ID |
| `Type` | string | Yes | "Collection", "Surveillance", etc. |

**Behavior**:
- Sets `DroneManager.droneLaunched = true`
- UI displays "Drone Launched: Yes" for 3 seconds, then resets
- Notifies listeners

---

### 3. Cargo

**Handler**: `CargoInventoryEventHandler`  
**Manager**: `CargoInventoryManager`

**JSON Structure**:
```json
{
  "timestamp": "2025-11-13T12:36:00Z",
  "event": "Cargo",
  "Inventory": [
    {
      "Name": "Tritium",
      "Name_Localised": "Tritium",
      "Count": 5,
      "Stolen": 0
    }
  ],
  "Vessel": "Ship",
  "Capacity": 256
}
```

**Key Fields**:
| Field | Type | Optional | Notes |
|-------|------|----------|-------|
| `Inventory` | array | No | List of { Name, Count, Stolen } |
| `Inventory[].Name` | string | No | Commodity/material name |
| `Inventory[].Count` | number | No | Quantity |
| `Vessel` | string | No | "Ship" or "Carrier" |
| `Capacity` | number | No | Max cargo capacity |

---

### 4. CargoTransfer (partial handling via CargoUpdateEventHandler)

**Handler**: `CargoUpdateEventHandler`  
**Manager**: `ConstructionSiteManager`

**JSON Structure**:
```json
{
  "timestamp": "2025-11-13T12:37:00Z",
  "event": "CargoTransfer",
  "Transfers": [
    {
      "Type": "Tritium",
      "Count": 5,
      "Direction": "toShip"
    }
  ]
}
```

**Status**: **WIP** — handler has TODO for manual merge logic when cargo spans multiple depots

---

### 5. Loadout

**Handler**: `LoadoutEventHandler`  
**Manager**: (None; informational only)

**JSON Structure**:
```json
{
  "timestamp": "2025-11-13T12:38:00Z",
  "event": "Loadout",
  "Ship": "CobraMkIII",
  "ShipID": 1,
  "ShipName": "My Ship",
  "Modules": [
    {
      "Slot": "Armour",
      "Item": "Mk2 Armour",
      "Class": 1
    }
  ]
}
```

**Status**: Currently just logged; no manager update

---

### 6. Location

**Handler**: `LocationEventHandler`  
**Manager**: `StatusMonitor` (game status tracking)

**JSON Structure**:
```json
{
  "timestamp": "2025-11-13T12:39:00Z",
  "event": "Location",
  "Docked": false,
  "StarSystem": "Omega Sector",
  "SystemAddress": 9999999999999999,
  "Body": "Station Name"
}
```

**Key Fields**:
| Field | Type | Optional | Notes |
|-------|------|----------|-------|
| `Docked` | boolean | No | true if at station/fleet carrier |
| `StarSystem` | string | No | Current system name |
| `SystemAddress` | number | Yes | Unique system ID |
| `Body` | string | Yes | Current planet/station |

---

### 7. Docked

**Handler**: `DockedEventHandler`  
**Manager**: (None; informational)

**JSON Structure**:
```json
{
  "timestamp": "2025-11-13T12:40:00Z",
  "event": "Docked",
  "Docked": true,
  "StationType": "Coriolis",
  "Station": "Tyr Station"
}
```

**Status**: Currently just logged

---

### 8. MarketBuy

**Handler**: `MarketBuyEventHandler`  
**Manager**: `CargoInventoryManager`

**JSON Structure**:
```json
{
  "timestamp": "2025-11-13T12:41:00Z",
  "event": "MarketBuy",
  "Type": "Tritium",
  "Count": 10,
  "BuyPrice": 50000,
  "TotalCost": 500000
}
```

**Key Fields**:
| Field | Type | Optional | Notes |
|-------|------|----------|-------|
| `Type` | string | No | Commodity name |
| `Count` | number | No | Units purchased |
| `BuyPrice` | number | No | Price per unit |
| `TotalCost` | number | No | Total credits spent |

---

### 9. MarketSell

**Handler**: `MarketSellEventHandler`  
**Manager**: `CargoInventoryManager`

**JSON Structure**:
```json
{
  "timestamp": "2025-11-13T12:42:00Z",
  "event": "MarketSell",
  "Type": "Tritium",
  "Count": 5,
  "SellPrice": 48000,
  "TotalSale": 240000
}
```

**Key Fields**:
| Field | Type | Optional | Notes |
|-------|------|----------|-------|
| `Type` | string | No | Commodity name |
| `Count` | number | No | Units sold |
| `SellPrice` | number | No | Price per unit |
| `TotalSale` | number | No | Total credits received |

---

### 10. ColonisationContribution

**Handler**: `ColonisationContributionEventHandler`  
**Manager**: `ConstructionSiteManager`

**JSON Structure**:
```json
{
  "timestamp": "2025-11-13T12:43:00Z",
  "event": "ColonisationContribution",
  "Location": "Omega Sector 5",
  "Delivered": 10,
  "Type": "Tritium",
  "TotalRequired": 100
}
```

**Key Fields**:
| Field | Type | Optional | Notes |
|-------|------|----------|-------|
| `Location` | string | No | Construction site name |
| `Delivered` | number | No | Units delivered this action |
| `Type` | string | No | Material type |
| `TotalRequired` | number | No | Total units needed for site |

**Behavior**:
- Updates construction site progress
- Notifies listeners for UI refresh

---

### 11. ColonisationConstructionDepot

**Handler**: `ColonisationConstructionDepotEventHandler`  
**Manager**: `ConstructionSiteManager`

**JSON Structure**:
```json
{
  "timestamp": "2025-11-13T12:44:00Z",
  "event": "ColonisationConstructionDepot",
  "Location": "Omega Sector 5",
  "Materials": [
    {
      "Type": "Tritium",
      "Delivered": 50,
      "Total": 100,
      "Percentage": 50.0
    }
  ],
  "DistributionCenter": false
}
```

**Key Fields**:
| Field | Type | Optional | Notes |
|-------|------|----------|-------|
| `Location` | string | No | Construction site name |
| `Materials` | array | No | { Type, Delivered, Total, Percentage } |
| `Materials[].Type` | string | No | Material type |
| `Materials[].Delivered` | number | No | Units delivered so far |
| `Materials[].Total` | number | No | Total units needed |
| `Materials[].Percentage` | number | No | Completion percentage (0–100) |

**Behavior**:
- Provides full depot status snapshot
- Updates construction site tracking
- Notifies listeners (ConstructionSitePanel refreshes)

**Status**: **WIP** — Some UI filtering TODOs (hide/show completed depots)

---

## Unhandled Events (Reference)

These events are logged but have no dedicated handler:

| Event Type | Typical Fields | Note |
|------------|----------------|------|
| `Music` | `MusicTrack` | BGM changes |
| `NavRoute` | `Route` | Nav computer updates |
| `ShipTargeted` | `Ship`, `Ship_Localised` | Target lock |
| `FSD` / `FSDJump` | `StarSystem`, `Jump distance` | Hyperspace jump events |
| `Scan` | `BodyName`, `DistanceFromArrivalLS` | Celestial body scan |

**These can be added** if needed by creating new handlers and registering in `HandlerConfiguration`.

---

## Event Processing Rules

### For Handler Developers

1. **Event Type Matching**: Return exact string from `getEventType()`
   ```java
   @Override
   public String getEventType() { return "ProspectedAsteroid"; }  // Case-sensitive!
   ```

2. **Defensive JSON Parsing**: Always use `opt*()` methods
   ```java
   String name = event.optString("Name", "Unknown");
   double value = event.optDouble("Proportion", 0.0);
   JSONArray arr = event.optJSONArray("Materials");  // Returns null if missing
   ```

3. **Error Handling**: Wrap in try-catch, log errors
   ```java
   try {
       // handle event
   } catch (JSONException e) {
       log.error("Failed to parse event: {}", e.getMessage());
   }
   ```

4. **Manager Calls**: Call manager method, never update UI directly
   ```java
   asteroidManager.updateProspectingLabel(event);
   // Manager will notify listeners
   ```

5. **No Blocking**: Event handlers should complete in < 100ms
   - Offload heavy computation to background thread if needed

---

## Known Issues & Quirks

| Issue | Event(s) Affected | Workaround |
|-------|-------------------|-----------|
| Proportion in % vs. decimal | ProspectedAsteroid | Handler normalizes to 0..1 range |
| Duplicate Material entries | ProspectedAsteroid | Filter by Name, take first match |
| Missing Materials array | ProspectedAsteroid | Check `event.has("Materials")` before parsing |
| Docked before Location | Docked, Location | Handlers may receive events out of order |
| Market price fluctuation | MarketBuy, MarketSell | Prices in event are actual price paid/received |

---

## Testing: Sample JSON Events

### ProspectedAsteroid (Low Tritium)
```json
{
  "timestamp": "2025-11-13T12:34:56Z",
  "event": "ProspectedAsteroid",
  "Materials": [
    {"Name": "Tritium", "Proportion": 0.15},
    {"Name": "Platinum", "Proportion": 0.22},
    {"Name": "Painite", "Proportion": 0.08}
  ]
}
```
→ Expected: Gray rectangle (15% < 30%)

### ProspectedAsteroid (Medium Tritium)
```json
{
  "timestamp": "2025-11-13T12:34:56Z",
  "event": "ProspectedAsteroid",
  "Materials": [
    {"Name": "Tritium", "Proportion": 0.37},
    {"Name": "Platinum", "Proportion": 0.12},
    {"Name": "Painite", "Proportion": 0.05}
  ]
}
```
→ Expected: Orange rectangle (30% ≤ 37% ≤ 43%)

### ProspectedAsteroid (High Tritium)
```json
{
  "timestamp": "2025-11-13T12:34:56Z",
  "event": "ProspectedAsteroid",
  "Materials": [
    {"Name": "Tritium", "Proportion": 0.48},
    {"Name": "Platinum", "Proportion": 0.08},
    {"Name": "Painite", "Proportion": 0.03}
  ]
}
```
→ Expected: Violet rectangle (48% > 43%)

---

## Future Event Types

When implementing new features, reference this template:

```markdown
### N. [EventName]

**Handler**: `[EventName]EventHandler`  
**Manager**: `[Manager]`  
**UI Component**: `[Panel]` (optional)

**JSON Structure**:
\`\`\`json
{ ... }
\`\`\`

**Key Fields**:
| Field | Type | Optional | Notes |
|-------|------|----------|-------|

**Behavior**:
- ...

**Status**: Active | WIP | Not yet implemented
```
