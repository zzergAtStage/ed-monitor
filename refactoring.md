# Refactoring Chat Export

This document summarizes the discussion and suggestions regarding the refactoring and separation of responsibilities in the project.

---

## Project Separation Strategy


# DONE at this moment. need to be removed.

1. **Modularization:**  
   - Create a multi-module Maven project with a root `pom.xml` and separate modules for:
     - Core business logic and domain (ed-monitor-core)
     - Swing desktop application (ed-monitor-swing)
     - Spring Boot-based server (ed-monitor-server)

2. **Separation of Concerns:**  
   - **Swing Module:**
     - Contains Swing UI components and file processing logic.
     - Handles local events and interacts with the user.
   - **Server Module:**
     - Implements REST controllers and processes core logic.
     - Handles data persistence, calculations, and reactive feedback.
   - **Core Module:**
     - Houses shared data classes and DTOs for strong API contracts.

3. **Communication Between Modules:**  
   - Define interfaces and DTOs in the core module.
   - Swing client sends events and data to the server via REST or messaging.

4. **Scaling Considerations:**  
   - The server module is designed for independent deployment and scaling.
   - The Swing client remains lightweight, delegating heavy lifting to the server.

---

## Suggested Module Configuration for the Swing Part

### Directory Structure
```
ed-monitor/              
│
├── pom.xml              
│
├── ed-monitor-core/     
│   └── pom.xml         
│   └── src/
│       └── main/java/...
│
├── ed-monitor-swing/    
│   └── pom.xml         
│   └── src/
│       └── main/java/   <-- Swing UI classes (e.g., frames, panels)
│       └── main/resources/...
│
└── ed-monitor-server/   
    └── pom.xml         
    └── src/
        └── main/java/   <-- REST controllers, services, repositories
        └── main/resources/...
```

### Root `pom.xml` Example
```xml
<project xmlns="http://maven.apache.org/POM/4.0.0" 
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" 
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 
                             http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>com.zergatstage</groupId>
    <artifactId>ed-monitor</artifactId>
    <version>1.0-SNAPSHOT</version>
    <packaging>pom</packaging>

    <modules>
        <module>ed-monitor-core</module>
        <module>ed-monitor-swing</module>
        <module>ed-monitor-server</module>
    </modules>

    <properties>
        <java.version>21</java.version>
        <spring.boot.version>3.3.4</spring.boot.version>
    </properties>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-dependencies</artifactId>
                <version>${spring.boot.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>
</project>
```

### Swing Module `pom.xml` Example
```xml
<project xmlns="http://maven.apache.org/POM/4.0.0" 
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" 
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 
                             http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.zergatstage</groupId>
        <artifactId>ed-monitor</artifactId>
        <version>1.0-SNAPSHOT</version>
    </parent>
    <artifactId>ed-monitor-swing</artifactId>
    <packaging>jar</packaging>

    <dependencies>
        <!-- The Swing APIs are provided by the JDK -->
        <!-- Optionally, include the core module for shared business logic -->
        <dependency>
            <groupId>com.zergatstage</groupId>
            <artifactId>ed-monitor-core</artifactId>
            <version>1.0-SNAPSHOT</version>
        </dependency>
    </dependencies>
</project>
```

### Version Control: Creating a New Git Branch

Before applying these changes, create a new git branch to safely test the refactoring:
```bash
git checkout -b feature/swing-modularization
```

---

## Next Steps: Refactoring the Existing Code

### Current Issues
- Swing app still depends on Spring app and handles events and some core logic.
- Core module only handles data classes.
- Server module is empty but should handle core logic, data persistence, events processing, and reactive feedbacks.

### Refactoring Suggestions

1. **Decoupling Swing from Spring:**
   - Remove Spring dependencies and event handling from the Swing module.
   - Maintain file processing and UI event triggers in the Swing part.

2. **Migrating Business Logic to the Server Module:**
   - Transfer calculation routines, persistence logic, and event processing from Swing to the server.
   - Implement REST endpoints in the server for accessing these functions.

3. **Defining API Contracts:**
   - Create shared DTOs and interfaces in the core module.
   - Ensure the client sends events/data following these contracts to the server.

4. **Isolating Event Processing:**
   - Move reactive event handling (e.g., Spring-managed listeners) into the server module.
   - Consider using polling or web sockets to notify the Swing client of updates from the server.

5. **Consolidating the Core Module:**
   - Ensure it contains only the shared data models and DTOs.
   - Remove any business logic to keep it a pure model repository.

6. **Iterative Refactoring with Version Control:**
   - Use dedicated feature branches (e.g., _feature/server-refactoring_) to iteratively separate and test changes.
   - Verify client-server communication using integration tests.

---

This document provides a high-level roadmap for refactoring the application toward a clear client/server architecture, ensuring a clean separation of concerns and scalability for future development.