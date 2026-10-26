import { describe, expect, it } from 'vitest'
import { BAND_COLOURS, NO_PRICE_COLOUR, bandOf, bandRanges, bandThresholds, colourOf, formatPrice, median, setNumberLocale } from './price'

describe('formatPrice', () => {
  it('always shows two decimals', () => {
    expect(formatPrice(3)).toBe('3.00')
    expect(formatPrice(2.456)).toBe('2.46')
  })

  it('shows a dash when there is no price', () => {
    expect(formatPrice(null)).toBe('–')
  })

  it('uses a decimal comma in Danish', () => {
    setNumberLocale('da-DK')
    expect(formatPrice(3.456)).toBe('3,46')
    setNumberLocale('en-GB')
  })
})

describe('bandThresholds', () => {
  it('splits prices into five equal groups', () => {
    const prices = Array.from({ length: 100 }, (_, i) => 1 + i / 100)
    expect(bandThresholds(prices)).toEqual([1.2, 1.4, 1.6, 1.8])
  })

  it('counts each price level once, so one very common price does not squeeze the colours', () => {
    const prices = [...Array(3000).fill(3.49), 1.5, 2.0, 2.5, 3.0, 4.0, 4.5, 5.0, 5.5, 6.0, 6.5]
    const thresholds = bandThresholds(prices)
    expect(thresholds[0]).toBeLessThan(3)
    expect(thresholds[3]).toBeGreaterThan(4)
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

  it('gives each band its price range', () => {
    expect(bandRanges(thresholds)).toEqual([
      { from: null, to: 1.99 },
      { from: 2, to: 2.49 },
      { from: 2.5, to: 2.99 },
      { from: 3, to: 3.49 },
      { from: 3.5, to: null },
    ])
  })
})

describe('median', () => {
  it('takes the middle value, or the mean of the two middle values', () => {
    expect(median([3, 1, 2])).toBe(2)
    expect(median([4, 1, 3, 2])).toBe(2.5)
    expect(median([])).toBeNull()
  })
})
