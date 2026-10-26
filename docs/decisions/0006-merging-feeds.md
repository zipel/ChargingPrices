# 6. Merging feeds that overlap

- **Status:** accepted
- **Date:** 2026-10-26

## Context

With a second source, Eco-Movement, some charging sites appear twice. Shell's 87 Danish stations are in Spirii's feed (operator "Shell Recharge") and in Eco-Movement's (operator "Spirii"). The EVSE IDs differ between the two (`DK.DCC.00923701*1` against `31948*163840`), so they can't be matched by ID alone. The names and coordinates are identical.

## Decision

`SiteStore` merges the feeds in a fixed priority order, Spirii first, then Eco-Movement (`SiteStore.SOURCE_PRIORITY`). `SiteDeduplicator` drops a site from a later feed when either:

- one of its EVSE IDs matches an earlier feed's charge point, comparing letters and digits only (so `DK*SPI*E1` equals `DK.SPI.E1`), or
- an earlier feed has a site with the same name, compared the same way, within 50 metres.

Spirii wins because its own feed carries 15-minute dynamic prices and its operators' live status straight from the source.

Sites within one feed are never merged with each other. Different operators in the same car park have different names, so they stay separate.

## Alternatives considered

- **EVSE IDs only.** Simple and exact, but misses Shell, the one real overlap found.
- **Location only.** Would wrongly merge different operators' chargers in the same car park; 9 Clever sites sit within 15 m of a Spirii site.
- **Merging charge points from both feeds into one site.** More complete, but the two feeds disagree on IDs and sometimes on prices, and showing one source per site keeps the price story clear.

## Consequences

- The site counts in `/api/meta` are after merging: 2,329 Spirii + 3,921 Eco-Movement − 87 duplicates = 6,163 (2026-10-26).
- Merging runs on every store update. A grid of about 100 m cells keeps it fast enough for thousands of sites.
- A new feed only needs a place in `SOURCE_PRIORITY`.
