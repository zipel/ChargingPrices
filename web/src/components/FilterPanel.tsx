import type { Meta, Place } from '../api'
import { BAND_COLOURS, NO_PRICE_COLOUR, bandLabels, colourOf, formatPrice } from '../lib/price'
import { SPEED_HINTS, SPEED_LABELS, type Filters, type MapSite, type SpeedFilter, siteTitle } from '../lib/sites'
import { PlaceSearch } from './PlaceSearch'

const SPEEDS: SpeedFilter[] = ['ALL', 'STANDARD', 'FAST', 'ULTRA']

interface Props {
  filters: Filters
  onFiltersChange: (filters: Filters) => void
  meta: Meta | null
  visibleCount: number
  thresholds: number[]
  cheapest: MapSite[]
  selectedId: string | null
  onPick: (site: MapSite) => void
  onPlace: (place: Place) => void
  collapsed: boolean
  onToggle: () => void
}

export function FilterPanel({ filters, onFiltersChange, meta, visibleCount, thresholds, cheapest, selectedId, onPick, onPlace, collapsed, onToggle }: Props) {
  const set = (change: Partial<Filters>) => onFiltersChange({ ...filters, ...change })
  const lastRefresh = meta?.sources[0]?.lastRefresh

  return (
    <section className={collapsed ? 'panel filters collapsed' : 'panel filters'} aria-label="Filters">
      <header className="brand">
        <img src="/favicon.svg" alt="" width="32" height="32" />
        <div>
          <h1>ChargingPrices</h1>
          <p className="muted">Pay-as-you-go prices at public chargers in Denmark</p>
        </div>
        <button className="icon-button toggle" onClick={onToggle} aria-expanded={!collapsed} aria-label={collapsed ? 'Show filters' : 'Hide filters'}>
          <svg viewBox="0 0 24 24" width="18" height="18" aria-hidden="true">
            <path d={collapsed ? 'M6 9l6 6 6-6' : 'M6 15l6-6 6 6'} stroke="currentColor" strokeWidth="2" fill="none" strokeLinecap="round" />
          </svg>
        </button>
      </header>

      <PlaceSearch onPick={onPlace} />

      <div className="filters-body">
        <div className="segmented" role="radiogroup" aria-label="Charging speed">
          {SPEEDS.map((speed) => (
            <button
              key={speed}
              role="radio"
              aria-checked={filters.speed === speed}
              className={filters.speed === speed ? 'segment active' : 'segment'}
              title={speed === 'ALL' ? 'All charging speeds' : SPEED_HINTS[speed]}
              onClick={() => set({ speed })}
            >
              {speed === 'ALL' ? 'All' : SPEED_LABELS[speed]}
            </button>
          ))}
        </div>

        <label className="field">
          <span className="field-label">Operator</span>
          <select value={filters.operator} onChange={(e) => set({ operator: e.target.value })}>
            <option value="">All operators</option>
            {meta?.operators.map((op) => (
              <option key={op.name} value={op.name}>
                {op.name} ({op.sites})
              </option>
            ))}
          </select>
        </label>

        <div className="checks">
          <label className="check">
            <input type="checkbox" checked={filters.availableOnly} onChange={(e) => set({ availableOnly: e.target.checked })} />
            Free charger right now
          </label>
          <label className="check">
            <input type="checkbox" checked={filters.includeUnpriced} onChange={(e) => set({ includeUnpriced: e.target.checked })} />
            Include stations without a price
          </label>
          <label className="check" title="Sites whose name says they're private, such as housing associations or company car parks">
            <input type="checkbox" checked={filters.includeRestricted} onChange={(e) => set({ includeRestricted: e.target.checked })} />
            Include private chargers
          </label>
        </div>

        <div className="legend" aria-label="Price colours">
          <p className="field-label">kr/kWh right now, incl. VAT</p>
          <ul>
            {bandLabels(thresholds).map((label, band) => (
              <li key={label}>
                <span className="swatch" style={{ background: BAND_COLOURS[band] }} />
                {label}
              </li>
            ))}
            {filters.includeUnpriced && (
              <li>
                <span className="swatch" style={{ background: NO_PRICE_COLOUR }} />
                no price published
              </li>
            )}
          </ul>
          <p className="muted small">Circles with a number show the cheapest price in that area. Zoom in for each station.</p>
        </div>

        <div className="cheapest">
          <p className="field-label">Cheapest in this view</p>
          {cheapest.length === 0 && <p className="muted small">No stations with a price here. Try zooming out.</p>}
          <ol>
            {cheapest.map((item) => (
              <li key={item.site.id}>
                <button className={item.site.id === selectedId ? 'cheap-item active' : 'cheap-item'} onClick={() => onPick(item)}>
                  <span className="cheap-price" style={{ background: colourOf(item.price, thresholds) }}>
                    {formatPrice(item.price)}
                  </span>
                  <span className="cheap-text">
                    <span className="cheap-title">{siteTitle(item.site)}</span>
                    <span className="muted small">
                      {[item.site.operator, item.site.city].filter(Boolean).join(' · ')}
                      {item.maxPowerKw ? ` · ${Math.round(item.maxPowerKw)} kW` : ''}
                    </span>
                  </span>
                  <span className={item.available > 0 ? 'free yes' : 'free'}>
                    {item.available}/{item.total} free
                  </span>
                </button>
              </li>
            ))}
          </ol>
        </div>

        <footer className="data-note muted small">
          {meta?.loading ? 'Loading stations…' : `${visibleCount.toLocaleString('en-GB')} stations shown`}
          {lastRefresh && ` · updated ${new Date(lastRefresh).toLocaleTimeString('en-GB', { hour: '2-digit', minute: '2-digit' })}`}
          <br />
          Source:{' '}
          <a href="https://docs.spirii.com/developers/afir-compliance" target="_blank" rel="noreferrer">
            Spirii AFIR feed
          </a>{' '}
          (Spirii, EDF, PowerGo, Aura, Verdo and more). More operators coming.{' '}
          <a href="https://github.com/zipel/ChargingPrices" target="_blank" rel="noreferrer">
            About
          </a>
        </footer>
      </div>
    </section>
  )
}
