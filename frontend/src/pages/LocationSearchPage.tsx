import { LocationSearch } from '../components/location/LocationSearch'
import type { Location } from '../types/location'

type Props = { busy: boolean; onSelect: (location: Location) => void }

export function LocationSearchPage({ busy, onSelect }: Props) {
  return <>
    <section className="intro">
      <p className="eyebrow">PLAN A NIGHT OUTSIDE</p>
      <h1>A clearer view of the northern and southern lights.</h1>
      <p className="intro-copy">Choose a place to see its next three local nights. Forecast data and viewing rules are still being validated, so this version does not rate the chances of seeing an aurora.</p>
    </section>
    <LocationSearch busy={busy} onSelect={onSelect} />
  </>
}
