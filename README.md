# Energy Tracker

A Spring Boot REST API and React UI for tracking electricity/gas consumption via the
[Octopus Energy API](https://developer.octopus.energy/docs/api/).

## Architecture

The application is a standard two-tier web app: a Java/Spring Boot backend serving a JSON
REST API, and a separate React single-page app for the UI. In production the frontend is
built to static files and served by the backend; in development the two run as separate
processes with the frontend proxying API calls to the backend.

```
Octopus Energy API  <-->  OctopusService  -->  H2 database
                               |
                          Controllers (/api/*)
                               |
                          React SPA (frontend/)
```

### Backend (`src/main/java/com/carle7/energytracker`)

- **`controller/`** — REST endpoints under `/api/*` (agreements, meters, usage, standing
  charges, tariffs, users, auth, data load/integrity).
- **`service/OctopusService.java`** — integrates with the Octopus Energy API (via
  `RestTemplate` with Basic Auth), parses the responses, and persists them.
- **`repository/`** — Spring Data JPA repositories.
- **`model/`** — JPA entities: `Agreement`, `Meter`, `MeterPoint`, `Usage`, `StandingCharge`,
  `UnitRate`, `User`, `OctopusCredentials`, etc.
- **`security/`** — Spring Security session-cookie authentication.
- **`config/`**, **`dto/`** — application configuration and data-transfer objects.

Data flow: the external Octopus Energy API is polled by `OctopusService`, which stores
agreements, meters, usage readings, standing charges and unit rates in an H2 database.
`UsageController` and friends expose that data (by half-hour, day, week, month and year) to
the frontend over REST.

**Authentication**: on first run, with no `ADMIN` user present, the app requires a one-off
setup — an admin password plus the Octopus account number and API auth token (this also
triggers an initial data load). The Octopus account number/token are stored in the
`OCTOPUS_CREDENTIALS` table, not in a properties file. The admin can then add/remove other
users, who must change their password on first login.

**Configuration**:
- `octopus.properties` — Octopus Energy API base URL and meter config.
- `application.properties` — H2 database (file-based at `~/h2db/energytracker`), Hibernate,
  and Swagger settings.
- `schema.sql` — database schema initialization.

### Frontend (`frontend/`)

A React 19 + TypeScript SPA built with Vite.

- **`src/pages/`** — screens: login/setup/change-password, manage users, manage data, and
  usage views by half-hour/day/week/month/year.
- **`src/components/`** — shared UI (charts, modals).
- **`src/api/`** — typed HTTP client for the backend `/api/*` endpoints.

In dev mode, Vite proxies requests to `/api` through to `http://localhost:8080` (see
`frontend/vite.config.ts`), so the backend must be running for the UI to have data.

## Running the app

### Prerequisites

- Java 21
- Node.js (for the frontend)

### 1. Start the backend

From the repository root, using the Maven Wrapper:

```bash
mvnw.cmd spring-boot:run          # Windows
./mvnw spring-boot:run            # Linux/Mac
```

This starts the API on **http://localhost:8080**. On first run, no admin user exists yet —
open the app and follow the setup wizard to create an admin password and enter your Octopus
Energy account number and API auth token.

Useful backend URLs:
- API root: http://localhost:8080/api
- Swagger UI: http://localhost:8080/swagger-ui.html
- H2 Console: http://localhost:8080/h2-console

### 2. Start the frontend

In a separate terminal:

```bash
cd frontend
npm install     # first time only
npm run dev
```

This starts the Vite dev server (default **http://localhost:5173**) with API calls proxied
to the backend on port 8080. Open that URL in your browser to use the UI.

### Other useful commands

```bash
# Backend
mvnw.cmd clean compile      # Build and compile
mvnw.cmd test                # Run tests
mvnw.cmd package             # Package as JAR

# Frontend
npm run build                # Type-check and build for production
npm run lint                  # Lint with oxlint
npm run preview               # Preview the production build
```

## Technology stack

- **Backend**: Java 21, Spring Boot 4.1.0, Spring Data JPA, H2 database, Spring Security,
  SpringDoc OpenAPI (Swagger)
- **Frontend**: React 19, TypeScript, Vite, Recharts
