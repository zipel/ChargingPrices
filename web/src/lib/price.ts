// Price formatting and the colour scale used on the map, the legend and the chart

/**
 * Five colours from cheapest to most expensive, plus grey for "no price published". All are dark enough
 * for white text on top to reach WCAG AA contrast (4.5:1 or more).
 */
export const BAND_COLOURS = ['#15803d', '#4d7c0f', '#a16207', '#c2410c', '#b91c1c'] as const
export const NO_PRICE_COLOUR = '#6b7280'

let numberLocale = 'en-GB'
let priceFormat = new Intl.NumberFormat(numberLocale, { minimumFractionDigits: 2, maximumFractionDigits: 2 })

/** Set by the language switch: Danish writes 3,46 and English 3.46. */
export function setNumberLocale(locale: string) {
  numberLocale = locale
  priceFormat = new Intl.NumberFormat(locale, { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

export function currentLocale(): string {
  return numberLocale
}

export function formatPrice(price: number | null | undefined): string {
  return price == null ? '–' : priceFormat.format(price)
}

/**
 * Splits the price levels into five groups and returns the four boundaries between them.
 *
 * Each distinct price (to the øre) counts once, however many sites charge it. Otherwise one very common
 * price, like Clever's 3.49 kr at over 3,000 sites, would squeeze all five colours into a few øre.
 * Using the data's own levels keeps the colours useful whether prices are high or low today.
 */
export function bandThresholds(prices: number[]): number[] {
  const levels = new Set(prices.filter((p) => Number.isFinite(p)).map((p) => Math.round(p * 100) / 100))
  const sorted = [...levels].sort((a, b) => a - b)
  if (sorted.length === 0) {
    return [2, 2.5, 3, 3.5]
  }
  const at = (fraction: number) => sorted[Math.min(sorted.length - 1, Math.floor(fraction * sorted.length))]
  const thresholds = [0.2, 0.4, 0.6, 0.8].map(at)
  // Equal neighbours would make a band empty; nudge them apart so every colour still means "more expensive"
  for (let i = 1; i < thresholds.length; i++) {
    if (thresholds[i] <= thresholds[i - 1]) {
      thresholds[i] = thresholds[i - 1] + 0.01
    }
  }
  return thresholds
}

export function bandOf(price: number, thresholds: number[]): number {
  let band = 0
  while (band < thresholds.length && price >= thresholds[band]) {
    band++
  }
  return band
}

export function colourOf(price: number | null | undefined, thresholds: number[]): string {
  return price == null ? NO_PRICE_COLOUR : BAND_COLOURS[bandOf(price, thresholds)]
}

/**
 * The price range of each colour band, for the legend. The first band has no lower end and the last no
 * upper end. Upper ends are one øre below the next boundary, e.g. 2.40–2.79.
 */
export function bandRanges(thresholds: number[]): { from: number | null; to: number | null }[] {
  const below = (value: number) => Math.round((value - 0.01) * 100) / 100
  return BAND_COLOURS.map((_, band) => ({
    from: band === 0 ? null : thresholds[band - 1],
    to: band === thresholds.length ? null : below(thresholds[band]),
  }))
}

/** The middle value; the mean of the two middle values for an even count. */
export function median(values: number[]): number | null {
  if (values.length === 0) return null
  const sorted = [...values].sort((a, b) => a - b)
  const middle = Math.floor(sorted.length / 2)
  return sorted.length % 2 === 1 ? sorted[middle] : (sorted[middle - 1] + sorted[middle]) / 2
}
