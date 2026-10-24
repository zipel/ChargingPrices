# 5. Place search through a back-end proxy to Nominatim

- **Status:** accepted
- **Date:** 2026-10-23

## Context

People look for chargers near a town or an address, not near map coordinates, so the app needs a search box. The search must be free, need no account, and cover Danish addresses.

## Decision

Use OpenStreetMap's **Nominatim** geocoder, called by the back end (`GET /api/places?q=...`), never directly from the browser.

Nominatim is free but has a [usage policy](https://operations.osmfoundation.org/policies/nominatim/): identify the app, send at most one request per second, and cache results. Going through the back end lets the app follow it:

- `NominatimClient` sends a User-Agent with the app name and the repository URL, and limits results to Denmark.
- `PlaceService` caches results (500 entries, least recently used dropped first) and lets one request through per second. At most 3 searches wait their turn; more get `429 Too Many Requests` rather than tying up server threads.
- The UI searches when the user presses Enter, not on every keystroke.
- Geocoder failures answer `503` and aren't cached.

## Alternatives considered

- **Calling Nominatim from the browser.** Simpler, but every visitor would hit Nominatim directly, with no shared cache and no rate limit.
- **A Danish address API (DAWA or the Datafordeler).** Very good for addresses, but weaker for towns and places, so the app would still need a second service.
- **Google Places or Mapbox search.** Need an account, an API key and a billing setup (see [0004](0004-map-tiles.md)).

## Consequences

- Search results credit OpenStreetMap through the map's attribution, which is already shown.
- If the app ever gets real traffic, Nominatim's public server won't be enough. A self-hosted Nominatim or a paid geocoder can replace `NominatimClient` behind the `PlaceLookup` interface.
