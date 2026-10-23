import { type FormEvent, useState } from 'react'
import { type Place, fetchPlaces } from '../api'

interface Props {
  onPick: (place: Place) => void
}

/** Searches on Enter only, not on every keystroke: the geocoder behind it allows one request per second. */
export function PlaceSearch({ onPick }: Props) {
  const [query, setQuery] = useState('')
  const [results, setResults] = useState<Place[] | null>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function submit(event: FormEvent) {
    event.preventDefault()
    if (query.trim().length < 2) return
    setBusy(true)
    setError(null)
    try {
      setResults(await fetchPlaces(query.trim()))
    } catch {
      setError('Search isn’t working right now. Try again in a moment.')
      setResults(null)
    } finally {
      setBusy(false)
    }
  }

  function pick(place: Place) {
    onPick(place)
    setResults(null)
    setQuery(place.name)
  }

  return (
    <div className="search">
      <form role="search" onSubmit={submit}>
        <input
          type="search"
          value={query}
          onChange={(e) => {
            setQuery(e.target.value)
            if (e.target.value === '') setResults(null)
          }}
          placeholder="Search a town or address"
          aria-label="Search a town or address in Denmark"
        />
        <button type="submit" className="search-button" aria-label="Search" disabled={busy}>
          <svg viewBox="0 0 24 24" width="18" height="18" aria-hidden="true">
            <circle cx="11" cy="11" r="6.5" stroke="currentColor" strokeWidth="2" fill="none" />
            <path d="m16 16 4.5 4.5" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
          </svg>
        </button>
      </form>
      {error && <p className="error small">{error}</p>}
      {results && (
        <ul className="search-results">
          {results.length === 0 && <li className="muted small empty">No places found in Denmark.</li>}
          {results.map((place) => (
            <li key={`${place.lat},${place.lon},${place.name}`}>
              <button onClick={() => pick(place)}>
                <span className="cheap-title">{place.name}</span>
                {place.detail && <span className="muted small">{place.detail}</span>}
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
