import { useCallback, useEffect, useMemo, useState } from 'react'
import { type Meta, type SiteDetail, type SiteSummary, fetchMeta, fetchSite, fetchSites } from './api'
import { FilterPanel } from './components/FilterPanel'
import { MapView } from './components/MapView'
import { StationPanel } from './components/StationPanel'
import { bandThresholds } from './lib/price'
import { type Bounds, DEFAULT_FILTERS, type Filters, type MapSite, cheapestIn, toMapSites } from './lib/sites'

/** The back end refreshes the feeds every 2 minutes, so there's no point asking more often. */
const REFRESH_MS = 2 * 60 * 1000
const SMALL_SCREEN = '(max-width: 720px)'

export default function App() {
  const [sites, setSites] = useState<SiteSummary[]>([])
  const [meta, setMeta] = useState<Meta | null>(null)
  const [filters, setFilters] = useState<Filters>(DEFAULT_FILTERS)
  const [bounds, setBounds] = useState<Bounds | null>(null)
  const [selectedId, setSelectedId] = useState<string | null>(null)
  const [detail, setDetail] = useState<SiteDetail | null>(null)
  const [detailError, setDetailError] = useState<string | null>(null)
  const [focus, setFocus] = useState<{ lon: number; lat: number; nonce: number } | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [collapsed, setCollapsed] = useState(() => window.matchMedia(SMALL_SCREEN).matches)
  const [refreshCount, setRefreshCount] = useState(0)

  // Load all sites and the meta data, then keep them fresh. Poll faster while the back end is still loading.
  const stillLoading = meta?.loading ?? true
  useEffect(() => {
    const controller = new AbortController()
    Promise.all([fetchSites(controller.signal), fetchMeta(controller.signal)])
      .then(([sitesResponse, metaResponse]) => {
        setSites(sitesResponse.sites)
        setMeta(metaResponse)
        setError(null)
      })
      .catch((e: unknown) => {
        if (!controller.signal.aborted) setError(`Can’t reach the server (${e instanceof Error ? e.message : 'unknown error'}). Is the back end running?`)
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
        setDetailError(null)
      })
      .catch((e: unknown) => {
        if (!controller.signal.aborted) setDetailError(e instanceof Error ? e.message : 'Could not load the station')
      })
    return () => controller.abort()
  }, [selectedId, refreshCount])

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setSelectedId(null)
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [])

  const mapSites = useMemo(() => toMapSites(sites, filters), [sites, filters])
  const thresholds = useMemo(() => bandThresholds(mapSites.flatMap((s) => (s.price == null || s.price === 0 ? [] : [s.price]))), [mapSites])
  const cheapest = useMemo(() => (bounds ? cheapestIn(mapSites, bounds) : []), [mapSites, bounds])

  const select = useCallback((id: string | null) => {
    setSelectedId(id)
    setDetail(null)
    setDetailError(null)
  }, [])

  const pick = useCallback(
    (item: MapSite) => {
      select(item.site.id)
      setFocus({ lon: item.site.lon, lat: item.site.lat, nonce: Date.now() })
      if (window.matchMedia(SMALL_SCREEN).matches) setCollapsed(true)
    },
    [select],
  )

  return (
    <div className={selectedId ? 'app has-station' : 'app'}>
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
        collapsed={collapsed}
        onToggle={() => setCollapsed((c) => !c)}
      />
      {selectedId && (
        <StationPanel
          key={selectedId}
          detail={detail}
          loading={detail == null && detailError == null}
          error={detailError}
          thresholds={thresholds}
          preferredSpeed={filters.speed === 'ALL' ? null : filters.speed}
          onClose={() => select(null)}
        />
      )}
      {error && (
        <div className="toast error" role="alert">
          {error}
        </div>
      )}
      {!error && stillLoading && sites.length === 0 && (
        <div className="toast" role="status">
          Loading stations from the operators’ feeds…
        </div>
      )}
    </div>
  )
}
