import { toSimplifiedChinese } from '../components/aurora/simplifiedChinese.ts'

export type PageLanguage = 'en' | 'zh'
export type PlaceNames = { name: string; region: string; subregion: string; country: string }

const known = new Map<string, PlaceNames>()
const key = (id: number, language: PageLanguage) => `${id}:${language}`
const hasHan = (text: string) => /\p{Script=Han}/u.test(text)

/** The geocoder mixes Simplified and Traditional names, and the page shows Simplified Chinese. */
export function displayNames<T extends PlaceNames>(place: T, language: PageLanguage): T {
  if (language !== 'zh') return place
  return {
    ...place,
    name: toSimplifiedChinese(place.name),
    region: toSimplifiedChinese(place.region ?? ''),
    subregion: toSimplifiedChinese(place.subregion ?? ''),
    country: toSimplifiedChinese(place.country ?? ''),
  }
}

/** A name in the wrong script means the geocoder could not rename it, so asking by ID may still do better. */
export function rememberPlaceNames(id: number, language: PageLanguage, names: PlaceNames) {
  if (hasHan(names.name) === (language === 'zh')) {
    known.set(key(id, language), { name: names.name, region: names.region, subregion: names.subregion, country: names.country })
  }
}

export function storePlaceNames(id: number, language: PageLanguage, names: PlaceNames) {
  known.set(key(id, language), { name: names.name, region: names.region, subregion: names.subregion, country: names.country })
}

export function knownPlaceNames(id: number, language: PageLanguage) {
  return known.get(key(id, language))
}
