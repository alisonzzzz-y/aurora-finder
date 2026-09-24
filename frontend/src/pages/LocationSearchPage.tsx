import { LocationSearch } from '../components/location/LocationSearch'
import { AuroraMap } from '../components/aurora/AuroraMap'
import type { Location } from '../types/location'

type Props = { busy: boolean; onSelect: (location: Location) => void }

export function LocationSearchPage({ busy, onSelect }: Props) {
  return <>
    <section className="intro">
      <p className="eyebrow">PLAN A NIGHT OUTSIDE</p>
      <h1>A clearer view of the northern and southern lights.</h1>
      <p className="intro-copy">Follow the latest global aurora forecast, then choose a place from search results to explore its local nights. The map is a global overview and does not select locations. It shows a short-range model forecast, not a promise of what will be visible from the ground.</p>
    </section>
    <section className="home-dashboard-grid" aria-label="Aurora map and location search">
      <div className="map-column">
        <div className="map-heading"><div><p className="eyebrow">GLOBAL ACTIVITY</p><h2>NOAA OVATION forecast</h2></div><span>Short range · both hemispheres</span></div>
        <AuroraMap />
      </div>
      <aside className="map-sidebar">
        <LocationSearch busy={busy} onSelect={onSelect} />
        <article className="city-ranking-note">
          <p className="eyebrow">CITY OUTLOOKS</p>
          <h2>Local conditions, when the data is ready</h2>
          <p>City rankings need cloud cover and local darkness as well as aurora activity. Those sources and the viewing rules are still being checked, so this page does not invent low, medium, high, or percentage ratings.</p>
        </article>
      </aside>
    </section>
  </>
}
