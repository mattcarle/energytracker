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

- **octopus.properties** - Octopus Energy API base URL and meter config (account number and auth token live in the `OCTOPUS_CREDENTIALS` table instead, entered via the first-run admin setup wizard)
- **application.properties** - settings common to every profile (Hibernate/JPA, H2 driver credentials) plus `spring.profiles.active` (defaults to `dev`)
- **application-dev.properties** / **application-prod.properties** - per-profile settings: H2 database file path, and whether the H2 console/Swagger UI/HTTPS are enabled. `prod` disables the H2 console and Swagger UI, requires HTTPS (placeholder keystore settings - see README.md), and uses a separate database file from `dev`
- **schema.sql** - Database schema initialization

### Authentication

Spring Security with session-cookie auth. On first run (no `ADMIN` user in `USERS`), the app requires a one-off setup: an admin password plus the Octopus account number and API auth token, which also triggers an initial account+usage data load. The admin can then add/remove other users, who are forced to change their password on first login.

### Development URLs

- H2 Console: http://localhost:8080/h2-console
- Swagger UI: http://localhost:8080/swagger-ui.html

## Technology Stack

- Java 21
- Spring Boot 4.1.0
- Spring Data JPA with H2 Database
- SpringDoc OpenAPI (Swagger)
