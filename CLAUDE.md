# ChargingPrices: notes for Claude Code

A web app with a map of public EV charging stations in Denmark that shows each operator's current price per kWh. The data comes from the feeds operators publish under EU AFIR Article 20. Read `README.md` and `docs/data-sources.md` at the start of a session.

The operating rules in `AGENT_RULES.md` apply to every session. They're imported here so Claude Code always loads them:

@AGENT_RULES.md

Owner: Dimos Zikos (GitHub `zipel`). Dimos works in VS Code with Claude Code, on a Mac and a Windows laptop. The repo is public and part of Dimos's portfolio.

## Working agreement

- **Only do what Dimos asks.** If a message only sets a direction, ask which task to do before changing files.
- **Dimos must be able to explain every line in an interview.** Prefer plain, readable code over clever code. When you bring in a new library or pattern, explain it in two or three sentences.
- **Record real decisions.** For a choice that's hard to undo (a library, the data model, a hosting option), suggest an ADR in `docs/decisions/`, using `template.md`, and number it in order.
- **Small commits** with Conventional Commit messages (`feat:`, `fix:`, `test:`, `docs:`, `chore:`, `refactor:`). One logical change per commit.
- **Tests come with the feature.** Feed mapping is tested against real feed samples in the back end's test fixtures. Keep fixtures small: cut them down to a few sites.
- **No secrets in git.** API keys and tokens go in `.env`, which is git-ignored. Keep `.env.example` up to date, with placeholder values only.
- **Must work on macOS and Windows.** Use the Gradle wrapper, npm scripts and Docker Compose. No Bash-only scripts in the main workflow. `.gitattributes` handles line endings.
- **Be polite to the data sources.** Send `If-Modified-Since`, cache responses, respect rate limits, and never poll more often than the source updates.
- **Credit the data.** Sources under CC BY 4.0 must be credited in the UI.

## Stack

Java 25, Spring Boot 4.1, Gradle (Kotlin DSL) · in-memory store for phase 1 (PostgreSQL + PostGIS later) · JUnit 5, AssertJ · React 19, TypeScript, Vite, MapLibre GL JS with OpenFreeMap tiles, i18next (English and Danish) · Vitest · Docker · GitHub Actions. The reasons are in `docs/decisions/`.

## Layout

```
backend/              Spring Boot app
  feed/spirii/        fetches Spirii's public AFIR feed per operator (cpoId), with ETags
  feed/datex/         maps DATEX II JSON to the domain model
  feed/               FeedStatus and the feed health check for /actuator/health
  domain/             Site, ChargePoint, Tariff (price at a given local time), SpeedClass, SiteAccess
  store/              SiteStore: the latest snapshot, in memory
  places/             place search through Nominatim (cache, 1 request/second)
  api/                REST controller and the JSON views
web/                  React app
  src/components/     MapView, FilterPanel, StationPanel, PriceChart, PlaceSearch, OperatorComparison
  src/lib/            price colours and formatting, filtering, distances, operator stats
  src/i18n/           en.ts and da.ts with every UI text
docs/                 data sources, decisions (ADRs), the README screenshot
Dockerfile            builds the web app into the Spring Boot jar; one image on port 8080
```

## Commands

| What | Where | Command |
|---|---|---|
| Run the API on :8080 | `backend/` | `./gradlew bootRun` (Windows: `gradlew.bat bootRun`) |
| Back-end tests | `backend/` | `./gradlew test` |
| Run the UI on :5173 | `web/` | `npm install`, then `npm run dev` |
| Front-end tests, lint, build | `web/` | `npm test`, `npm run lint`, `npm run build` |
| Everything in one image on :8080 | repo root | `docker build -t chargingprices .`, then `docker run -p 8080:8080 chargingprices` |

The UI needs the API running. Vite forwards `/api` to `localhost:8080`.

## Domain notes

- **DATEX II v3** is the AFIR format. Static data (sites, stations, charge points, connectors, tariffs) and dynamic data (status, current prices) come from separate endpoints.
- **EVSE ID** (e.g. `DK.SPI.Z507*1`) is the stable key for a charge point across feeds.
- **Price** = the ad hoc price, VAT included, in DKK. See the Javadoc on `Tariff` and `TariffParser` for how the time windows work.
- **Spirii's feed** returns at most 1,000 sites per request, so it's fetched one operator (`cpoId`) at a time. The list is in `application.yml`.
- **Dynamic prices are published about a day ahead.** The price curve stops where the published slots end; it never shows the fallback price as a forecast.
- **UI texts:** every visible string goes in both `web/src/i18n/en.ts` and `da.ts`. The `Translations` type makes the build fail if Danish is missing a key, and `t('...')` keys are checked too.

## Gotchas (learned the hard way)

- **Spring Boot 4 uses Jackson 3.** Packages are `tools.jackson.databind.*`, not `com.fasterxml.jackson.databind.*`, and `JsonNode` uses `isString()` and `stringValue()`.
- **Spring Boot 4 has no `RestClient.Builder` bean** without the `spring-boot-starter-restclient` module. `SpiriiClient` and `NominatimClient` build their own with `RestClient.builder()`.
- **Spring Boot 4 moved the health API** to `org.springframework.boot.health.contributor` (`Health`, `HealthIndicator`, `Status`).
- **Test slices moved too:** `@WebMvcTest` is in `org.springframework.boot.webmvc.test.autoconfigure`, and `MockMvcTester` gives AssertJ-style checks.
- **Spirii always gzips**, even without `Accept-Encoding`. `SpiriiClient` checks the gzip magic bytes.
- **MapLibre 6 needs its worker URL set** (`setWorkerUrl` with a `?url` import in `MapView.tsx`), or the map stays blank in production builds.
- **MapLibre's CSS sets `.maplibregl-map { position: relative }`.** The full-screen rule for the map container must be more specific (`.app > .map`), or the map gets zero height.
