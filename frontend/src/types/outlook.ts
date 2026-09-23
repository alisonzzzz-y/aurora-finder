import type { Location } from './location'

export type NightOutlook = {
  localDate: string
  utcOffsetAtStart: string
  level: 'INSUFFICIENT_DATA'
  reason: string
}

export type Outlook = {
  location: Location
  generatedAtUtc: string
  ruleStatus: 'NOT_VALIDATED'
  nights: NightOutlook[]
}
