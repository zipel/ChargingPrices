// Comparing operators by their current prices

import { median } from './price'
import type { MapSite } from './sites'

export interface OperatorStats {
  operator: string
  medianPrice: number
  cheapestPrice: number
  pricedSites: number
}

/**
 * Each operator's median and lowest price right now, cheapest median first. The median is used rather than
 * the mean, so a few very cheap or very expensive sites don't skew an operator's typical price.
 * Prices of 0 are left out, as on the map.
 */
export function operatorStats(sites: MapSite[]): OperatorStats[] {
  const byOperator = new Map<string, number[]>()
  for (const { site, price } of sites) {
    if (!site.operator || price == null || price <= 0) continue
    const prices = byOperator.get(site.operator) ?? []
    prices.push(price)
    byOperator.set(site.operator, prices)
  }
  return [...byOperator.entries()]
    .map(([operator, prices]) => ({
      operator,
      medianPrice: median(prices) as number,
      cheapestPrice: Math.min(...prices),
      pricedSites: prices.length,
    }))
    .sort((a, b) => a.medianPrice - b.medianPrice || b.pricedSites - a.pricedSites)
}
