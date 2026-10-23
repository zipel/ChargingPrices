import { useState } from 'react'
import type { PricePoint } from '../api'
import { colourOf, formatPrice } from '../lib/price'

const WIDTH = 340
const HEIGHT = 120
const AXIS = 18
const TOP = 6

interface Props {
  points: PricePoint[]
  thresholds: number[]
}

/** Bars for the next 24 hours in 15-minute slots. The first bar is the current slot. */
export function PriceChart({ points, thresholds }: Props) {
  const [hovered, setHovered] = useState<number | null>(null)
  if (points.length === 0) {
    return null
  }

  const max = Math.max(...points.map((p) => p.price)) * 1.1 || 1
  const barWidth = WIDTH / points.length
  const plotHeight = HEIGHT - AXIS - TOP
  const cheapest = points.reduce((best, p) => (p.price < best.price ? p : best), points[0])
  const allSame = points.every((p) => p.price === points[0].price)
  const shown = hovered != null ? points[hovered] : points[0]

  const indexAt = (clientX: number, target: SVGSVGElement) => {
    const rect = target.getBoundingClientRect()
    const index = Math.floor(((clientX - rect.left) / rect.width) * points.length)
    return Math.min(points.length - 1, Math.max(0, index))
  }

  return (
    <figure className="chart">
      <figcaption className="chart-caption">
        <span>
          <strong>{formatPrice(shown.price)}</strong> kr/kWh at {timeOf(shown.start)}
          {hovered == null && <span className="muted"> (now)</span>}
        </span>
        {!allSame && (
          <span className="muted">
            Cheapest {formatPrice(cheapest.price)} at {timeOf(cheapest.start)}
          </span>
        )}
      </figcaption>
      <svg
        viewBox={`0 0 ${WIDTH} ${HEIGHT}`}
        role="img"
        aria-label={`Price for the next ${Math.round(points.length / 4)} hours, from ${formatPrice(Math.min(...points.map((p) => p.price)))} to ${formatPrice(Math.max(...points.map((p) => p.price)))} kr/kWh`}
        onPointerMove={(e) => setHovered(indexAt(e.clientX, e.currentTarget))}
        onPointerLeave={() => setHovered(null)}
      >
        {points.map((p, i) => {
          const h = (p.price / max) * plotHeight
          return (
            <rect
              key={p.start}
              x={i * barWidth + 0.5}
              y={TOP + plotHeight - h}
              width={Math.max(barWidth - 1, 1)}
              height={h}
              rx={1}
              fill={colourOf(p.price, thresholds)}
              opacity={hovered == null ? (i === 0 ? 1 : 0.75) : i === hovered ? 1 : 0.45}
            />
          )
        })}
        <line x1={0} x2={WIDTH} y1={TOP + plotHeight + 0.5} y2={TOP + plotHeight + 0.5} className="chart-axis" />
        {points.map((p, i) => {
          const d = new Date(p.start)
          if (d.getMinutes() !== 0 || d.getHours() % 3 !== 0) return null
          return (
            <text key={`t-${p.start}`} x={i * barWidth} y={HEIGHT - 4} className="chart-tick">
              {String(d.getHours()).padStart(2, '0')}
            </text>
          )
        })}
      </svg>
    </figure>
  )
}

function timeOf(iso: string): string {
  return new Date(iso).toLocaleTimeString('en-GB', { hour: '2-digit', minute: '2-digit', timeZone: 'Europe/Copenhagen' })
}
