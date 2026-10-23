# 3. Keep the data in memory for phase 1

- **Status:** accepted
- **Date:** 2026-10-23
- **Changes:** the database part of [0001](0001-stack.md), for now

## Context

Decision 0001 picked PostgreSQL with PostGIS. When phase 1 started, the development Mac had no Docker, and phase 1 only shows the *current* state of the feeds:

- About 2,200 sites and 10,000 charge points, a few MB in memory.
- The data is a copy of the feeds. After a restart it's back within a minute.
- The map loads all sites at once and filters them in the browser, so there are no geo queries on the server yet.

## Decision

Keep the latest snapshot in memory (`SiteStore`) and work out prices at request time. Updates replace whole immutable lists, so readers never need locks.

## Alternatives considered

- **PostGIS now, as planned.** It would need Docker on every machine before anything runs, for data that is thrown away on every refresh anyway.
- **An embedded database (H2).** Extra moving parts with no benefit until history is kept.

## Consequences

- One `./gradlew bootRun` starts the back end on macOS or Windows, with nothing else to install.
- The store sits behind its own class, so moving to PostGIS later only changes `SiteStore` and the code that fills it.
- Phase 3 (price history, "best time to charge") needs storage over time. That's when PostgreSQL with PostGIS comes back, together with Testcontainers.
