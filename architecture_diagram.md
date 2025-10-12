```mermaid
graph TD
    A[Frontend UI \(Swing\)] -->|User Interactions| B[Core Module]
    B -->|File Monitoring| C[File System]
    B -->|User Notifications| D[Notification System]
    B -->|Market Data Management| E[Database]
    E -->|Persistent Storage| F[Market Data Files]
    B -->|API Requests| G[Server Module]
    G -->|Configuration| H[Application YAML]
    G -->|Market Data| F
    G -->|Compiled Outputs| I[Target JARs]
    subgraph Core Module
        B[Core Logic]
    end
    subgraph Server Module
        G[Server Logic]
    end
    subgraph Data and Persistence
        E[Database]
        F[Market Data Files]
    end
    subgraph UI Components
        A[Frontend UI \(Swing\)]
    end
```
