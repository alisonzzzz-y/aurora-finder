/** Some places, such as Longyearbyen and Hong Kong, have no country in the geocoder's answer. */
export function placeLabel(place: { name: string; country?: string | null }) {
  return place.country ? `${place.name}, ${place.country}` : place.name
}
