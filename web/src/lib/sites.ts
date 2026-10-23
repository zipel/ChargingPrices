// Filtering sites and choosing which price to show, depending on the selected charging speed

import type { SiteSummary, SpeedClass } from '../api'

export type SpeedFilter = 'ALL' | SpeedClass

export interface Filters {
  speed: SpeedFilter
  availableOnly: boolean
  operator: string
  includeUnpriced: boolean
}

export const DEFAULT_FILTERS: Filters = {
  speed: 'ALL',
  availableOnly: false,
  operator: '',
  includeUnpriced: false,
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

export function siteTitle(site: { name: string | null; street: string | null; operator: string | null }): string {
  return site.name || site.street || site.operator || 'Charging station'
}

export const SPEED_LABELS: Record<SpeedClass, string> = {
  STANDARD: 'Standard',
  FAST: 'Fast',
  ULTRA: 'Ultra-fast',
}

export const SPEED_HINTS: Record<SpeedClass, string> = {
  STANDARD: 'up to 49 kW',
  FAST: '50–149 kW',
  ULTRA: '150 kW and up',
}
