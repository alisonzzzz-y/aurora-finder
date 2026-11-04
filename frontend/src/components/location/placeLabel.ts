/** Some places, such as Longyearbyen and Hong Kong, have no country in the geocoder's answer. */
export function placeLabel(place: { name: string; country?: string | null }) {
  return place.country ? `${place.name}, ${place.country}` : place.name
}

/** Chinese readers get a name such as 中欧时间, since an ID such as Europe/Oslo is English either way. */
export function timezoneLabel(timezone: string, language: 'en' | 'zh') {
  if (language !== 'zh') return timezone
  try {
    return new Intl.DateTimeFormat('zh-CN', { timeZone: timezone, timeZoneName: 'longGeneric' })
      .formatToParts(new Date())
      .find(part => part.type === 'timeZoneName')?.value ?? timezone
  } catch {
    return timezone
  }
}
