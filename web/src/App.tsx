import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { type Meta, type Place, type SiteDetail, type SiteSummary, fetchMeta, fetchSite, fetchSites } from './api'
import { FilterPanel } from './components/FilterPanel'
import { type MapFocus, MapView } from './components/MapView'
import { OperatorComparison } from './components/OperatorComparison'
import { StationPanel } from './components/StationPanel'
import { operatorStats } from './lib/operators'
import { bandThresholds } from './lib/price'
import { type Bounds, DEFAULT_FILTERS, type Filters, type MapSite, cheaperNearby, cheapestIn, toMapSites } from './lib/sites'

/** The back end refreshes the feeds every 2 minutes, so there's no point asking more often. */
const REFRESH_MS = 2 * 60 * 1000
const SMALL_SCREEN = '(max-width: 720px)'

/** The selected station lives in the URL (#site=...), so a link opens the same station. */
function siteFromUrl(): string | null {
  const match = window.location.hash.match(/site=([^&]+)/)
  return match ? decodeURIComponent(match[1]) : null
}

export default function App() {
  const { t } = useTranslation()
  const [sites, setSites] = useState<SiteSummary[]>([])
  const [meta, setMeta] = useState<Meta | null>(null)
  const [filters, setFilters] = useState<Filters>(DEFAULT_FILTERS)
  const [bounds, setBounds] = useState<Bounds | null>(null)
  const [selectedId, setSelectedId] = useState<string | null>(siteFromUrl)
  const [detail, setDetail] = useState<SiteDetail | null>(null)
  const [detailFailed, setDetailFailed] = useState(false)
  const [comparing, setComparing] = useState(false)
  const [focus, setFocus] = useState<MapFocus | null>(null)
  /** The technical reason the API can't be reached; shown inside a translated message */
  const [serverError, setServerError] = useState<string | null>(null)
  const [collapsed, setCollapsed] = useState(() => window.matchMedia(SMALL_SCREEN).matches)
  const [refreshCount, setRefreshCount] = useState(0)
  // A station opened from a link gets centred once, as soon as the sites have loaded
  const siteToCentre = useRef(siteFromUrl())

  // Load all sites and the meta data, then keep them fresh. Poll faster while the back end is still loading.
  const stillLoading = meta?.loading ?? true
  useEffect(() => {
    const controller = new AbortController()
    Promise.all([fetchSites(controller.signal), fetchMeta(controller.signal)])
      .then(([sitesResponse, metaResponse]) => {
        setSites(sitesResponse.sites)
        setMeta(metaResponse)
        setServerError(null)
        const linked = sitesResponse.sites.find((s) => s.id === siteToCentre.current)
        if (linked) {
          siteToCentre.current = null
          setFocus({ lon: linked.lon, lat: linked.lat, nonce: Date.now() })
        }
      })
      .catch((e: unknown) => {
        if (!controller.signal.aborted) setServerError(e instanceof Error ? e.message : 'unknown error')
      })
    const timer = window.setTimeout(() => setRefreshCount((n) => n + 1), stillLoading ? 5000 : REFRESH_MS)
    return () => {
      controller.abort()
      window.clearTimeout(timer)
    }
  }, [refreshCount, stillLoading])

  // Load the details of the selected station, and reload them on every refresh
  useEffect(() => {
    if (!selectedId) return
    const controller = new AbortController()
    fetchSite(selectedId, controller.signal)
      .then((d) => {
        setDetail(d)
        setDetailFailed(false)
      })
      .catch(() => {
        if (!controller.signal.aborted) setDetailFailed(true)
      })
    return () => controller.abort()
  }, [selectedId, refreshCount])

  // Keep the URL in step with the selection, without adding history entries
  useEffect(() => {
    const url = window.location.pathname + window.location.search + (selectedId ? `#site=${encodeURIComponent(selectedId)}` : '')
    window.history.replaceState(null, '', url)
  }, [selectedId])

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        setSelectedId(null)
        setDetail(null)
        setComparing(false)
      }
    }
    // A link to another station on the same page changes only the hash, without reloading
    const onHashChange = () => {
      const id = siteFromUrl()
      setSelectedId(id)
      setDetail(null)
      setDetailFailed(false)
      setComparing(false)
      siteToCentre.current = id
      setRefreshCount((n) => n + 1)
    }
    window.addEventListener('keydown', onKey)
    window.addEventListener('hashchange', onHashChange)
    return () => {
      window.removeEventListener('keydown', onKey)
      window.removeEventListener('hashchange', onHashChange)
    }
  }, [])

  const mapSites = useMemo(() => toMapSites(sites, filters), [sites, filters])
  const thresholds = useMemo(() => bandThresholds(mapSites.flatMap((s) => (s.price == null || s.price === 0 ? [] : [s.price]))), [mapSites])
  const cheapest = useMemo(() => (bounds ? cheapestIn(mapSites, bounds) : []), [mapSites, bounds])
  const nearby = useMemo(() => {
    if (!detail) return []
    // Compare at the speed the user filtered on, which is the price the map shows for this site
    const shown = mapSites.find((s) => s.site.id === detail.id)
    return cheaperNearby(mapSites, { id: detail.id, lat: detail.lat, lon: detail.lon, price: shown ? shown.price : detail.priceNow })
  }, [mapSites, detail])
  // The comparison ignores the operator filter, otherwise it would show one operator
  const stats = useMemo(() => (comparing ? operatorStats(toMapSites(sites, { ...filters, operator: '' })) : []), [comparing, sites, filters])

  const select = useCallback((id: string | null) => {
    setSelectedId(id)
    setDetail(null)
    setDetailFailed(false)
    if (id) setComparing(false)
  }, [])

  const pick = useCallback(
    (item: MapSite) => {
      select(item.site.id)
      setFocus({ lon: item.site.lon, lat: item.site.lat, nonce: Date.now() })
      if (window.matchMedia(SMALL_SCREEN).matches) setCollapsed(true)
    },
    [select],
  )

  const goToPlace = useCallback((place: Place) => {
    setFocus({ lon: place.lon, lat: place.lat, bbox: place.bbox, pin: true, nonce: Date.now() })
    if (window.matchMedia(SMALL_SCREEN).matches) setCollapsed(true)
  }, [])

  const openComparison = useCallback(() => {
    select(null)
    setComparing(true)
    if (window.matchMedia(SMALL_SCREEN).matches) setCollapsed(true)
  }, [select])

  const sidePanelOpen = selectedId != null || comparing

  return (
    <div className={sidePanelOpen ? 'app has-station' : 'app'}>
      <MapView sites={mapSites} thresholds={thresholds} selectedId={selectedId} focus={focus} onSelect={select} onBoundsChange={setBounds} />
      <FilterPanel
        filters={filters}
        onFiltersChange={setFilters}
        meta={meta}
        visibleCount={mapSites.length}
        thresholds={thresholds}
        cheapest={cheapest}
        selectedId={selectedId}
        onPick={pick}
        onPlace={goToPlace}
        onCompare={openComparison}
        collapsed={collapsed}
        onToggle={() => setCollapsed((c) => !c)}
      />
      {selectedId && (
        <StationPanel
          key={selectedId}
          detail={detail}
          loading={detail == null && !detailFailed}
          error={detailFailed ? t('app.stationError') : null}
          thresholds={thresholds}
          preferredSpeed={filters.speed === 'ALL' ? null : filters.speed}
          nearby={nearby}
          onPick={pick}
          onClose={() => select(null)}
        />
      )}
      {comparing && (
        <OperatorComparison
          stats={stats}
          thresholds={thresholds}
          selectedOperator={filters.operator}
          onPickOperator={(operator) => setFilters((f) => ({ ...f, operator: f.operator === operator ? '' : operator }))}
          onClose={() => setComparing(false)}
        />
      )}
      {serverError && (
        <div className="toast error" role="alert">
          {t('app.serverError', { message: serverError })}
        </div>
      )}
      {!serverError && stillLoading && sites.length === 0 && (
        <div className="toast" role="status">
          {t('app.loading')}
        </div>
      )}
    </div>
  )
}
