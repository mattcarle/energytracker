# Energy Tracker

A Spring Boot REST API and React UI for tracking electricity/gas consumption via the
[Octopus Energy API](https://developer.octopus.energy/docs/api/), with optional solar
generation, battery state of charge, and house load tracking via a Growatt solar
inverter's official OpenAPI v1.

## Architecture

The application is a standard two-tier web app: a Java/Spring Boot backend serving a JSON
REST API, and a separate React single-page app for the UI. In production the frontend is
built to static files and served by the backend; in development the two run as separate
processes with the frontend proxying API calls to the backend.

```
Octopus Energy API  <-->  OctopusService  -->  H2 database
                                                     |
Growatt OpenAPI      <-->  GrowattService  -->  H2 database
                                                     |
                                              Controllers (/api/*)
                                                     |
                                              React SPA (frontend/)
```

### Backend (`src/main/java/com/carle7/energytracker`)

- **`controller/`** — REST endpoints under `/api/*` (agreements, meters, usage, standing
  charges, tariffs, users, auth, data load/integrity, solar/battery/load, Growatt settings).
- **`service/OctopusService.java`** — integrates with the Octopus Energy API (via
  `RestTemplate` with Basic Auth), parses the responses, and persists them.
- **`service/GrowattService.java`** / **`GrowattApiService.java`** — the same pattern for the
  Growatt OpenAPI v1: resolves the account's plant/device, persists daily solar generation
  (kWh) to `SOLAR_GENERATION`, and proxies live intraday PV power, battery state of charge, and
  house load consumption straight through (not persisted — see
  [Growatt integration](#growatt-integration-optional) below).
- **`repository/`** — Spring Data JPA repositories.
- **`model/`** — JPA entities: `Agreement`, `Meter`, `MeterPoint`, `Usage`, `StandingCharge`,
  `UnitRate`, `User`, `OctopusCredentials`, `GrowattCredentials`, `SolarGeneration`, etc.
- **`security/`** — Spring Security session-cookie authentication.
- **`config/`**, **`dto/`** — application configuration and data-transfer objects.

Data flow: the external Octopus Energy API is polled by `OctopusService`, which stores
agreements, meters, usage readings, standing charges and unit rates in an H2 database.
`UsageController` and friends expose that data (by half-hour, day, week, month and year) to
the frontend over REST. `GrowattService` does the equivalent for solar generation, and
`SolarController` exposes it (plus the live battery/load figures) alongside the usage data.

**Authentication**: on first run, with no `ADMIN` user present, the app requires a one-off
setup — an admin password plus the Octopus account number and API auth token (this also
triggers an initial data load), followed by an optional second step to enter a Growatt API
token (skippable, and configurable later — see below). The Octopus/Growatt credentials are
stored in the `OCTOPUS_CREDENTIALS`/`GROWATT_CREDENTIALS` tables, not in a properties file.
The admin can then add/remove other users, who must change their password on first login.

**Configuration**:
- `octopus.properties` — Octopus Energy API base URL and meter config.
- `growatt.properties` — Growatt OpenAPI v1 base URL.
- `application.properties` — settings common to every environment (JPA/Hibernate, H2 driver
  credentials). Which profile is active — `dev` or `prod` — is also set here, via
  `spring.profiles.active` (defaults to `dev`; override for a real deployment).
- `application-dev.properties` / `application-prod.properties` — everything that differs
  between environments: the H2 database file, and whether the H2 console/Swagger UI/HTTPS are
  on. See [Running in production](#running-in-production) below for what `prod` changes.
- `schema.sql` — database schema initialization.

### Growatt integration (optional)

If you don't have a Growatt inverter, skip the second setup step (or never fill it in) and the
app works exactly as it did with Octopus alone — every Growatt-dependent piece of UI (the
Solar/Battery/Load checkboxes on the Usage pages, the "Manage Growatt Data" admin page) simply
doesn't appear until a token is configured.

With a token configured, the app tracks three things from the inverter, all sourced from
Growatt's `device/mix/mix_data` telemetry:
- **Solar generation** — daily totals (kWh) are persisted to `SOLAR_GENERATION` and shown
  alongside usage on the Week/Month/Year pages; the Day page instead overlays the live intraday
  power curve (kW), since Growatt only exposes that at 5-minute resolution, not as a
  backfillable daily history.
- **Battery state of charge** (%) and **house load consumption** (kW) — Day page only, both
  live intraday curves proxied straight from Growatt rather than persisted, for the same reason
  as the solar power curve above.

Each of Solar/Battery/Load has its own independent checkbox on the Usage pages. The Growatt
account's plant and device are resolved automatically from the token — no plant ID or device
serial number needs to be entered manually. The token itself can be set (or changed) any time
from **Admin → Manage Growatt Data**, which also has a "Load Solar Data Now" button for a
manual refresh. Both Octopus and Growatt otherwise refresh automatically once a day, at 02:00
Europe/London (`app.startup-usage-load.enabled` / `app.startup-solar-load.enabled` control
whether they also do an initial load on application startup — on by default in `prod`, off in
`dev`).

### Frontend (`frontend/`)

A React 19 + TypeScript SPA built with Vite.

- **`src/pages/`** — screens: login/setup/change-password, manage users, manage Octopus data,
  manage Growatt data, and usage views by half-hour/day/week/month/year (each overlaying
  Solar/Battery/Load when Growatt is configured — see
  [Growatt integration](#growatt-integration-optional) above).
- **`src/components/`** — shared UI (charts, modals).
- **`src/api/`** — typed HTTP client for the backend `/api/*` endpoints.

The default landing page is Usage by Day, on the most recent day with a complete set of
half-hourly data — it steps back a day at a time past any not-yet-fully-synced day(s) rather
than showing a partial one.

In dev mode, Vite proxies requests to `/energytracker/api` through to `http://localhost:8080`
(see `frontend/vite.config.ts`), so the backend must be running for the UI to have data.

## Running the app

### Prerequisites

- Java 21
- Node.js (for the frontend)

The backend picks a profile via `spring.profiles.active` (see `application.properties`),
which controls the H2 database file location and whether the H2 console/Swagger UI/HTTPS are
on. It defaults to `dev` — nothing extra is needed to run it locally. See
[Running in production](#running-in-production) for deploying with the `prod` profile instead.

## Running in development

### 1. Start the backend

From the repository root, using the Maven Wrapper:

```bash
mvnw.cmd spring-boot:run          # Windows
./mvnw spring-boot:run            # Linux/Mac
```

This starts the API on **http://localhost:8080** using the `dev` profile (H2 database file at
`~/h2db/energytracker`). On first run, no admin user exists yet — open the app and follow the
setup wizard to create an admin password and enter your Octopus Energy account number and API
auth token (and, optionally, a Growatt API token — skippable, see
[Growatt integration](#growatt-integration-optional) above).

Useful backend URLs (dev only — both are disabled under the `prod` profile):
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
to the backend on port 8080. The app is served under `/energytracker` (matching the production
Caddy routing - see [Deploying with Docker](#deploying-with-docker) below), so open
**http://localhost:5173/energytracker/** — visiting the bare root redirects there automatically.

## Running in production

The `prod` profile (`application-prod.properties`) differs from `dev` in three ways:

- **H2 console and Swagger UI are disabled** — neither should be reachable outside a
  developer's own machine.
- **HTTPS is required** (`server.ssl.enabled=true`, on port `8443`) rather than plain HTTP.
- **A separate database file** (`~/h2db/energytracker-prod`) — so a prod run never reads or
  writes dev's data, even on the same machine.

### 1. Provide a TLS certificate

`application-prod.properties` ships with placeholder keystore settings pointing at
`/etc/energytracker/keystore.p12`, which won't exist until you put a real certificate there.
For a real deployment, use a certificate from your CA/reverse proxy setup; for local testing
of the `prod` profile, generate a self-signed one:

```bash
keytool -genkeypair -alias energytracker -keyalg RSA -keysize 2048 -validity 365 \
  -storetype PKCS12 -keystore keystore.p12 -storepass changeit \
  -dname "CN=localhost, OU=Dev, O=EnergyTracker, C=GB"
```

Place the resulting file at the configured `server.ssl.key-store` path (or point that property
elsewhere), and set a real `server.ssl.key-store-password`. Every `server.ssl.*` property can
also be supplied as an environment variable instead of editing the properties file directly
(e.g. `SERVER_SSL_KEY_STORE_PASSWORD`), which is the safer option for a real deployment.

### 2. Build and run

```bash
mvnw.cmd clean package                                  # Windows
./mvnw clean package                                     # Linux/Mac

java -jar target/energytracker-0.0.1-SNAPSHOT.jar --spring.profiles.active=prod
```

`SPRING_PROFILES_ACTIVE=prod` works the same way as an environment variable, if you'd rather
not pass it as a command-line argument. Once running, the API is served over
**https://localhost:8443** (or whatever `server.port` you've configured).

On first run, exactly as in dev, no admin user exists yet — open the app and follow the setup
wizard to create an admin password and enter your Octopus Energy account number and API auth
token.

The frontend still needs building and serving separately (`npm run build` in `frontend/`,
per the SPA architecture described above) — this repo doesn't yet wire that build into the
backend's own jar/static resources.

### 3. Point the UI at the prod backend

`npm run preview` (which serves the `npm run build` output, default **http://localhost:4173**)
reuses the same `/api` proxy as `npm run dev`, which defaults to `http://localhost:8080` — the
`dev` backend. To test the UI against a locally running `prod`-profile backend instead, point
the proxy at it with `VITE_API_PROXY_TARGET`:

```bash
cd frontend
VITE_API_PROXY_TARGET=https://localhost:8443 npm run preview      # Windows/Linux/Mac (bash)
```

```powershell
$env:VITE_API_PROXY_TARGET = 'https://localhost:8443'; npm run preview   # PowerShell
```

Without this, `/api/*` requests from the UI fail with a 502 (Vite's proxy tries the default
dev target on port 8080, where nothing is listening).

## Deploying with Docker

This app shares its host and domain (`carle7.com`) with other apps behind a single reverse proxy
— see the separate [`carle7-edge`](https://github.com/mattcarle/carle7-edge) repo, which is the
only thing on the host that binds ports 80/443 or terminates TLS. The included
`docker-compose.yml` here runs just this app's own two containers, on a private network plus the
`carle7-edge` network that proxy reaches them on:

- **`app`** — the Spring Boot backend, built by the root `Dockerfile`, running the `prod`
  profile on plain HTTP internally (port 8080, not published to the host). Its H2 database
  file lives in the `h2-data` named volume, so it survives container rebuilds/restarts.
- **`caddy`** — built by `frontend/Dockerfile`, serves the built React static files and
  reverse-proxies `/energytracker/api/*` to `app`. It speaks plain HTTP on the Docker network
  only (no TLS, no published ports) — `carle7-edge` forwards it everything under
  `/energytracker/*` unmodified, still carrying that prefix.

### First-time deployment

1. Bring up [`carle7-edge`](https://github.com/mattcarle/carle7-edge) first if it isn't already
   running — it creates the `carle7-edge` Docker network this app's `caddy` service attaches to.
   `docker compose up` here fails until that network exists.

2. Build and start both containers:

   ```bash
   docker compose up -d --build
   ```

3. Open `https://<SITE_ADDRESS>/energytracker/` (`SITE_ADDRESS` is configured in `carle7-edge`,
   not here). On first run, no admin user exists yet — the setup wizard walks you through
   creating an admin password and entering your Octopus Energy account number and API auth
   token.

Check container status/logs with:

```bash
docker compose ps
docker compose logs -f app       # backend logs
docker compose logs -f caddy     # reverse proxy logs
```

### Updating after code or config changes

`docker compose up -d --build` rebuilds every service's image and recreates only the
containers whose image or config actually changed, so it's safe to run after any change — but
rebuilding both images on every change is slower than it needs to be. To target just the
service you touched:

```bash
# Backend code (src/), pom.xml, or Dockerfile changes
docker compose build app
docker compose up -d app

# Frontend code (frontend/src/), frontend/Dockerfile, or frontend/Caddyfile changes
docker compose build caddy
docker compose up -d caddy
```

If you only changed `docker-compose.yml` itself (e.g. an environment variable) with no
Dockerfile/source changes, skip the `build` step — `docker compose up -d` alone detects the
config change and recreates the affected container(s).

The `h2-data` volume is untouched by rebuilds or `docker compose down`, so the database
persists across updates. Only `docker compose down -v` (or manually removing the volume)
deletes it — avoid that unless you actually intend to wipe all data.

### Running a second, independent instance

`docker-compose.yml` also defines `app2`/`caddy2` — a second instance of this same app (own
database in the `h2-data-2` volume, own Octopus and Growatt account/credentials entered via its
own setup wizard) sharing the domain at `/energytracker2`, alongside the primary instance at
`/energytracker`. What differs from `app`/`caddy`:

- `app2`'s `SPRING_DATASOURCE_URL` points at a separate database file, and its `APP_BASE_PATH`
  env var (`/energytracker2`) scopes its session/CSRF cookies to that path so they don't collide
  with the primary instance's cookies on the same domain (see `SecurityConfig`).
- `caddy2` is built with `APP_BASE_PATH=/energytracker2/` (a build arg — bakes that prefix into
  the frontend's asset URLs) and run with `APP_PATH`/`APP_UPSTREAM` env vars (runtime - tells
  `frontend/Caddyfile` which path prefix and which backend container name to use), and joins
  `carle7-edge` under the `energytracker2` alias instead of `energytracker`.

Add `app3`/`caddy3` the same way for a third instance. Each instance also needs a matching
`handle /energytracker2` + `handle /energytracker2/*` pair in `carle7-edge`'s own `Caddyfile` -
see that repo's README.

## Other useful commands

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
