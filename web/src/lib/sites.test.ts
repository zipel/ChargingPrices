import { describe, expect, it } from 'vitest'
import type { SiteSummary } from '../api'
import { DEFAULT_FILTERS, cheaperNearby, cheapestIn, siteTitle, toMapSites } from './sites'

function site(id: string, overrides: Partial<SiteSummary> = {}): SiteSummary {
  return {
    id,
    name: `Site ${id}`,
    operator: 'Spirii ApS',
    lat: 55.68,
    lon: 12.57,
    street: null,
    city: 'København',
    restricted: false,
    priceNow: 3,
    available: 1,
    total: 2,
    groups: [
      { speed: 'STANDARD', maxPowerKw: 22, priceNow: 3, available: 1, total: 1 },
      { speed: 'ULTRA', maxPowerKw: 300, priceNow: 4.5, available: 0, total: 1 },
    ],
    ...overrides,
  }
}

describe('toMapSites', () => {
  it('uses the price of the selected speed', () => {
    const [ultra] = toMapSites([site('a')], { ...DEFAULT_FILTERS, speed: 'ULTRA' })
    expect(ultra.price).toBe(4.5)
    expect(ultra.maxPowerKw).toBe(300)
  })

  it('drops sites without the selected speed', () => {
    expect(toMapSites([site('a')], { ...DEFAULT_FILTERS, speed: 'FAST' })).toHaveLength(0)
  })

  it('can show only sites with a free charger', () => {
    expect(toMapSites([site('a')], { ...DEFAULT_FILTERS, speed: 'ULTRA', availableOnly: true })).toHaveLength(0)
    expect(toMapSites([site('a')], { ...DEFAULT_FILTERS, speed: 'STANDARD', availableOnly: true })).toHaveLength(1)
  })

  it('hides sites without a price unless asked', () => {
    const unpriced = site('b', { priceNow: null })
    expect(toMapSites([unpriced], DEFAULT_FILTERS)).toHaveLength(0)
    expect(toMapSites([unpriced], { ...DEFAULT_FILTERS, includeUnpriced: true })).toHaveLength(1)
  })

  it('hides sites that look private unless asked', () => {
    const housing = site('p', { name: 'Taastrupgaard 17 (Privat)', restricted: true })
    expect(toMapSites([housing], DEFAULT_FILTERS)).toHaveLength(0)
    expect(toMapSites([housing], { ...DEFAULT_FILTERS, includeRestricted: true })).toHaveLength(1)
  })

  it('filters by operator', () => {
    const sites = [site('a'), site('b', { operator: 'PowerGo' })]
    expect(toMapSites(sites, { ...DEFAULT_FILTERS, operator: 'PowerGo' }).map((s) => s.site.id)).toEqual(['b'])
  })
})

describe('cheapestIn', () => {
  it('returns the cheapest sites inside the bounds, cheapest first', () => {
    const sites = toMapSites(
      [site('a', { priceNow: 3.2 }), site('b', { priceNow: 2.1 }), site('far', { priceNow: 1.0, lat: 57.0 })],
      DEFAULT_FILTERS,
    )
    const bounds = { west: 12, south: 55, east: 13, north: 56 }
    expect(cheapestIn(sites, bounds).map((s) => s.site.id)).toEqual(['b', 'a'])
  })

  it('leaves out sites that publish a price of 0', () => {
    const sites = toMapSites([site('a', { priceNow: 3.2 }), site('zero', { priceNow: 0 })], DEFAULT_FILTERS)
    expect(cheapestIn(sites, { west: 12, south: 55, east: 13, north: 56 }).map((s) => s.site.id)).toEqual(['a'])
  })
})

describe('cheaperNearby', () => {
  const sites = toMapSites(
    [
      site('here', { priceNow: 3.0, lat: 55.68, lon: 12.57 }),
      site('cheaper-close', { priceNow: 2.5, lat: 55.685, lon: 12.575 }),
      site('cheapest-close', { priceNow: 2.0, lat: 55.69, lon: 12.58 }),
      site('dearer-close', { priceNow: 3.5, lat: 55.681, lon: 12.571 }),
      site('cheap-far', { priceNow: 1.0, lat: 56.0, lon: 12.57 }),
    ],
    DEFAULT_FILTERS,
  )

  it('lists cheaper sites within the radius, cheapest first', () => {
    const result = cheaperNearby(sites, { id: 'here', lat: 55.68, lon: 12.57, price: 3.0 })
    expect(result.map((s) => s.site.id)).toEqual(['cheapest-close', 'cheaper-close'])
    expect(result[0].distanceKm).toBeLessThan(3)
  })

  it('lists any priced site when the origin has no price', () => {
    const result = cheaperNearby(sites, { id: 'x', lat: 55.68, lon: 12.57, price: null })
    expect(result.map((s) => s.site.id)).toEqual(['cheapest-close', 'cheaper-close', 'here'])
  })
})

describe('siteTitle', () => {
  it('falls back to the street and then the operator', () => {
    expect(siteTitle({ name: null, street: 'Nannasgade 28', operator: 'Spirii' })).toBe('Nannasgade 28')
    expect(siteTitle({ name: null, street: null, operator: 'Spirii' })).toBe('Spirii')
  })
})
