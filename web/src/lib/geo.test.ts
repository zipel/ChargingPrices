import { describe, expect, it } from 'vitest'
import { distanceKm, formatDistance } from './geo'

describe('distanceKm', () => {
  it('measures Copenhagen to Aarhus at about 157 km', () => {
    const copenhagen = { lat: 55.6761, lon: 12.5683 }
    const aarhus = { lat: 56.1629, lon: 10.2039 }
    expect(distanceKm(copenhagen, aarhus)).toBeGreaterThan(150)
    expect(distanceKm(copenhagen, aarhus)).toBeLessThan(165)
  })

  it('is zero for the same point', () => {
    expect(distanceKm({ lat: 55, lon: 12 }, { lat: 55, lon: 12 })).toBe(0)
  })
})

describe('formatDistance', () => {
  it('uses metres below 1 km and km above', () => {
    expect(formatDistance(0.43)).toBe('450 m')
    expect(formatDistance(2.345)).toBe('2.3 km')
    expect(formatDistance(2.345, 'da-DK')).toBe('2,3 km')
  })
})
