import type { TFunction } from 'i18next'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import type { Availability, ChargePointView, SiteDetail, SpeedClass } from '../api'
import { localeOf } from '../i18n'
import { formatDistance } from '../lib/geo'
import { colourOf, formatPrice } from '../lib/price'
import { type MapSite, type NearbySite, siteTitle } from '../lib/sites'
import { PriceChart } from './PriceChart'

interface Props {
  detail: SiteDetail | null
  loading: boolean
  error: string | null
  thresholds: number[]
  preferredSpeed: SpeedClass | null
  nearby: NearbySite[]
  onPick: (site: MapSite) => void
  onClose: () => void
}

export function StationPanel({ detail, loading, error, thresholds, preferredSpeed, nearby, onPick, onClose }: Props) {
  const { t, i18n } = useTranslation()
  // The tab the user clicked. App gives this panel a new key per station, which resets it.
  const [chosenSpeed, setSpeed] = useState<SpeedClass | null>(null)
  const speeds = detail?.curves.map((c) => c.speed) ?? []
  // Otherwise show the speed the user filtered on, or the first one the site has
  const defaultSpeed = preferredSpeed && speeds.includes(preferredSpeed) ? preferredSpeed : (speeds[0] ?? null)
  const speed = chosenSpeed && speeds.includes(chosenSpeed) ? chosenSpeed : defaultSpeed
  const locale = localeOf(i18n.language)

  return (
    <aside className="panel station" aria-label={t('station.label')}>
      <button className="icon-button close" onClick={onClose} aria-label={t('station.close')}>
        <svg viewBox="0 0 24 24" width="18" height="18" aria-hidden="true">
          <path d="M6 6l12 12M18 6 6 18" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
        </svg>
      </button>

      {!detail && loading && <p className="muted">{t('station.loading')}</p>}
      {!detail && error && <p className="error">{error}</p>}

      {detail && (
        <>
          <header className="station-header">
            <p className="eyebrow">{detail.operator ?? t('station.unknownOperator')}</p>
            <h2>{siteTitle(detail)}</h2>
            <p className="muted">{[detail.street, [detail.postcode, detail.city].filter(Boolean).join(' ')].filter(Boolean).join(', ')}</p>
            <div className="badges">
              {detail.restricted && <span className="badge warning">{t('station.looksPrivate')}</span>}
              {detail.openAllHours && <span className="badge">{t('station.open247')}</span>}
              <span className="badge">{t('station.chargePointCount', { count: detail.chargePoints.length })}</span>
            </div>
          </header>

          <section className="price-now">
            <span className="price-big" style={{ color: colourOf(detail.priceNow, thresholds) }}>
              {formatPrice(detail.priceNow)}
            </span>
            <span className="price-unit">
              {t('station.nowUnit')}
              <br />
              <span className="muted">{t('station.nowNote')}</span>
            </span>
          </section>

          {detail.curves.length > 0 && (
            <section>
              <div className="section-title">
                <h3>{t('station.next24')}</h3>
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
                        {t(`speed.${curve.speed}`)}
                      </button>
                    ))}
                  </div>
                )}
              </div>
              <PriceChart points={detail.curves.find((c) => c.speed === speed)?.points ?? []} thresholds={thresholds} />
            </section>
          )}

          <section>
            <h3>{detail.priceNow == null ? t('station.pricedNearby') : t('station.cheaperNearby')}</h3>
            {nearby.length === 0 ? (
              <p className="muted small">{detail.priceNow == null ? t('station.noPricedNearby') : t('station.nothingCheaper')}</p>
            ) : (
              <ol className="nearby">
                {nearby.map((item) => (
                  <li key={item.site.id}>
                    <button className="cheap-item" onClick={() => onPick(item)}>
                      <span className="cheap-price" style={{ background: colourOf(item.price, thresholds) }}>
                        {formatPrice(item.price)}
                      </span>
                      <span className="cheap-text">
                        <span className="cheap-title">{siteTitle(item.site)}</span>
                        <span className="muted small">{[item.site.operator, formatDistance(item.distanceKm, locale)].filter(Boolean).join(' · ')}</span>
                      </span>
                      <span className={item.available > 0 ? 'free yes' : 'free'}>
                        {t('cheapest.free', { available: item.available, total: item.total })}
                      </span>
                    </button>
                  </li>
                ))}
              </ol>
            )}
          </section>

          <section>
            <h3>{t('station.chargePoints')}</h3>
            <ul className="points">
              {groupPoints(detail.chargePoints, t, locale).map((group) => (
                <li key={group.key} className="point">
                  <div className="point-main">
                    <span className="point-title">
                      {group.connectors}
                      {group.maxPowerKw != null && <span className="muted"> · {Math.round(group.maxPowerKw)} kW</span>}
                    </span>
                    <span className="point-price">{group.priceNow == null ? t('station.noPrice') : t('station.priceKr', { price: formatPrice(group.priceNow) })}</span>
                  </div>
                  <div className="point-meta">
                    <StatusLine counts={group.counts} t={t} />
                    <span className="muted">
                      {t(`speed.${group.speed}`)} ({t(`speed.hint.${group.speed}`)})
                    </span>
                  </div>
                  {group.fees.length > 0 && <p className="fees">{group.fees.join(' · ')}</p>}
                </li>
              ))}
            </ul>
          </section>

          <section className="actions">
            <a className="button" href={`https://www.google.com/maps/dir/?api=1&destination=${detail.lat},${detail.lon}`} target="_blank" rel="noreferrer">
              {t('station.directions')}
            </a>
            {detail.statusUpdated && <span className="muted small">{t('station.lastStatus', { when: relativeTime(detail.statusUpdated, t) })}</span>}
          </section>

          <p className="footnote">{t('station.footnote')}</p>
        </>
      )}
    </aside>
  )
}

const AVAILABILITIES: Availability[] = ['AVAILABLE', 'OCCUPIED', 'OUT_OF_SERVICE', 'UNKNOWN']

function StatusLine({ counts, t }: { counts: Record<Availability, number>; t: TFunction }) {
  return (
    <span className="status">
      {AVAILABILITIES.filter((a) => counts[a] > 0).map((a) => (
        <span key={a} className="status-item">
          <span className={`dot dot-${a.toLowerCase()}`} aria-hidden="true" />
          {t(`status.${a}`, { count: counts[a] })}
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
function groupPoints(points: ChargePointView[], t: TFunction, locale: string): PointGroup[] {
  const groups = new Map<string, PointGroup>()
  for (const p of points) {
    const connectors = [...new Set(p.connectors.map((c) => c.label))].join(' / ') || t('station.connector')
    const fees = feesOf(p, t, locale)
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

function feesOf(point: ChargePointView, t: TFunction, locale: string): string[] {
  const fees: string[] = []
  if (point.startFee != null && point.startFee > 0) fees.push(t('fees.startFee', { price: formatPrice(point.startFee) }))
  for (const fee of point.minuteFees) {
    const duration = fee.fromMinute % 60 === 0 ? t('fees.hours', { count: fee.fromMinute / 60 }) : t('fees.minutes', { count: fee.fromMinute })
    const after = fee.fromMinute > 0 ? ` ${t('fees.after', { duration })}` : ''
    const when = [daysLabel(fee.days, t, locale), fee.from && fee.until ? `${fee.from}–${fee.until}` : ''].filter(Boolean).join(' ')
    fees.push(`${t('fees.perMinute', { price: formatPrice(fee.perMinute) })}${after}${when ? ` (${when})` : ''}`)
  }
  return fees
}

const DAY_ORDER = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY']

function daysLabel(days: string[], t: TFunction, locale: string): string {
  if (days.length === 0 || days.length === 7) return ''
  const same = (list: string[]) => list.length === days.length && list.every((d) => days.includes(d))
  if (same(DAY_ORDER.slice(0, 5))) return t('fees.monFri')
  if (same(DAY_ORDER.slice(5))) return t('fees.satSun')
  // 1 January 2024 was a Monday, so day i of the week is 1 + i January
  const format = new Intl.DateTimeFormat(locale, { weekday: 'short' })
  return days.map((d) => format.format(new Date(2024, 0, 1 + DAY_ORDER.indexOf(d)))).join(', ')
}

function relativeTime(iso: string, t: TFunction): string {
  const minutes = Math.round((Date.now() - new Date(iso).getTime()) / 60000)
  if (minutes < 1) return t('time.justNow')
  if (minutes < 60) return t('time.minutesAgo', { count: minutes })
  const hours = Math.round(minutes / 60)
  if (hours < 48) return t('time.hoursAgo', { count: hours })
  return t('time.daysAgo', { count: Math.round(hours / 24) })
}
