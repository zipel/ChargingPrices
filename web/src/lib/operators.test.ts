import { describe, expect, it } from 'vitest'
import type { SiteSummary } from '../api'
import { operatorStats } from './operators'
import type { MapSite } from './sites'

function entry(operator: string | null, price: number | null): MapSite {
  const site = { id: `${operator}-${price}`, operator } as SiteSummary
  return { site, price, available: 1, total: 1, maxPowerKw: 22 }
}

describe('operatorStats', () => {
  it('ranks operators by their median price', () => {
    const stats = operatorStats([
      entry('Dear', 4.0),
      entry('Dear', 3.8),
      entry('Cheap', 2.0),
      entry('Cheap', 2.4),
      entry('Cheap', 9.0),
    ])

    expect(stats.map((s) => s.operator)).toEqual(['Cheap', 'Dear'])
    expect(stats[0]).toEqual({ operator: 'Cheap', medianPrice: 2.4, cheapestPrice: 2.0, pricedSites: 3 })
    expect(stats[1].medianPrice).toBeCloseTo(3.9)
  })

  it('skips sites without a price, a price of 0 or an operator', () => {
    const stats = operatorStats([entry('A', null), entry('A', 0), entry(null, 2), entry('A', 3)])
    expect(stats).toEqual([{ operator: 'A', medianPrice: 3, cheapestPrice: 3, pricedSites: 1 }])
  })
})
