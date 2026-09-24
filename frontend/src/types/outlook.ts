import type { Location } from './location'

export type NightOutlook = {
  localDate: string
  utcOffsetAtStart: string
  evaluationWindowStartUtc: string
  evaluationWindowEndUtc: string
  level: 'HIGH' | 'MEDIUM' | 'LOW' | 'INSUFFICIENT_DATA'
  reason: string
}

export type Outlook = {
  location: Location
  generatedAtUtc: string
  ruleStatus: 'NOT_VALIDATED' | 'VALIDATED'
  nights: NightOutlook[]
}
