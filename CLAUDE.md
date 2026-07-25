# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

This project uses Maven with the Maven Wrapper. Use `mvnw.cmd` on Windows or `./mvnw` on Linux/Mac.

```bash
# Build and compile
mvnw.cmd clean compile

# Run tests
mvnw.cmd test

# Run a single test class
mvnw.cmd test -Dtest=EnergyTrackerApplicationTests

# Package as JAR
mvnw.cmd package

# Run the application
mvnw.cmd spring-boot:run
```

## Architecture

Spring Boot REST API that integrates with the Octopus Energy API to track electricity/gas consumption.

### Layers

- **Controller** (`controller/`) - REST endpoints at `/api/*`
- **Service** (`service/OctopusService.java`) - Octopus Energy API integration and business logic
- **Repository** (`repository/`) - Spring Data JPA repositories
- **Model** (`model/`) - JPA entities (Agreement, Meter, Usage, StandingCharge, UnitRate)

### Data Flow

1. External Octopus Energy API → OctopusService (fetches via RestTemplate with Basic Auth)
2. OctopusService parses JSON responses and persists to H2 database
3. UsageController exposes data via REST endpoints

### Key Configuration

- **octopus.properties** - Octopus Energy API credentials and configuration
- **application.properties** - H2 database (file-based at `~/h2db/energytracker`), Hibernate settings
- **schema.sql** - Database schema initialization

### Development URLs

- H2 Console: http://localhost:8080/h2-console
- Swagger UI: http://localhost:8080/swagger-ui.html

## Technology Stack

- Java 21
- Spring Boot 4.1.0
- Spring Data JPA with H2 Database
- SpringDoc OpenAPI (Swagger)
