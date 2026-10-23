# Data sources

All data comes from feeds that charging operators publish to meet AFIR Article 20. They are listed on Denmark's National Access Point, [Dataudveksleren](https://du-portal-ui.dataudveksler.app.vd.dk/). Since 14 April 2026, DATEX II (CEN/TS 16157-10:2022, model version 3) has been the required format in Denmark. OCPI is still used between partners.

Last checked: 2026-10-23.

## Overview

| Source | NAP dataset | Format | Access | Licence | Update rate |
|---|---|---|---|---|---|
| Spirii | (Spirii's own API; OCPI dataset 1004 on the NAP) | DATEX II v3 | Public, no key | Not stated | Not stated |
| Monta platform | 1092 | DATEX II | Monta Public API credentials | CC BY 4.0 | Up to 1 min |
| Eco-Movement | 1170 | DATEX II | Token from support@eco-movement.com | CC BY 4.0 | Up to 1 min |
| OK | 1096 | DATEX II v3 | To be confirmed | Other | Up to 5 min |
| E.ON Drive | 897 | DATEX II | Agreement needed | Restricted | |
| EV-EL | 1101 | Not stated | To be confirmed | CC BY 4.0 | |
| nef Strøm | 1174 (static), 1175 (status) | DATEX II v3 | To be confirmed | | |
| TotalEnergies | 1152 | OCPI 2.2.1 (via Spirii) | To be confirmed | | |
| EDF Danmark | 994 | OCPI 2.2.0 | Restricted | | EDF stations also appear in Spirii's public feed |

The NAP's dataset search works without logging in:
`https://businessservice.dataudveksler.app.vd.dk/api/Search/search?query=<text>&page=1&pageSize=50`

## Spirii (start here)

Docs: <https://docs.spirii.com/developers/afir-compliance>

| Endpoint | Content |
|---|---|
| `GET https://api.spirii.com/v2/afir/energy-infrastructure-tables` | Static data: sites, stations, charge points, connectors, opening hours, tariffs |
| `GET https://api.spirii.com/v2/afir/energy-infrastructure-statuses` | Dynamic data: status of each charge point and the current energy prices |

- Filters: `countryIsos=DK`, `cpoId`, `customerIds`. With no filter, each endpoint returns only 100 records.
- Responses are gzip-encoded. Send `If-Modified-Since` to get `304 Not Modified` when nothing has changed. Poll statuses more often than tables.
- No rate limits or terms of use are stated.

### What a test call returned (2026-10-23, `countryIsos=DK`)

- Tables: about 1.2 MB, **1,000 sites** and about 4,200 charge points. Exactly 1,000 suggests a page limit, so check pagination.
- Operators in the feed include Spirii ApS, PowerGo, Verdo, Aura, EDF Danmark, Looad, Shell Recharge, Region H, DEAS and By & Havn.
- Status values: `available`, `occupied`, `blocked`, `inoperative`, `outOfOrder`.
- Connector example: `connectorType: iec62196T2`, `maxPowerAtSocket: 22000` (W), `currentType: ac`.
- Charge points carry an EVSE ID, e.g. `DK.SPI.Z507*1`, in `externalIdentifier` with `extendedValueG: evseId`. Use it as the stable key across feeds.

### Where the price is

```
messageContainer.payload[0]
  .aegiEnergyInfrastructureStatusPublication
  .energyInfrastructureSiteStatus[]
    .energyInfrastructureStationStatus[]
      .refillPointStatus[]
        .aegiElectricChargingPointStatus
          .status.value                      -> "available", ...
          .energyRateUpdate[]
            .energyRateReference.idG         -> links to the energy rate in the table
            .energyPrice[]
              .priceType.value               -> "pricePerKWh" | "pricePerMinute" | "flatRate"
              .value                         -> e.g. 5 (DKK)
              .taxIncluded / .taxRate        -> true / 25
              .overallPeriod
                .overallStartTime / .overallEndTime
                .validPeriod[].recurringTimePeriodOfDay[]
                  .startTimeOfPeriod / .endTimeOfPeriod   -> 15-minute slots, e.g. "21:45" to "22:00"
```

- The currency is in the static table: `applicableCurrency: ["DKK"]`.
- `pricePerKWh` values ranged from 0 to 6.97 DKK. A few rates also have a `pricePerMinute` or a `flatRate` (start fee) part, so "price per kWh" alone doesn't always tell the full cost.

## Monta platform

- NAP dataset 1092 covers Uno-X, Stella Energy, Nortec, NRGi, Monta, Norlys and smaller operators.
- Sign-up and auth: <https://docs.public-api.monta.com/reference/home>
- `GET https://public-api.monta.com/api/v1/afir/charge-points` returns DATEX II `EnergyInfrastructureTablePublication`, paged with `page` and `perPage`, and filtered with `country=DK`.
- `GET .../afir/charge-points/{evseId}/status` gives live availability and the ad hoc price.
- The old partner API endpoint was retired on 8 September 2026. Use the Public API.

## Eco-Movement

- NAP dataset 1170 covers Allego, Circle K, Clever, Eviny, Fastned, IONITY, Lidl, Mercedes-Benz High-Power Charging, Milence, Q8, Q-Park and Shell Recharge.
- A country snapshot is delivered through one endpoint. EVSE status and ad hoc price are included where available.
- A token is needed. Request it from support@eco-movement.com.

## Open questions

- [ ] Spirii: how to page beyond 1,000 sites. Any rate limit?
- [ ] OK and nef Strøm: how to get access. Is it a public URL behind the NAP entry?
- [ ] E.ON Drive: will they grant access for a non-commercial app?
- [ ] Tesla Superchargers: no Danish NAP dataset found yet.
- [ ] Duplicates: Shell Recharge appears in both the Spirii and Eco-Movement feeds. Merge on EVSE ID and decide which source wins.
- [ ] Price model: how to show start fees and per-minute fees next to the price per kWh.
