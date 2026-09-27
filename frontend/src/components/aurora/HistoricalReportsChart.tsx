import { useEffect, useRef, useState } from 'react'
import { useI18n } from '../../i18n'
import reportSummary from '../../data/auroraHistorySummary.json'
import './HistoricalReportsChart.css'

const chartHeight = 300
const plot = { top: 20, right: 16, bottom: 38, left: 52 }

function niceStep(maximum: number) {
  const raw = maximum / 4
  const magnitude = 10 ** Math.floor(Math.log10(raw))
  const scaled = raw / magnitude
  const factor = [1, 2, 2.5, 5, 10].find(candidate => candidate >= scaled) ?? 10
  return factor * magnitude
}

export function HistoricalReportsChart() {
  const { language, t } = useI18n()
  const chartRef = useRef<HTMLDivElement>(null)
  const [width, setWidth] = useState(760)
  const locale = language === 'zh' ? 'zh-CN' : 'en'
  const maxReports = Math.max(...reportSummary.years.map(item => item.reports))
  const tickStep = niceStep(maxReports)
  const axisMaximum = tickStep * 4
  const chartStart = plot.left
  const chartEnd = Math.max(chartStart + 1, width - plot.right)
  const chartTop = plot.top
  const chartBottom = chartHeight - plot.bottom
  const yFor = (value: number) => chartBottom - value / axisMaximum * (chartBottom - chartTop)
  const slotWidth = (chartEnd - chartStart) / reportSummary.years.length
  const barWidth = Math.min(40, slotWidth * 0.62)
  const yearStride = width < 500 ? 2 : 1
  const number = new Intl.NumberFormat(locale)

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

  return <section className="historical-reports" aria-labelledby="historical-reports-title">
    <p className="eyebrow">{t('historicalReportsEyebrow')}</p>
    <h2 id="historical-reports-title">{t('historicalReportsTitle')}</h2>
    <p className="historical-reports-intro">{t('historicalReportsIntro')}</p>
    <figure className="historical-reports-figure">
      <div className="historical-reports-chart-wrap" ref={chartRef}>
        <svg className="historical-reports-chart" viewBox={`0 0 ${width} ${chartHeight}`} role="img" aria-label={`${t('historicalReportsTitle')}. ${t('historicalReportsIntro')}`}>
          <title>{t('historicalReportsTitle')}</title>
          <desc>{t('historicalReportsIntro')}</desc>
          {Array.from({ length: 5 }, (_, index) => {
            const value = tickStep * index
            return <g key={value}>
              <line className="historical-reports-gridline" x1={chartStart} x2={chartEnd} y1={yFor(value)} y2={yFor(value)} />
              <text className="historical-reports-axis-label" x={chartStart - 8} y={yFor(value) + 4} textAnchor="end">{number.format(value)}</text>
            </g>
          })}
          {reportSummary.years.map((item, index) => {
            const x = chartStart + slotWidth * index + (slotWidth - barWidth) / 2
            const y = yFor(item.reports)
            return <g key={item.year} className="historical-reports-bar">
              <rect x={x} y={y} width={barWidth} height={chartBottom - y} rx="3">
                <title>{`${item.year}: ${number.format(item.reports)} ${t('historicalReportsCountLabel')}`}</title>
              </rect>
              {index % yearStride === 0 || index === reportSummary.years.length - 1
                ? <text className="historical-reports-axis-label" x={x + barWidth / 2} y={chartHeight - 12} textAnchor="middle">{item.year}</text>
                : null}
            </g>
          })}
        </svg>
      </div>
      <ol className="sr-only" aria-label={t('historicalReportsTitle')}>
        {reportSummary.years.map(item => <li key={`summary-${item.year}`}>
          {item.year}: {number.format(item.reports)} {t('historicalReportsCountLabel')}
        </li>)}
      </ol>
      <figcaption>
        <a href={reportSummary.sourceUrl} target="_blank" rel="noreferrer">{t('historicalReportsSource')} ↗</a>
        <span>{t('historicalReportsNote')}</span>
      </figcaption>
    </figure>
  </section>
}
