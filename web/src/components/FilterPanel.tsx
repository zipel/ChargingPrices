import { useTranslation } from 'react-i18next'
import type { Meta, Place } from '../api'
import { changeLanguage, localeOf, type Language } from '../i18n'
import { BAND_COLOURS, NO_PRICE_COLOUR, bandRanges, colourOf, formatPrice } from '../lib/price'
import { type Filters, type MapSite, type SpeedFilter, siteTitle } from '../lib/sites'
import { PlaceSearch } from './PlaceSearch'

const SPEEDS: SpeedFilter[] = ['ALL', 'STANDARD', 'FAST', 'ULTRA']
const LANGUAGES: Language[] = ['en', 'da']

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
  onCompare: () => void
  collapsed: boolean
  onToggle: () => void
}

export function FilterPanel(props: Props) {
  const { filters, onFiltersChange, meta, visibleCount, thresholds, cheapest, selectedId, onPick, onPlace, onCompare, collapsed, onToggle } = props
  const { t, i18n } = useTranslation()
  const set = (change: Partial<Filters>) => onFiltersChange({ ...filters, ...change })
  const lastRefresh = meta?.sources[0]?.lastRefresh
  const locale = localeOf(i18n.language)

  return (
    <section className={collapsed ? 'panel filters collapsed' : 'panel filters'} aria-label={t('brand.showFilters')}>
      <header className="brand">
        <img src="/favicon.svg" alt="" width="32" height="32" />
        <div>
          <h1>ChargingPrices</h1>
          <p className="muted">{t('brand.tagline')}</p>
        </div>
        <div className="brand-actions">
          <div className="language" role="group" aria-label={t('language.label')}>
            {LANGUAGES.map((language) => (
              <button
                key={language}
                className={i18n.language === language ? 'lang active' : 'lang'}
                aria-pressed={i18n.language === language}
                onClick={() => changeLanguage(language)}
              >
                {t(`language.${language}`)}
              </button>
            ))}
          </div>
          <button
            className="icon-button toggle"
            onClick={onToggle}
            aria-expanded={!collapsed}
            aria-label={collapsed ? t('brand.showFilters') : t('brand.hideFilters')}
          >
            <svg viewBox="0 0 24 24" width="18" height="18" aria-hidden="true">
              <path d={collapsed ? 'M6 9l6 6 6-6' : 'M6 15l6-6 6 6'} stroke="currentColor" strokeWidth="2" fill="none" strokeLinecap="round" />
            </svg>
          </button>
        </div>
      </header>

      <PlaceSearch onPick={onPlace} />

      <div className="filters-body">
        <div className="segmented" role="radiogroup" aria-label={t('speed.label')}>
          {SPEEDS.map((speed) => (
            <button
              key={speed}
              role="radio"
              aria-checked={filters.speed === speed}
              className={filters.speed === speed ? 'segment active' : 'segment'}
              title={t(`speed.hint.${speed}`)}
              onClick={() => set({ speed })}
            >
              {t(`speed.${speed}`)}
            </button>
          ))}
        </div>

        <label className="field">
          <span className="field-label">{t('filters.operator')}</span>
          <select value={filters.operator} onChange={(e) => set({ operator: e.target.value })}>
            <option value="">{t('filters.allOperators')}</option>
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
            {t('filters.availableOnly')}
          </label>
          <label className="check">
            <input type="checkbox" checked={filters.includeUnpriced} onChange={(e) => set({ includeUnpriced: e.target.checked })} />
            {t('filters.includeUnpriced')}
          </label>
          <label className="check" title={t('filters.includeRestrictedHint')}>
            <input type="checkbox" checked={filters.includeRestricted} onChange={(e) => set({ includeRestricted: e.target.checked })} />
            {t('filters.includeRestricted')}
          </label>
        </div>

        <div className="legend">
          <p className="field-label">{t('legend.title')}</p>
          <ul>
            {bandRanges(thresholds).map(({ from, to }, band) => (
              <li key={band}>
                <span className="swatch" style={{ background: BAND_COLOURS[band] }} />
                {from == null
                  ? t('legend.under', { price: formatPrice(thresholds[0]) })
                  : to == null
                    ? t('legend.andUp', { price: formatPrice(from) })
                    : t('legend.range', { from: formatPrice(from), to: formatPrice(to) })}
              </li>
            ))}
            {filters.includeUnpriced && (
              <li>
                <span className="swatch" style={{ background: NO_PRICE_COLOUR }} />
                {t('legend.noPrice')}
              </li>
            )}
          </ul>
          <p className="muted small">{t('legend.note')}</p>
        </div>

        <div className="cheapest">
          <p className="field-label">{t('cheapest.title')}</p>
          {cheapest.length === 0 && <p className="muted small">{t('cheapest.none')}</p>}
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
                    {t('cheapest.free', { available: item.available, total: item.total })}
                  </span>
                </button>
              </li>
            ))}
          </ol>
          <button className="link-button compare-link" onClick={onCompare}>
            {t('footer.compare')} →
          </button>
        </div>

        <footer className="data-note muted small">
          {meta?.loading ? t('footer.loading') : t('footer.shown', { count: visibleCount })}
          {lastRefresh && ` · ${t('footer.updated', { time: new Date(lastRefresh).toLocaleTimeString(locale, { hour: '2-digit', minute: '2-digit' }) })}`}
          <br />
          {t('footer.source')}{' '}
          <a href="https://docs.spirii.com/developers/afir-compliance" target="_blank" rel="noreferrer">
            {t('footer.sourceLink')}
          </a>{' '}
          {t('footer.operators')}{' '}
          <a href="https://github.com/zipel/ChargingPrices" target="_blank" rel="noreferrer">
            {t('footer.about')}
          </a>
        </footer>
      </div>
    </section>
  )
}
