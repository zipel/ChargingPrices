# 2. Use the official AFIR feeds, not scraping

- **Status:** accepted
- **Date:** 2026-10-23

## Context

Station and price data could come from three places: the operators' own apps and websites, community databases such as Open Charge Map or OpenStreetMap, or the feeds operators must publish under AFIR Article 20.

## Decision

Use only the AFIR feeds listed on the Danish National Access Point (see [data-sources.md](../data-sources.md)).

- They include the **ad hoc price** and live status, which community databases don't have in a structured form. Open Charge Map's cost field is free text.
- Operators publish them on purpose, in one standard format (DATEX II), and several are under CC BY 4.0.
- Scraping operator apps would break their terms of use and fail every time an app changes.

## Consequences

- Coverage depends on which feeds we can access. Spirii's is open. Monta and Eco-Movement need credentials. E.ON may refuse access, and its stations would then be missing.
- Every CC BY source must be credited in the app.
- Polling has to be polite: use `If-Modified-Since`, cache responses and respect rate limits.
