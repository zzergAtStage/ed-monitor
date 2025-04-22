# ed-monitor: Advanced Desktop Application for File Monitoring and User Notifications
Overview

ed-monitor is a sophisticated desktop application developed in Java that has evolved
from a simple Swing-based interface to a comprehensive solution incorporating 
file monitoring, user notifications, and persistent market data management. 
This project reflects the progressive enhancement of Java programming skills, 
integrating popular frameworks and adhering to established architectural patterns
to ensure resilience and scalability.

# Features

* File Monitoring: Utilizes Java NIO's WatchService API to efficiently monitor directory and file changes, including creation, modification, and deletion events. This allows for real-time tracking of file system activities.

* User Notifications: Implements a robust notification system that alerts users to specific events or changes within the application. This feature enhances user engagement and responsiveness to critical updates.

* Persistent Market Data Management: Incorporates mechanisms for the persistent storage and retrieval of market data, ensuring data integrity and availability across sessions. This is particularly beneficial for applications requiring reliable data persistence.

* Modular Architecture: Designed with a modular approach, facilitating maintainability and extensibility. This architecture supports the seamless integration of additional features and components as the application evolves.
  ### Screenshoots
![Screenshot 2025-04-22 125423](https://github.com/user-attachments/assets/971c08bf-7c65-48ae-a3ac-df020952c78a)
![Screenshot 2025-04-22 125450](https://github.com/user-attachments/assets/4e57c442-abc5-4422-aba3-d86d0032e5cd)
![Screenshot 2025-04-22 125517](https://github.com/user-attachments/assets/13c86f5b-1b0e-494b-b534-3723fc188151)

# Getting Started

To run ed-monitor on your local machine, follow these steps:

1. Clone the Repository:

```bash
git clone https://github.com/zzergAtStage/ed-monitor.git
```
2. Navigate to the Project Directory:

`cd ed-monitor`

3. Build the Project: Utilize your preferred build tool (e.g., Maven or Gradle) to compile the project. Ensure all dependencies are resolved during this process.

```bash
mvn clean install   
```
4. Run the Application: Execute the compiled JAR file to launch the application.  

```bash
java -jar target/ed-monitor.jar
```

5. To run tests   
```bash
mvn clean test -Ptest  
```

## Prerequisites

* Java Development Kit (JDK): Ensure JDK 17 or higher is installed on your system.

* Build Tool: Maven or Gradle is required for building the project.
```bash
mvn package -Dmaven.test.skip
mvn spring-boot:run
```

# Usage

Upon launching ed-monitor, users can configure monitoring parameters, set up notification preferences, and manage market data through an intuitive graphical user interface. Detailed user guides and documentation are available within the application under the 'Help' section.

## Contributing

Contributions to ed-monitor are welcome. To contribute:

Fork the repository.
Create a new branch for your feature or bug fix.
Commit your changes with clear and concise messages.
Submit a pull request detailing the modifications and their purposes.

## License

This project is licensed under the [MIT License](./license/license.txt). For more information, refer to the LICENSE file in the repository.

### Acknowledgements

Gratitude is extended to the open-source community and the developers of the frameworks and libraries utilized in this project. Their contributions have been instrumental in the development and enhancement of ed-monitor.

### Contact

For inquiries, suggestions, or feedback, please contact the project maintainer at @zzergAtStage

