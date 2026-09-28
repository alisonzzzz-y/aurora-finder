import { useEffect, useRef, useState } from 'react'
import type { NightOutlook } from '../../types/outlook'
import type { WeatherCloudPoint } from '../../types/weatherForecast'
import { useI18n } from '../../i18n'
import './CloudDarknessTimeline.css'

type Props = {
  points: WeatherCloudPoint[]
  night: NightOutlook
  timezone: string
}

const thresholdOrder = ['CIVIL_TWILIGHT', 'NAUTICAL_TWILIGHT', 'ASTRONOMICAL_TWILIGHT'] as const

export function CloudDarknessTimeline({ points, night, timezone }: Props) {
  const { language, t } = useI18n()
  const chartRef = useRef<HTMLDivElement>(null)
  const [width, setWidth] = useState(380)
  const [selectedPointAt, setSelectedPointAt] = useState<string | null>(null)
  const locale = language === 'zh' ? 'zh-CN' : 'en'
  const start = Date.parse(night.evaluationWindowStartUtc)
  const end = Date.parse(night.evaluationWindowEndUtc)
  const timelinePoints = points.filter(point => {
    const instant = Date.parse(point.validAt)
    return Number.isFinite(instant) && instant >= start && instant <= end
  })
  const validPoints = timelinePoints.filter(point => point.cloudCoverPercent !== null)
  const selectedPoint = validPoints.find(point => point.validAt === selectedPointAt)
  const left = 110
  const right = 10
  const top = 25
  const cloudBottom = 164
  const height = 316
  const xFor = (instant: number) => left + (instant - start) / Math.max(1, end - start) * (width - left - right)
  const yFor = (cloud: number) => top + (100 - cloud) / 100 * (cloudBottom - top)
  const timeLabel = (instant: number) => new Intl.DateTimeFormat(locale, {
    hour: '2-digit', minute: '2-digit', timeZone: timezone,
  }).format(new Date(instant))
  const xTicks = [0, 0.25, 0.5, 0.75, 1].map(ratio => start + (end - start) * ratio)
  const thresholds = thresholdOrder.map(key => night.solarDarkness.thresholds.find(item => item.threshold === key))

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

  const description = t('cloudDarknessTimelineDescription')

  return <section className="cloud-darkness-timeline" aria-labelledby="cloud-darkness-timeline-title">
    <div className="cloud-darkness-title-row">
      <h3 id="cloud-darkness-timeline-title">{t('cloudDarknessTimelineTitle')}</h3>
      <span>{t('localTimeNote')}</span>
    </div>
    <div className="cloud-darkness-legend" aria-hidden="true">
      <span><i className="cloud-line-key" />{t('cloudCoverLegend')}</span>
      <span><i className="darkness-band-key" />{t('sunBelowHorizonLegend')}</span>
    </div>
    <div ref={chartRef} className="cloud-darkness-chart-wrap">
      <svg className="cloud-darkness-chart" viewBox={`0 0 ${width} ${height}`} role="img" aria-label={`${t('cloudDarknessTimelineTitle')}. ${description}`}>
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
          return <circle className="cloud-darkness-point" key={point.validAt} cx={xFor(instant)} cy={yFor(point.cloudCoverPercent!)} r="5" role="button" tabIndex={0}
            aria-label={`${timeLabel(instant)} · ${point.cloudCoverPercent}% ${t('cloudCoverLegend')}`}
            onClick={() => setSelectedPointAt(point.validAt)}
            onKeyDown={event => {
              if (event.key === 'Enter' || event.key === ' ') {
                event.preventDefault()
                setSelectedPointAt(point.validAt)
              }
            }}>
            <title>{`${timeLabel(instant)} · ${point.cloudCoverPercent}% ${t('cloudCoverLegend')}`}</title>
          </circle>
        })}
        <text className="cloud-darkness-row-heading" x="0" y="207">{t('sunBelowHorizonLegend')}</text>
        {thresholds.map((threshold, index) => {
          const y = 221 + index * 27
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
