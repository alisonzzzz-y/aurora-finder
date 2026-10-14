import type { Location } from '../types/location'
import { fetchJson } from './fetchJson'
import { filterNearby } from '../components/location/nearbyMath'

type City = [number, string, string, number, number, string]
let cityIndex: Location[] | undefined
export async function nearbyLocations(origin: Location, radius: number, signal: AbortSignal): Promise<Location[]> {
  if (!cityIndex) {
    const data = await fetchJson<{ cities: City[] }>('/data/nearby-cities.json', {signal}, 'Nearby place search is unavailable.', 15000)
    const countries = new Intl.DisplayNames(['en'], {type:'region'})
    cityIndex = data.cities.map(([id,name,countryCode,latitude,longitude,timezone])=>({
      id,name,country:countries.of(countryCode) ?? countryCode,latitude,longitude,timezone,region:'',subregion:'',
    }))
  }
  if (signal.aborted) throw new DOMException('Aborted','AbortError')
  return filterNearby(origin,cityIndex,radius)
}
