import type { Location } from './location'

export type NightOutlook = {
  localDate: string
  utcOffsetAtStart: string
  evaluationWindowStartUtc: string
  evaluationWindowEndUtc: string
  level: 'HIGH' | 'MEDIUM' | 'LOW' | 'INSUFFICIENT_DATA'
  reasonCode: 'RULES_NOT_VALIDATED'
  solarDarkness: {
    thresholds: {
      threshold: 'CIVIL_TWILIGHT' | 'NAUTICAL_TWILIGHT' | 'ASTRONOMICAL_TWILIGHT'
      solarElevationDegrees: number
      status: 'INTERVALS_FOUND' | 'NO_INTERVAL' | 'CONTINUOUS' | 'CALCULATION_FAILED'
      intervals: { startUtc: string; endUtc: string }[]
    }[]
  }
}

export type Outlook = {
  location: Location
  generatedAtUtc: string
  ruleStatus: 'NOT_VALIDATED' | 'VALIDATED'
  nights: NightOutlook[]
}
