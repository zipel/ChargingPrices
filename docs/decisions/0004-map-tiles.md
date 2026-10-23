# 4. MapLibre with OpenFreeMap tiles

- **Status:** accepted
- **Date:** 2026-10-23

## Context

The app needs a base map of Denmark that costs nothing, needs no account, and still looks clean with about 2,000 coloured markers on top.

## Decision

Use **MapLibre GL JS** (open source, BSD licence) with the **Positron** style from **[OpenFreeMap](https://openfreemap.org/)**, which serves free vector tiles built from OpenStreetMap data, with no API key and no usage limits.

The style URL is a single constant in `web/src/components/MapView.tsx`, so another provider means a one-line change.

## Alternatives considered

- **Google Maps.** Needs an API key tied to a billing account with a card on file. Too much friction for a hobby project, and the key would have to be kept out of a public repo.
- **Mapbox.** Same problem: an account and an access token, with paid tiers.
- **Leaflet with raster OpenStreetMap tiles.** OpenStreetMap's own tile servers don't allow heavy app use, raster tiles look blurry on high-DPI screens, and thousands of DOM markers get slow.

## Consequences

- The map credits OpenFreeMap, OpenMapTiles and OpenStreetMap contributors in its attribution control. That has to stay visible.
- If OpenFreeMap goes down, the map has no base layer. Self-hosting the tiles is possible later, because OpenFreeMap is open source too.
- MapLibre 6 runs a web worker, which needs a small setup step in Vite (see the comment in `MapView.tsx`).
