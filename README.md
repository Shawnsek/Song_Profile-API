# Song_Profile-API

This project is a microservices-based application built with Spring Boot, Maven, MongoDB, and Neo4j. Please note that this application is configured to work only on localhost.

## Prerequisites

To run this project locally, ensure you have the following installed and set up in your development environment:

### 1. Java Development Kit (JDK)
- Version: 11 or higher
- Ensure JAVA_HOME is set up correctly.

### 2. Maven
- Version: 3.6 or higher
- Used for building and managing dependencies.

### 3. Spring Boot
- Framework used for building the microservices.
- No manual installation is needed as Maven manages dependencies.

### 4. MongoDB
- Version: 5.0 or higher
- Ensure MongoDB is running on localhost with the default port (27017).
- Create and configure a database as required by the application.

### 5. Neo4j
- Version: 4.0 or higher
- Ensure Neo4j is running locally, and you have the required credentials configured in the application properties.
        `fullName`,  `password`. `plName`, `songId` `userName` 

### 6. Postman or a Browser (Optional)
- For testing the REST API endpoints.

## How to Run

### 1. Clone this repository:

`git clone https://github.com/Shawnsek/Song_Profile-API.git`
`cd Song_Profile-API`
### 2. Build the project using Maven:

`mvn clean install`
### 3. Start the application:

`mvn spring-boot:run`
### 4. Access the application: - The application runs on http://localhost:8080 by default.
- Use a tool like Postman to test the API endpoints.
