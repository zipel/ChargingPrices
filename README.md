# ChargingPrices

A map of public EV charging stations in Denmark that shows what each operator charges per kWh right now.

> **Status:** planning. There's no code yet. See the [roadmap](#roadmap).

## The problem

Denmark has many charging operators: Clever, OK, Spirii, E.ON, Norlys, Monta, Lidl and more. Each one has its own app and its own prices, and many prices change during the day. If you pay as you go, without a subscription, there's no simple way to see who is cheapest near you at this moment.

## Why this is possible now

Since 14 April 2026, Article 20 of the EU's Alternative Fuels Infrastructure Regulation (AFIR) has required operators of public chargers to publish their locations, live status and **ad hoc price** through each country's National Access Point. In Denmark that is [Dataudveksleren](https://du-portal-ui.dataudveksler.app.vd.dk/), run by Vejdirektoratet, and the required format is DATEX II.

ChargingPrices reads those feeds, maps them into one common model and shows the result on a map.

## What the first version will do

- A map of Denmark with stations clustered and coloured by their current price per kWh.
- Filters for charging speed (AC, fast DC, high-power), connector type, operator and "available now".
- A station panel with the operator, address, connectors and kW, live status, the price now and today's price curve.
- "Cheapest near me".
- A mobile-first web app, built so it can later become a PWA and then a phone app.

Prices are the ad hoc prices (pay as you go, VAT included) that each operator publishes. Subscription prices, such as Clever One or Norlys member discounts, are out of scope for now.

## Data sources

| Source | Operators covered | Access | Licence |
|---|---|---|---|
| Spirii AFIR API (DATEX II) | Spirii and operators on its platform, e.g. PowerGo, Verdo, Aura, EDF, Shell Recharge, By & Havn | Public, no key | Not stated |
| Monta Public API (DATEX II) | Monta, Norlys, Uno-X, NRGi, Stella Energy and others | Free API credentials | CC BY 4.0 |
| Eco-Movement (DATEX II) | Clever, Lidl, Circle K, IONITY, Allego, Fastned, Q8, Milence and others | Token on request | CC BY 4.0 |
| OK (DATEX II) | OK | To be confirmed | Other |
| E.ON Drive (DATEX II) | E.ON | Agreement needed | Restricted |

Details, endpoints and open questions are in [docs/data-sources.md](docs/data-sources.md).

## Architecture (planned)

```
Operator feeds (DATEX II, OCPI)
        |  scheduled fetch with If-Modified-Since
        v
Feed adapters, one per source  -->  common model  -->  PostgreSQL + PostGIS
                                                             |
                                                  REST API (Spring Boot)
                                                             |
                                                Web app (React + MapLibre)
```

## Stack

- **Back end:** Java 25, Spring Boot 4, Gradle
- **Database:** PostgreSQL with PostGIS, migrations with Flyway
- **Tests:** JUnit 5, Testcontainers, real feed samples as fixtures
- **Front end:** React, TypeScript, Vite, MapLibre GL JS
- **Running it:** Docker Compose. CI on GitHub Actions.

The reasons are in [docs/decisions/0001-stack.md](docs/decisions/0001-stack.md).

## Roadmap

1. **One feed end to end.** Fetch the Spirii feed, store it, serve it through the API and show it on the map. Spirii's feed is public, so this needs no keys.
2. **More operators.** Add Monta, Eco-Movement and OK, plus E.ON if access is granted. Merge stations that appear in more than one feed, using their EVSE IDs.
3. **Prices over time.** Keep price history, show "best time to charge today" and put a public demo online.
4. **Later.** An assistant for questions like "cheapest charge on my way to Aarhus before 18:00", and a phone app (PWA first).

## Running locally

Not yet. The plan is a single `docker compose up`.

## How it's built

Built with [Claude Code](https://claude.com/claude-code) as a coding assistant. Design decisions are written down in [docs/decisions](docs/decisions).

## Data, attribution and disclaimer

The charging data belongs to the operators and is published under AFIR. Data under CC BY 4.0 (Monta, Eco-Movement and others) will be credited in the app. ChargingPrices isn't affiliated with any operator. Prices can change at short notice, so always check the price at the charger or in the operator's app before you charge.

## Licence

The code is released under the [MIT licence](LICENSE).
