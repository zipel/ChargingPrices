import { describe, expect, it } from 'vitest'
import { BAND_COLOURS, NO_PRICE_COLOUR, bandLabels, bandOf, bandThresholds, colourOf, formatPrice } from './price'

describe('formatPrice', () => {
  it('always shows two decimals', () => {
    expect(formatPrice(3)).toBe('3.00')
    expect(formatPrice(2.456)).toBe('2.46')
  })

  it('shows a dash when there is no price', () => {
    expect(formatPrice(null)).toBe('–')
  })
})

describe('bandThresholds', () => {
  it('splits prices into five equal groups', () => {
    const prices = Array.from({ length: 100 }, (_, i) => 1 + i / 100)
    expect(bandThresholds(prices)).toEqual([1.2, 1.4, 1.6, 1.8])
  })

  it('keeps boundaries increasing when many prices are the same', () => {
    const thresholds = bandThresholds([3, 3, 3, 3, 3, 3, 3, 3, 4, 5])
    for (let i = 1; i < thresholds.length; i++) {
      expect(thresholds[i]).toBeGreaterThan(thresholds[i - 1])
    }
  })
})

describe('bandOf and colourOf', () => {
  const thresholds = [2, 2.5, 3, 3.5]

  it('puts a price in the band below the first boundary it does not reach', () => {
    expect(bandOf(1.5, thresholds)).toBe(0)
    expect(bandOf(2, thresholds)).toBe(1)
    expect(bandOf(3.49, thresholds)).toBe(3)
    expect(bandOf(9, thresholds)).toBe(4)
  })

  it('uses grey when there is no price', () => {
    expect(colourOf(null, thresholds)).toBe(NO_PRICE_COLOUR)
    expect(colourOf(1, thresholds)).toBe(BAND_COLOURS[0])
  })

  it('labels each band', () => {
    expect(bandLabels(thresholds)).toEqual(['under 2.00', '2.00–2.49', '2.50–2.99', '3.00–3.49', '3.50 and up'])
  })
})
