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

Java 25, Spring Boot 4, Gradle · PostgreSQL + PostGIS, Flyway · JUnit 5, Testcontainers · React, TypeScript, Vite, MapLibre GL JS · Docker Compose · GitHub Actions. The reasons are in `docs/decisions/0001-stack.md`.

## Planned layout

```
backend/              Spring Boot app: feed adapters, common model, REST API
web/                  React app: map and station panel
docs/                 data sources, decisions (ADRs)
docker-compose.yml    PostGIS plus both apps
```

## Domain notes

- **DATEX II v3** is the AFIR format. Static data (sites, stations, charge points, connectors, tariffs) and dynamic data (status, current prices) come from separate endpoints.
- **EVSE ID** (e.g. `DK.SPI.Z507*1`) is the stable key for a charge point across feeds.
- **Price** = the ad hoc price, VAT included, in DKK. Spirii publishes it in 15-minute slots. A rate can also have per-minute or flat (start fee) parts.
- The first source is Spirii's public feed: `https://api.spirii.com/v2/afir/energy-infrastructure-tables` and `.../energy-infrastructure-statuses`, with `countryIsos=DK`. No key needed.

## Commands

Fill this in once the projects exist.
