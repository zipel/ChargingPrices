import type { FeatureCollection, Point } from 'geojson'
import * as maplibregl from 'maplibre-gl'
import type { ExpressionSpecification, GeoJSONSource, MapGeoJSONFeature } from 'maplibre-gl'
import 'maplibre-gl/dist/maplibre-gl.css'
// MapLibre parses map tiles in a web worker. Importing the worker file with ?url makes Vite copy it into
// the build and gives its address, which works the same in development and production.
import workerUrl from 'maplibre-gl/dist/maplibre-gl-worker.mjs?url'
import { useEffect, useRef } from 'react'
import type { Bounds, MapSite } from '../lib/sites'
import { BAND_COLOURS, NO_PRICE_COLOUR, bandOf, formatPrice } from '../lib/price'

// Free vector tiles based on OpenStreetMap, no API key needed (see docs/decisions/0004-map-tiles.md)
const MAP_STYLE = 'https://tiles.openfreemap.org/styles/positron'
maplibregl.setWorkerUrl(workerUrl)

const DENMARK_BOUNDS: [[number, number], [number, number]] = [[7.9, 54.5], [15.3, 57.8]]
const SELECTED_COLOUR = '#0f172a'
/** Above this zoom, sites show as price labels instead of dots. */
const LABEL_ZOOM = 11
/** Marks "no usable price" in cluster aggregates, which can't hold null. */
const NO_PRICE = 999

interface Props {
  sites: MapSite[]
  thresholds: number[]
  selectedId: string | null
  focus: { lon: number; lat: number; nonce: number } | null
  onSelect: (id: string | null) => void
  onBoundsChange: (bounds: Bounds) => void
}

export function MapView({ sites, thresholds, selectedId, focus, onSelect, onBoundsChange }: Props) {
  const container = useRef<HTMLDivElement>(null)
  const mapRef = useRef<maplibregl.Map | null>(null)
  const loaded = useRef(false)
  // Callbacks and data change on every render; the map's event handlers read the latest through refs
  const handlers = useRef({ onSelect, onBoundsChange })
  const latest = useRef({ sites, thresholds, selectedId })
  useEffect(() => {
    handlers.current = { onSelect, onBoundsChange }
    latest.current = { sites, thresholds, selectedId }
  })

  useEffect(() => {
    if (!container.current) return
    const map = new maplibregl.Map({
      container: container.current,
      style: MAP_STYLE,
      bounds: DENMARK_BOUNDS,
      // On wide screens the filter panel covers the left side, so keep Denmark clear of it
      fitBoundsOptions: { padding: window.innerWidth > 720 ? { top: 32, bottom: 32, left: 380, right: 32 } : 16 },
      attributionControl: false,
    })
    mapRef.current = map
    map.addControl(new maplibregl.AttributionControl({ compact: true, customAttribution: 'Prices: operators’ AFIR feeds via Spirii' }), 'bottom-right')
    map.addControl(new maplibregl.NavigationControl({ showCompass: false }), 'bottom-right')
    map.addControl(new maplibregl.GeolocateControl({ positionOptions: { enableHighAccuracy: true }, fitBoundsOptions: { maxZoom: 13 } }), 'bottom-right')

    map.on('load', () => {
      addPillImages(map)
      map.addSource('sites', {
        type: 'geojson',
        data: toGeoJson([], [], null),
        cluster: true,
        clusterMaxZoom: 9,
        clusterRadius: 44,
        clusterProperties: { minPrice: ['min', ['get', 'rankPrice']] },
      })

      map.addLayer({
        id: 'clusters',
        type: 'circle',
        source: 'sites',
        filter: ['has', 'point_count'],
        paint: {
          'circle-color': clusterColour(latest.current.thresholds),
          'circle-radius': ['step', ['get', 'point_count'], 17, 25, 20, 100, 24],
          'circle-stroke-width': 2,
          'circle-stroke-color': '#ffffff',
        },
      })
      map.addLayer({
        id: 'cluster-labels',
        type: 'symbol',
        source: 'sites',
        filter: ['has', 'point_count'],
        layout: {
          'text-field': ['case', ['<', ['get', 'minPrice'], NO_PRICE], ['number-format', ['get', 'minPrice'], { 'min-fraction-digits': 2, 'max-fraction-digits': 2 }], '–'],
          'text-font': ['Noto Sans Bold'],
          'text-size': 12,
          'text-allow-overlap': true,
        },
        paint: { 'text-color': '#ffffff' },
      })
      map.addLayer({
        id: 'site-dots',
        type: 'circle',
        source: 'sites',
        filter: ['!', ['has', 'point_count']],
        paint: {
          'circle-color': bandColour(),
          'circle-radius': ['interpolate', ['linear'], ['zoom'], 6, 4, 11, 7, 15, 9],
          'circle-stroke-width': ['case', ['get', 'selected'], 3, 1.5],
          'circle-stroke-color': ['case', ['get', 'selected'], SELECTED_COLOUR, '#ffffff'],
        },
      })
      map.addLayer({
        id: 'site-pills',
        type: 'symbol',
        source: 'sites',
        minzoom: LABEL_ZOOM,
        filter: ['!', ['has', 'point_count']],
        layout: {
          'icon-image': ['case', ['get', 'selected'], 'pill-selected', ['concat', 'pill-', ['to-string', ['get', 'band']]]],
          'icon-text-fit': 'both',
          'icon-text-fit-padding': [3, 7, 3, 7],
          'text-field': ['get', 'label'],
          'text-font': ['Noto Sans Bold'],
          'text-size': 12,
          // Cheaper sites win when labels would overlap; the dots underneath still show the rest
          'symbol-sort-key': ['get', 'rankPrice'],
          'symbol-z-order': 'source',
        },
        paint: { 'text-color': '#ffffff' },
      })

      for (const layer of ['site-dots', 'site-pills']) {
        map.on('click', layer, (event) => {
          const id = featureId(event.features)
          if (id) handlers.current.onSelect(id)
        })
      }
      map.on('click', 'clusters', async (event) => {
        const feature = event.features?.[0]
        if (!feature || feature.geometry.type !== 'Point') return
        const source = map.getSource('sites') as GeoJSONSource
        const zoom = await source.getClusterExpansionZoom(feature.properties.cluster_id as number)
        map.easeTo({ center: feature.geometry.coordinates as [number, number], zoom: zoom + 0.5 })
      })
      map.on('click', (event) => {
        const hits = map.queryRenderedFeatures(event.point, { layers: ['clusters', 'site-dots', 'site-pills'] })
        if (hits.length === 0) handlers.current.onSelect(null)
      })
      for (const layer of ['clusters', 'site-dots', 'site-pills']) {
        map.on('mouseenter', layer, () => (map.getCanvas().style.cursor = 'pointer'))
        map.on('mouseleave', layer, () => (map.getCanvas().style.cursor = ''))
      }
      map.on('moveend', () => handlers.current.onBoundsChange(boundsOf(map)))

      loaded.current = true
      const { sites, thresholds, selectedId } = latest.current
      ;(map.getSource('sites') as GeoJSONSource).setData(toGeoJson(sites, thresholds, selectedId))
      handlers.current.onBoundsChange(boundsOf(map))
    })

    return () => {
      loaded.current = false
      map.remove()
      mapRef.current = null
    }
  }, [])

  // New data, filters or selection: redraw the sites
  useEffect(() => {
    const map = mapRef.current
    if (!map || !loaded.current) return
    ;(map.getSource('sites') as GeoJSONSource).setData(toGeoJson(sites, thresholds, selectedId))
    map.setPaintProperty('clusters', 'circle-color', clusterColour(thresholds))
  }, [sites, thresholds, selectedId])

  // Centre on a site picked from the list
  useEffect(() => {
    const map = mapRef.current
    if (!map || !focus) return
    map.easeTo({ center: [focus.lon, focus.lat], zoom: Math.max(map.getZoom(), 13.5), duration: 800 })
  }, [focus])

  return <div ref={container} className="map" aria-label="Map of charging stations" />
}

function toGeoJson(sites: MapSite[], thresholds: number[], selectedId: string | null): FeatureCollection<Point> {
  return {
    type: 'FeatureCollection',
    features: sites.map(({ site, price }) => {
      // A price of 0 is shown but greyed out and never ranked as cheapest: it's mostly company chargers
      const usable = price != null && price > 0
      return {
        type: 'Feature',
        geometry: { type: 'Point', coordinates: [site.lon, site.lat] },
        properties: {
          id: site.id,
          label: formatPrice(price),
          rankPrice: usable ? price : NO_PRICE,
          band: usable ? bandOf(price, thresholds) : 'none',
          selected: site.id === selectedId,
        },
      }
    }),
  }
}

function bandColour(): ExpressionSpecification {
  return ['match', ['to-string', ['get', 'band']], '0', BAND_COLOURS[0], '1', BAND_COLOURS[1], '2', BAND_COLOURS[2], '3', BAND_COLOURS[3], '4', BAND_COLOURS[4], NO_PRICE_COLOUR]
}

/** Clusters take the colour of their cheapest site. */
function clusterColour(thresholds: number[]): ExpressionSpecification {
  return [
    'case',
    ['>=', ['get', 'minPrice'], NO_PRICE],
    NO_PRICE_COLOUR,
    ['step', ['get', 'minPrice'], BAND_COLOURS[0], thresholds[0], BAND_COLOURS[1], thresholds[1], BAND_COLOURS[2], thresholds[2], BAND_COLOURS[3], thresholds[3], BAND_COLOURS[4]],
  ]
}

function featureId(features: MapGeoJSONFeature[] | undefined): string | null {
  const id = features?.[0]?.properties?.id
  return typeof id === 'string' ? id : null
}

function boundsOf(map: maplibregl.Map): Bounds {
  const b = map.getBounds()
  return { west: b.getWest(), south: b.getSouth(), east: b.getEast(), north: b.getNorth() }
}

/** Rounded "pill" backgrounds for the price labels, one per colour. MapLibre stretches them to fit the text. */
function addPillImages(map: maplibregl.Map) {
  const colours: Record<string, string> = { none: NO_PRICE_COLOUR, selected: SELECTED_COLOUR }
  BAND_COLOURS.forEach((colour, band) => (colours[String(band)] = colour))

  const ratio = 2
  const size = 24 * ratio
  const radius = 10 * ratio
  for (const [name, colour] of Object.entries(colours)) {
    const canvas = document.createElement('canvas')
    canvas.width = size
    canvas.height = size
    const ctx = canvas.getContext('2d')
    if (!ctx) continue
    ctx.fillStyle = colour
    ctx.strokeStyle = '#ffffff'
    ctx.lineWidth = 2 * ratio
    ctx.beginPath()
    ctx.roundRect(ratio, ratio, size - 2 * ratio, size - 2 * ratio, radius)
    ctx.fill()
    ctx.stroke()
    map.addImage(`pill-${name}`, ctx.getImageData(0, 0, size, size), {
      pixelRatio: ratio,
      stretchX: [[radius, size - radius]],
      stretchY: [[radius, size - radius]],
      content: [radius / 2, radius / 2, size - radius / 2, size - radius / 2],
    })
  }
}
