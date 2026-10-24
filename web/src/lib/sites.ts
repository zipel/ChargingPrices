// Filtering sites and choosing which price to show, depending on the selected charging speed

import type { SiteSummary, SpeedClass } from '../api'
import { distanceKm } from './geo'

export type SpeedFilter = 'ALL' | SpeedClass

export interface Filters {
  speed: SpeedFilter
  availableOnly: boolean
  operator: string
  includeUnpriced: boolean
  includeRestricted: boolean
}

export const DEFAULT_FILTERS: Filters = {
  speed: 'ALL',
  availableOnly: false,
  operator: '',
  includeUnpriced: false,
  includeRestricted: false,
}

/** A site as the map shows it: the price and availability for the selected speed. */
export interface MapSite {
  site: SiteSummary
  price: number | null
  available: number
  total: number
  maxPowerKw: number | null
}

export function toMapSites(sites: SiteSummary[], filters: Filters): MapSite[] {
  const result: MapSite[] = []
  for (const site of sites) {
    if (filters.operator && site.operator !== filters.operator) continue
    if (!filters.includeRestricted && site.restricted) continue

    let entry: MapSite
    if (filters.speed === 'ALL') {
      const maxPowerKw = Math.max(0, ...site.groups.map((g) => g.maxPowerKw ?? 0)) || null
      entry = { site, price: site.priceNow, available: site.available, total: site.total, maxPowerKw }
    } else {
      const group = site.groups.find((g) => g.speed === filters.speed)
      if (!group) continue
      entry = { site, price: group.priceNow, available: group.available, total: group.total, maxPowerKw: group.maxPowerKw }
    }

    if (filters.availableOnly && entry.available === 0) continue
    if (!filters.includeUnpriced && entry.price == null) continue
    result.push(entry)
  }
  return result
}

export interface Bounds {
  west: number
  south: number
  east: number
  north: number
}

/**
 * The cheapest sites inside the visible part of the map.
 * A price of 0 is left out: the few sites that publish it are mostly company chargers, not free public ones.
 */
export function cheapestIn(sites: MapSite[], bounds: Bounds, limit = 5): MapSite[] {
  return sites
    .filter((s) => s.price != null && s.price > 0)
    .filter((s) => s.site.lon >= bounds.west && s.site.lon <= bounds.east && s.site.lat >= bounds.south && s.site.lat <= bounds.north)
    .sort((a, b) => (a.price as number) - (b.price as number) || b.available - a.available)
    .slice(0, limit)
}

export interface NearbySite extends MapSite {
  distanceKm: number
}

/**
 * Sites within {@code radiusKm} that are cheaper than {@code origin} right now, cheapest first.
 * When the origin has no price, any site with a price counts.
 */
export function cheaperNearby(
  sites: MapSite[],
  origin: { id: string; lat: number; lon: number; price: number | null },
  radiusKm = 3,
  limit = 3,
): NearbySite[] {
  return sites
    .filter((s) => s.site.id !== origin.id && s.price != null && s.price > 0)
    .filter((s) => origin.price == null || (s.price as number) < origin.price)
    .map((s) => ({ ...s, distanceKm: distanceKm(origin, s.site) }))
    .filter((s) => s.distanceKm <= radiusKm)
    .sort((a, b) => (a.price as number) - (b.price as number) || a.distanceKm - b.distanceKm)
    .slice(0, limit)
}

export function siteTitle(site: { name: string | null; street: string | null; operator: string | null }): string {
  return site.name || site.street || site.operator || 'Charging station'
}
