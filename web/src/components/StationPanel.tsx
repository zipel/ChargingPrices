import { useState } from 'react'
import type { Availability, ChargePointView, SiteDetail, SpeedClass } from '../api'
import { colourOf, formatPrice } from '../lib/price'
import { SPEED_HINTS, SPEED_LABELS, siteTitle } from '../lib/sites'
import { PriceChart } from './PriceChart'

interface Props {
  detail: SiteDetail | null
  loading: boolean
  error: string | null
  thresholds: number[]
  preferredSpeed: SpeedClass | null
  onClose: () => void
}

export function StationPanel({ detail, loading, error, thresholds, preferredSpeed, onClose }: Props) {
  // The tab the user clicked. App gives this panel a new key per station, which resets it.
  const [chosenSpeed, setSpeed] = useState<SpeedClass | null>(null)
  const speeds = detail?.curves.map((c) => c.speed) ?? []
  // Otherwise show the speed the user filtered on, or the first one the site has
  const defaultSpeed = preferredSpeed && speeds.includes(preferredSpeed) ? preferredSpeed : (speeds[0] ?? null)
  const speed = chosenSpeed && speeds.includes(chosenSpeed) ? chosenSpeed : defaultSpeed

  return (
    <aside className="panel station" aria-label="Station details">
      <button className="icon-button close" onClick={onClose} aria-label="Close">
        <svg viewBox="0 0 24 24" width="18" height="18" aria-hidden="true">
          <path d="M6 6l12 12M18 6 6 18" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
        </svg>
      </button>

      {!detail && loading && <p className="muted">Loading station…</p>}
      {!detail && error && <p className="error">{error}</p>}

      {detail && (
        <>
          <header className="station-header">
            <p className="eyebrow">{detail.operator ?? 'Unknown operator'}</p>
            <h2>{siteTitle(detail)}</h2>
            <p className="muted">{[detail.street, [detail.postcode, detail.city].filter(Boolean).join(' ')].filter(Boolean).join(', ')}</p>
            <div className="badges">
              {detail.restricted && <span className="badge warning">Looks private</span>}
              {detail.openAllHours && <span className="badge">Open 24/7</span>}
              <span className="badge">{detail.chargePoints.length} charge point{detail.chargePoints.length === 1 ? '' : 's'}</span>
            </div>
          </header>

          <section className="price-now">
            <span className="price-big" style={{ color: colourOf(detail.priceNow, thresholds) }}>
              {formatPrice(detail.priceNow)}
            </span>
            <span className="price-unit">
              kr/kWh now
              <br />
              <span className="muted">incl. VAT, pay as you go</span>
            </span>
          </section>

          {detail.curves.length > 0 && (
            <section>
              <div className="section-title">
                <h3>Next 24 hours</h3>
                {detail.curves.length > 1 && (
                  <div className="tabs" role="tablist">
                    {detail.curves.map((curve) => (
                      <button
                        key={curve.speed}
                        role="tab"
                        aria-selected={curve.speed === speed}
                        className={curve.speed === speed ? 'tab active' : 'tab'}
                        onClick={() => setSpeed(curve.speed)}
                      >
                        {SPEED_LABELS[curve.speed]}
                      </button>
                    ))}
                  </div>
                )}
              </div>
              <PriceChart points={detail.curves.find((c) => c.speed === speed)?.points ?? []} thresholds={thresholds} />
            </section>
          )}

          <section>
            <h3>Charge points</h3>
            <ul className="points">
              {groupPoints(detail.chargePoints).map((group) => (
                <li key={group.key} className="point">
                  <div className="point-main">
                    <span className="point-title">
                      {group.connectors}
                      {group.maxPowerKw != null && <span className="muted"> · {Math.round(group.maxPowerKw)} kW</span>}
                    </span>
                    <span className="point-price">{group.priceNow == null ? 'No price' : `${formatPrice(group.priceNow)} kr`}</span>
                  </div>
                  <div className="point-meta">
                    <StatusLine counts={group.counts} />
                    <span className="muted">{SPEED_LABELS[group.speed]} ({SPEED_HINTS[group.speed]})</span>
                  </div>
                  {group.fees.length > 0 && <p className="fees">{group.fees.join(' · ')}</p>}
                </li>
              ))}
            </ul>
          </section>

          <section className="actions">
            <a className="button" href={`https://www.google.com/maps/dir/?api=1&destination=${detail.lat},${detail.lon}`} target="_blank" rel="noreferrer">
              Directions
            </a>
            {detail.statusUpdated && <span className="muted small">Last status change {relativeTime(detail.statusUpdated)}</span>}
          </section>

          <p className="footnote">
            The operator’s own pay-as-you-go price from its AFIR data feed. Subscriptions and apps can be cheaper. Check the price at the charger before
            you start.
          </p>
        </>
      )}
    </aside>
  )
}

const STATUS_LABELS: Record<Availability, string> = {
  AVAILABLE: 'free',
  OCCUPIED: 'in use',
  OUT_OF_SERVICE: 'out of service',
  UNKNOWN: 'status unknown',
}

function StatusLine({ counts }: { counts: Record<Availability, number> }) {
  const parts = (Object.keys(STATUS_LABELS) as Availability[]).filter((a) => counts[a] > 0)
  return (
    <span className="status">
      {parts.map((a) => (
        <span key={a} className="status-item">
          <span className={`dot dot-${a.toLowerCase()}`} aria-hidden="true" />
          {counts[a]} {STATUS_LABELS[a]}
        </span>
      ))}
    </span>
  )
}

interface PointGroup {
  key: string
  speed: SpeedClass
  connectors: string
  maxPowerKw: number | null
  priceNow: number | null
  fees: string[]
  counts: Record<Availability, number>
}

/** Sites often have many identical charge points; show them as one row with counts. */
function groupPoints(points: ChargePointView[]): PointGroup[] {
  const groups = new Map<string, PointGroup>()
  for (const p of points) {
    const connectors = [...new Set(p.connectors.map((c) => c.label))].join(' / ') || 'Connector'
    const fees = feesOf(p)
    const key = [p.speed, connectors, p.maxPowerKw, p.priceNow, fees.join()].join('|')
    let group = groups.get(key)
    if (!group) {
      group = { key, speed: p.speed, connectors, maxPowerKw: p.maxPowerKw, priceNow: p.priceNow, fees, counts: { AVAILABLE: 0, OCCUPIED: 0, OUT_OF_SERVICE: 0, UNKNOWN: 0 } }
      groups.set(key, group)
    }
    group.counts[p.availability]++
  }
  return [...groups.values()]
}

function feesOf(point: ChargePointView): string[] {
  const fees: string[] = []
  if (point.startFee != null && point.startFee > 0) fees.push(`Start fee ${formatPrice(point.startFee)} kr`)
  for (const fee of point.minuteFees) {
    const after = fee.fromMinute > 0 ? ` after ${fee.fromMinute % 60 === 0 ? `${fee.fromMinute / 60} h` : `${fee.fromMinute} min`}` : ''
    fees.push(`${formatPrice(fee.perMinute)} kr/min${after}`)
  }
  return fees
}

function relativeTime(iso: string): string {
  const minutes = Math.round((Date.now() - new Date(iso).getTime()) / 60000)
  if (minutes < 1) return 'just now'
  if (minutes < 60) return `${minutes} min ago`
  const hours = Math.round(minutes / 60)
  if (hours < 48) return `${hours} h ago`
  return `${Math.round(hours / 24)} days ago`
}
