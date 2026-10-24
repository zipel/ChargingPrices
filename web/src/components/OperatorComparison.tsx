import { useTranslation } from 'react-i18next'
import type { OperatorStats } from '../lib/operators'
import { colourOf, formatPrice } from '../lib/price'

interface Props {
  stats: OperatorStats[]
  thresholds: number[]
  selectedOperator: string
  onPickOperator: (operator: string) => void
  onClose: () => void
}

/** Below this, a median says little, so the operator is listed apart from the ranking. */
const MIN_STATIONS = 5

/** A table of operators ranked by their median price right now, with a bar for each. */
export function OperatorComparison({ stats, thresholds, selectedOperator, onPickOperator, onClose }: Props) {
  const { t } = useTranslation()
  const highest = Math.max(1, ...stats.map((s) => s.medianPrice))
  const ranked = stats.filter((s) => s.pricedSites >= MIN_STATIONS)
  const small = stats.filter((s) => s.pricedSites < MIN_STATIONS)

  const row = (s: OperatorStats) => (
    <tr key={s.operator} className={s.operator === selectedOperator ? 'active' : undefined}>
      <td>
        <button className="operator-button" onClick={() => onPickOperator(s.operator)}>
          {s.operator}
        </button>
        <span className="bar" style={{ width: `${(s.medianPrice / highest) * 100}%`, background: colourOf(s.medianPrice, thresholds) }} />
      </td>
      <td className="num strong">{formatPrice(s.medianPrice)}</td>
      <td className="num">{formatPrice(s.cheapestPrice)}</td>
      <td className="num muted">{s.pricedSites}</td>
    </tr>
  )

  return (
    <aside className="panel station compare" aria-label={t('compare.title')}>
      <button className="icon-button close" onClick={onClose} aria-label={t('compare.close')}>
        <svg viewBox="0 0 24 24" width="18" height="18" aria-hidden="true">
          <path d="M6 6l12 12M18 6 6 18" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
        </svg>
      </button>

      <header className="station-header">
        <h2>{t('compare.title')}</h2>
        <p className="muted small">{t('compare.intro')}</p>
      </header>

      {stats.length === 0 ? (
        <p className="muted">{t('compare.empty')}</p>
      ) : (
        <table className="compare-table">
          <thead>
            <tr>
              <th>{t('compare.operator')}</th>
              <th className="num">{t('compare.median')}</th>
              <th className="num">{t('compare.cheapest')}</th>
              <th className="num">{t('compare.stations')}</th>
            </tr>
          </thead>
          <tbody>{ranked.map(row)}</tbody>
          {small.length > 0 && (
            <tbody>
              <tr className="group-row">
                <td colSpan={4}>{t('compare.fewStations', { count: MIN_STATIONS })}</td>
              </tr>
              {small.map(row)}
            </tbody>
          )}
        </table>
      )}
    </aside>
  )
}
