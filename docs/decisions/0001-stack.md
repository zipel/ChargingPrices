# 1. Stack

- **Status:** accepted
- **Date:** 2026-10-23

## Context

ChargingPrices fetches several operator feeds in DATEX II, a large and deeply nested XML/JSON standard. It maps them to one model, stores them with their locations and serves them to a map in the browser. It should run the same way on macOS and Windows, and a web app comes first, with a phone app later.

## Decision

- **Back end: Java 25 (LTS) and Spring Boot 4, built with Gradle.** Java has strong JSON and XML tooling for a schema like DATEX II. Spring's scheduling, HTTP clients and testing support cover the fetch-map-store work. Spring AI fits the same stack if the assistant in phase 4 happens.
- **Database: PostgreSQL with PostGIS.** "Stations within the map view" and "cheapest near me" are geo queries, and PostGIS does them with an index. Flyway handles schema migrations.
- **Tests: JUnit 5 and Testcontainers.** Mapping code is tested against real feed samples saved as fixtures. Repository tests run against a real PostGIS in a container, not an in-memory fake.
- **Front end: React, TypeScript and Vite, with MapLibre GL JS.** MapLibre is open source and needs no paid map key. It works with free vector tiles (OpenFreeMap or similar, decided during setup). A mobile-first React app can become a PWA and later a phone app.
- **Running it: Docker Compose** for the database and services, the Gradle wrapper and npm scripts for everything else. No Bash-only scripts in the main workflow, so Windows works too.
- **CI: GitHub Actions**, building and testing both parts on every push.

## Alternatives considered

- **Node.js with NestJS for the back end.** It would mean one language end to end, but Java's typed models are a better fit for a schema as big as DATEX II.
- **SQLite or plain PostgreSQL.** Simpler, but geo queries would then live in application code.
- **Leaflet instead of MapLibre.** Simpler, but slower with thousands of markers and no vector tiles.

## Consequences

- Two toolchains (JDK and Node) are needed on every machine.
- PostGIS adds a container to local setup, but Docker Compose handles it.
