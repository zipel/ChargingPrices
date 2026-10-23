// Types and calls for the back end's REST API (see backend/.../api/SiteViews.java)

export type SpeedClass = 'STANDARD' | 'FAST' | 'ULTRA'
export type Availability = 'AVAILABLE' | 'OCCUPIED' | 'OUT_OF_SERVICE' | 'UNKNOWN'

export interface SpeedGroup {
  speed: SpeedClass
  maxPowerKw: number | null
  priceNow: number | null
  available: number
  total: number
}

export interface SiteSummary {
  id: string
  name: string | null
  operator: string | null
  lat: number
  lon: number
  street: string | null
  city: string | null
  priceNow: number | null
  available: number
  total: number
  groups: SpeedGroup[]
}

export interface SitesResponse {
  generatedAt: string
  sites: SiteSummary[]
}

export interface ChargePointView {
  evseId: string | null
  currentType: 'AC' | 'DC' | 'UNKNOWN'
  speed: SpeedClass
  maxPowerKw: number | null
  connectors: { type: string | null; label: string; maxPowerKw: number | null }[]
  availability: Availability
  priceNow: number | null
  startFee: number | null
  minuteFees: { perMinute: number; fromMinute: number }[]
}

export interface PricePoint {
  start: string
  price: number
}

export interface PriceCurve {
  speed: SpeedClass
  points: PricePoint[]
}

export interface SiteDetail {
  id: string
  name: string | null
  operator: string | null
  lat: number
  lon: number
  street: string | null
  postcode: string | null
  city: string | null
  openAllHours: boolean
  source: string
  priceNow: number | null
  chargePoints: ChargePointView[]
  curves: PriceCurve[]
  statusUpdated: string | null
}

export interface Meta {
  generatedAt: string
  loading: boolean
  siteCount: number
  pricedSiteCount: number
  operators: { name: string; sites: number }[]
  sources: { name: string; url: string; licence: string; lastRefresh: string | null }[]
}

async function getJson<T>(path: string, signal?: AbortSignal): Promise<T> {
  const response = await fetch(path, { signal })
  if (!response.ok) {
    throw new Error(`${path} returned ${response.status}`)
  }
  return response.json() as Promise<T>
}

export const fetchSites = (signal?: AbortSignal) => getJson<SitesResponse>('/api/sites', signal)
export const fetchSite = (id: string, signal?: AbortSignal) => getJson<SiteDetail>(`/api/sites/${encodeURIComponent(id)}`, signal)
export const fetchMeta = (signal?: AbortSignal) => getJson<Meta>('/api/meta', signal)
