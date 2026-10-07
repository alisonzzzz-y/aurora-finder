import type { LocalAuroraActivity } from './localAuroraActivity'
import type { Outlook } from './outlook'
import type { WeatherForecast } from './weatherForecast'

export type FactFetchStatus = 'CURRENT' | 'EXPIRED' | 'NO_COVERAGE' | 'PARTIAL' | 'UNAVAILABLE'
export type ProviderFailure = 'DISABLED' | 'INVALID_RESPONSE' | 'TIMEOUT' | 'RATE_LIMITED'
  | 'FORBIDDEN' | 'UPSTREAM_ERROR' | 'NETWORK_ERROR' | 'INTERRUPTED'

export type SourceFact<T> = {
  status: FactFetchStatus
  timeScope: 'SHORT_RANGE' | 'TONIGHT' | 'THREE_LOCAL_NIGHTS'
  retrievedAtUtc: string | null
  sourceObservedAtUtc: string | null
  sourceForecastAtUtc: string | null
  scopeStartUtc: string | null
  scopeEndUtc: string | null
  source: string
  sourceUrl: string
  failureCode: ProviderFailure | null
  data: T | null
}

export type ObservationFacts = {
  viewingConditions?: {
    level: 'HIGH' | 'MEDIUM' | 'LOW' | 'INSUFFICIENT_DATA'
    evaluatedAtUtc: string | null
    validUntilUtc: string | null
    modelSignal: number | null
    cloudPercent: number | null
    dark: boolean | null
    reasons: string[]
    ruleVersion: string
    probabilityCalibrated: boolean
  }
  runId?: string
  generatedAtUtc: string
  outlook: Outlook
  auroraActivity: SourceFact<LocalAuroraActivity>
  cloudForecast: SourceFact<WeatherForecast>
  solarDarkness: SourceFact<{ localDate: string; solarDarkness: NonNullable<Outlook['nights'][number]['solarDarkness']> }[]>
  coverage: {
    status: 'OVERLAPS' | 'NO_OVERLAP' | 'CANNOT_CHECK'
    firstScope: 'SHORT_RANGE' | 'TONIGHT'
    secondScope: 'SHORT_RANGE' | 'TONIGHT' | 'THREE_LOCAL_NIGHTS'
    shortRangeStartUtc: string | null
    shortRangeEndUtc: string | null
    cloudPointsWithValuesInsideShortRange: number
  }
  sourceStatus: FactFetchStatus
}
