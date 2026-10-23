// Price formatting and the colour scale used on the map, the legend and the chart

/** Five colours from cheapest to most expensive, plus grey for "no price published". */
export const BAND_COLOURS = ['#15803d', '#65a30d', '#ca8a04', '#ea580c', '#dc2626'] as const
export const NO_PRICE_COLOUR = '#9ca3af'

const priceFormat = new Intl.NumberFormat('en-GB', { minimumFractionDigits: 2, maximumFractionDigits: 2 })

export function formatPrice(price: number | null | undefined): string {
  return price == null ? '–' : priceFormat.format(price)
}

/**
 * Splits the prices into five equally large groups and returns the four boundaries between them.
 * Using the data's own spread means the colours stay useful whether prices are high or low today.
 */
export function bandThresholds(prices: number[]): number[] {
  const sorted = prices.filter((p) => Number.isFinite(p)).sort((a, b) => a - b)
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

/** Legend labels such as "under 2.40", "2.40–2.79", ..., "3.60 and up". */
export function bandLabels(thresholds: number[]): string[] {
  const below = (value: number) => formatPrice(Math.round((value - 0.01) * 100) / 100)
  return BAND_COLOURS.map((_, band) => {
    if (band === 0) return `under ${formatPrice(thresholds[0])}`
    if (band === thresholds.length) return `${formatPrice(thresholds[band - 1])} and up`
    return `${formatPrice(thresholds[band - 1])}–${below(thresholds[band])}`
  })
}
