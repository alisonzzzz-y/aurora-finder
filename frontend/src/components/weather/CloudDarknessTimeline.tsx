import { useEffect, useRef, useState } from 'react'
import type { NightOutlook } from '../../types/outlook'
import type { WeatherCloudPoint } from '../../types/weatherForecast'
import type { SourceFact } from '../../types/observationFacts'
import type { LocalAuroraActivity } from '../../types/localAuroraActivity'
import { sortByValidAt } from '../../utils/sortByValidAt'
import { useI18n } from '../../i18n'
import './CloudDarknessTimeline.css'

type Props = {
  points: WeatherCloudPoint[]
  night: NightOutlook
  timezone: string
  highlightedPointTimes?: string[]
  auroraFact?: SourceFact<LocalAuroraActivity>
}

const thresholdOrder = ['CIVIL_TWILIGHT', 'NAUTICAL_TWILIGHT', 'ASTRONOMICAL_TWILIGHT'] as const

export function CloudDarknessTimeline({ points, night, timezone, highlightedPointTimes = [], auroraFact }: Props) {
  const { language, t } = useI18n()
  const chartRef = useRef<HTMLDivElement>(null)
  const [width, setWidth] = useState(380)
  const [selectedPointAt, setSelectedPointAt] = useState<string | null>(null)
  const locale = language === 'zh' ? 'zh-CN' : 'en'
  const start = Date.parse(night.evaluationWindowStartUtc)
  const end = Date.parse(night.evaluationWindowEndUtc)
  const timelinePoints = sortByValidAt(points.filter(point => {
    const instant = Date.parse(point.validAt)
    return Number.isFinite(instant) && instant >= start && instant <= end
  }))
  const validPoints = timelinePoints.filter(point => point.cloudCoverPercent !== null)
  const highlightedPointSet = new Set(highlightedPointTimes)
  const hasHighlightedPoints = validPoints.some(point => highlightedPointSet.has(point.validAt))
  const selectedPoint = validPoints.find(point => point.validAt === selectedPointAt)
  const left = 110
  const right = 10
  const top = 25
  const cloudBottom = 164
  const height = 344
  const xFor = (instant: number) => left + (instant - start) / Math.max(1, end - start) * (width - left - right)
  const yFor = (cloud: number) => top + (100 - cloud) / 100 * (cloudBottom - top)
  const timeLabel = (instant: number) => new Intl.DateTimeFormat(locale, {
    hour: '2-digit', minute: '2-digit', timeZone: timezone,
  }).format(new Date(instant))
  const xTicks = [0, 0.25, 0.5, 0.75, 1].map(ratio => start + (end - start) * ratio)
  const thresholds = thresholdOrder.map(key => night.solarDarkness.thresholds.find(item => item.threshold === key))
  const aurora = auroraFact?.status === 'CURRENT' && auroraFact.data?.status === 'CURRENT'
    && auroraFact.data.modelValue !== null && Number.isFinite(auroraFact.data.modelValue)
    && auroraFact.data.level !== 'INSUFFICIENT_DATA'
    && auroraFact.scopeStartUtc && auroraFact.scopeEndUtc
    ? {
        value: auroraFact.data.modelValue,
        start: Math.max(start, Date.parse(auroraFact.scopeStartUtc)),
        end: Math.min(end, Date.parse(auroraFact.scopeEndUtc)),
        forecastAt: Date.parse(auroraFact.sourceForecastAtUtc ?? auroraFact.data.forecastTime),
      }
    : null
  const hasAuroraWindow = Boolean(aurora && Number.isFinite(aurora.start) && Number.isFinite(aurora.end)
    && aurora.end > aurora.start)

  useEffect(() => {
    const element = chartRef.current
    if (!element) return
    const observer = new ResizeObserver(entries => {
      const nextWidth = entries[0]?.contentRect.width
      if (nextWidth) setWidth(nextWidth)
    })
    observer.observe(element)
    return () => observer.disconnect()
  }, [])

  const description = `${t('cloudDarknessTimelineDescription')} ${hasAuroraWindow ? t('auroraTimelineDescription') : ''}`

  return <section className="cloud-darkness-timeline" aria-labelledby="cloud-darkness-timeline-title">
    <div className="cloud-darkness-title-row">
      <h3 id="cloud-darkness-timeline-title">{t('cloudDarknessTimelineTitle')}</h3>
      <span>{t('localTimeNote')}</span>
    </div>
    <div className="cloud-darkness-legend" aria-hidden="true">
      <span><i className="cloud-line-key" />{t('cloudCoverLegend')}</span>
      <span><i className="darkness-band-key" />{t('sunBelowHorizonLegend')}</span>
      {hasHighlightedPoints && <span><i className="cloud-darkness-overlap-key" />{t('auroraCloudOverlapPoint')}</span>}
      {hasAuroraWindow && <span><i className="aurora-window-key" />{t('localAuroraForecast')}</span>}
    </div>
    <div ref={chartRef} className="cloud-darkness-chart-wrap">
      <svg className="cloud-darkness-chart" viewBox={`0 0 ${width} ${height}`} role="group" aria-label={`${t('cloudDarknessTimelineTitle')}. ${description}`}>
        <title>{t('cloudDarknessTimelineTitle')}</title>
        <desc>{description}</desc>
        {[0, 25, 50, 75, 100].map(value => <g key={value}>
          <line className="cloud-darkness-gridline" x1={left} x2={width - right} y1={yFor(value)} y2={yFor(value)} />
          <text className="cloud-darkness-axis-label" x={left - 8} y={yFor(value) + 3} textAnchor="end">{value}%</text>
        </g>)}
        {xTicks.map((instant, index) => <g key={index}>
          <line className="cloud-darkness-gridline vertical" x1={xFor(instant)} x2={xFor(instant)} y1={top} y2={cloudBottom} />
          <text className="cloud-darkness-axis-label" x={xFor(instant)} y={cloudBottom + 17} textAnchor={index === 0 ? 'start' : index === xTicks.length - 1 ? 'end' : 'middle'}>{timeLabel(instant)}</text>
        </g>)}
        {timelinePoints.slice(0, -1).map((point, index) => {
          const next = timelinePoints[index + 1]
          if (point.cloudCoverPercent === null || next.cloudCoverPercent === null
            || Date.parse(next.validAt) - Date.parse(point.validAt) > 4 * 60 * 60 * 1000) return null
          return <path className="cloud-darkness-line" key={`${point.validAt}-${next.validAt}`}
            d={`M ${xFor(Date.parse(point.validAt))} ${yFor(point.cloudCoverPercent!)} L ${xFor(Date.parse(next.validAt))} ${yFor(next.cloudCoverPercent!)}`} />
        })}
        {validPoints.map(point => {
          const instant = Date.parse(point.validAt)
          const overlapsAurora = highlightedPointSet.has(point.validAt)
          const label = `${timeLabel(instant)} · ${point.cloudCoverPercent}% ${t('cloudCoverLegend')}${overlapsAurora ? ` · ${t('auroraCloudOverlapPoint')}` : ''}`
          return <circle className={overlapsAurora ? 'cloud-darkness-point cloud-darkness-point-overlap' : 'cloud-darkness-point'}
            key={point.validAt} cx={xFor(instant)} cy={yFor(point.cloudCoverPercent!)} r={overlapsAurora ? 7 : 5} role="button" tabIndex={0}
            aria-label={label} aria-pressed={selectedPointAt === point.validAt}
            onClick={() => setSelectedPointAt(point.validAt)}
            onKeyDown={event => {
              if (event.key === 'Enter' || event.key === ' ') {
                event.preventDefault()
                setSelectedPointAt(point.validAt)
              }
            }}>
            <title>{label}</title>
          </circle>
        })}
        {hasAuroraWindow && aurora && <g aria-label={`${t('localAuroraForecast')}: ${aurora.value}/100`}>
          <text className="cloud-darkness-row-heading" x="0" y="202">{t('localAuroraForecast')}</text>
          <line className="cloud-darkness-track aurora-window-track" x1={left} x2={width - right} y1="198" y2="198" />
          <rect className="aurora-window-band" x={xFor(aurora.start)} y="192"
            width={Math.max(3, xFor(aurora.end) - xFor(aurora.start))} height="12" rx="5">
            <title>{`${t('localAuroraForecast')}: ${timeLabel(aurora.start)}–${timeLabel(aurora.end)} · ${aurora.value}/100`}</title>
          </rect>
          {aurora.forecastAt >= aurora.start && aurora.forecastAt <= aurora.end && <circle className="aurora-window-marker"
            cx={xFor(aurora.forecastAt)} cy="198" r="5">
            <title>{`${t('activityValue')}: ${aurora.value}/100 · ${timeLabel(aurora.forecastAt)}`}</title>
          </circle>}
          <text className="cloud-darkness-axis-label" x={width - right} y="202" textAnchor="end">{aurora.value}/100</text>
        </g>}
        <text className="cloud-darkness-row-heading" x="0" y="245">{t('sunBelowHorizonLegend')}</text>
        {thresholds.map((threshold, index) => {
          const y = 258 + index * 27
          const label = t(index === 0 ? 'civilTwilightShort' : index === 1 ? 'nauticalTwilightShort' : 'astronomicalTwilightShort')
          return <g key={thresholdOrder[index]}>
            <text className="cloud-darkness-row-label" x="0" y={y + 9}>{label}</text>
            <line className="cloud-darkness-track" x1={left} x2={width - right} y1={y + 5} y2={y + 5} />
            {threshold?.intervals.map(interval => {
              const intervalStart = Math.max(start, Date.parse(interval.startUtc))
              const intervalEnd = Math.min(end, Date.parse(interval.endUtc))
              if (intervalEnd <= intervalStart) return null
              return <rect className={`cloud-darkness-band darkness-band-${index}`} key={interval.startUtc}
                x={xFor(intervalStart)} y={y} width={Math.max(2, xFor(intervalEnd) - xFor(intervalStart))} height="11" rx="4">
                <title>{`${label}: ${timeLabel(intervalStart)} – ${timeLabel(intervalEnd)}`}</title>
              </rect>
            })}
            {threshold && threshold.intervals.length === 0 && <text className="cloud-darkness-no-interval" x={left + 5} y={y + 9}>{t(threshold.status === 'CALCULATION_FAILED' ? 'solarCalculationFailed' : 'solarNoInterval')}</text>}
          </g>
        })}
        {!validPoints.length && <text className="cloud-darkness-empty" x={left + 8} y={top + 16}>{t('cloudTimelineNoPoints')}</text>}
      </svg>
    </div>
    {selectedPoint?.cloudCoverPercent !== null && selectedPoint && <p className="cloud-point-selection" role="status">
      <strong>{timeLabel(Date.parse(selectedPoint.validAt))}</strong>
      <span>{t('cloudCoverLegend')}: {selectedPoint.cloudCoverPercent}%</span>
    </p>}
    <ol className="sr-only">
      {validPoints.map(point => <li key={`cloud-${point.validAt}`}>{timeLabel(Date.parse(point.validAt))}: {point.cloudCoverPercent}%</li>)}
      {thresholds.map((threshold, index) => <li key={`darkness-${thresholdOrder[index]}`}>
        {t(index === 0 ? 'civilTwilightShort' : index === 1 ? 'nauticalTwilightShort' : 'astronomicalTwilightShort')}: {threshold?.intervals.length
          ? threshold.intervals.map(interval => `${timeLabel(Date.parse(interval.startUtc))}–${timeLabel(Date.parse(interval.endUtc))}`).join(', ')
          : t(threshold?.status === 'CALCULATION_FAILED' ? 'solarCalculationFailed' : 'solarNoInterval')}
      </li>)}
    </ol>
  </section>
}
