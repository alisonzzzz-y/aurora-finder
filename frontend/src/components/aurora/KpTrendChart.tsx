import { useEffect, useId, useMemo, useRef, useState, type PointerEvent, type KeyboardEvent } from 'react'
import type { KpIndexRecord } from '../../types/kpIndex'
import { useI18n } from '../../i18n'
import { canConnectKpPeriods, selectKpPeriods } from './kpPeriods'
import { formatKpTime, KP_PERIOD_MS, KP_WINDOW_MS, kpStormBand, kpStormScale, nearestKpRecord, normalizeKpRecords } from './kpChartData'

export function KpTrendChart({ records, timeZone }: { records: KpIndexRecord[]; timeZone: string }) {
  const { t, language } = useI18n()
  const locale = language === 'zh' ? 'zh-CN' : 'en'
  const clipId = useId()
  const wrap = useRef<HTMLDivElement>(null)
  const [width, setWidth] = useState(640)
  const [windowStart, setWindowStart] = useState<number | null>(null)
  const [activeTime, setActiveTime] = useState<string | null>(null)
  const [dragging, setDragging] = useState(false)
  const drag = useRef<{ x: number; start: number; moved: boolean } | null>(null)
  const sorted = useMemo(() => normalizeKpRecords(records), [records])
  const earliest = sorted.length ? Date.parse(sorted[0].periodStart) : 0
  const latest = sorted.length ? Date.parse(sorted[sorted.length - 1].periodStart) : earliest
  const span = Math.min(KP_WINDOW_MS, Math.max(KP_PERIOD_MS, latest - earliest))
  const lastStart = Math.max(earliest, latest - span)
  const clamp = (value: number) => Math.max(earliest, Math.min(lastStart, value))
  const defaultStart = clamp(Date.parse(selectKpPeriods(sorted).trend[0]?.periodStart ?? '') || earliest)
  const start = clamp(windowStart ?? defaultStart)
  const end = start + span
  const visible = sorted.filter(record => Date.parse(record.periodStart) >= start && Date.parse(record.periodStart) <= end)
  const active = visible.find(record => record.periodStart === activeTime)
  const hasGap = visible.slice(0, -1).some((record, index) => !canConnectKpPeriods(record, visible[index + 1]))
  const height = 420
  const left = 34, right = Math.max(left + 1, width - 14), top = 14, bottom = height - 58
  const xFor = (instant: string | number) => left + ((typeof instant === 'number' ? instant : Date.parse(instant)) - start) / span * (right - left)
  const yFor = (kp: number) => top + (9 - kp) / 9 * (bottom - top)
  const format = (instant: string | number, compact = false) => formatKpTime(instant, locale, timeZone, compact)
  const typeLabel = (record: KpIndexRecord) => t(record.type === 'OBSERVED' ? 'observedKp' : record.type === 'ESTIMATED' ? 'estimatedKp' : 'predictedKp')
  const scale = (record: KpIndexRecord) => kpStormScale(record.kp, t('noaaScaleName').split('|'), t('belowG1'), record.geomagneticStormScale)

  useEffect(() => {
    const element = wrap.current
    if (!element) return
    const observer = new ResizeObserver(entries => {
      const measured = entries[0]?.contentRect.width
      if (measured) setWidth(measured)
    })
    observer.observe(element)
    return () => observer.disconnect()
  }, [])

  function moveWindow(value: number) { setWindowStart(clamp(value)); setActiveTime(null) }
  function hoverAt(event: PointerEvent<SVGSVGElement>) {
    const bounds = event.currentTarget.getBoundingClientRect()
    const x = (event.clientX - bounds.left) / bounds.width * width
    const point = x >= left && x <= right ? nearestKpRecord(visible, start + (x - left) / (right - left) * span) : undefined
    setActiveTime(point?.periodStart ?? null)
  }
  function onPointerMove(event: PointerEvent<SVGSVGElement>) {
    const gesture = drag.current
    if (!gesture) { hoverAt(event); return }
    const dx = event.clientX - gesture.x
    if (Math.abs(dx) > 4) gesture.moved = true
    if (gesture.moved) {
      const bounds = event.currentTarget.getBoundingClientRect()
      moveWindow(gesture.start - dx / bounds.width * width / (right - left) * span)
    }
  }
  function finishDrag(event: PointerEvent<SVGSVGElement>) {
    const gesture = drag.current
    drag.current = null
    setDragging(false)
    if (event.currentTarget.hasPointerCapture(event.pointerId)) event.currentTarget.releasePointerCapture(event.pointerId)
    if (gesture && !gesture.moved && event.type !== 'pointercancel') hoverAt(event)
  }
  function onKeyDown(event: KeyboardEvent<SVGSVGElement>) {
    if (event.key === 'Escape') { setActiveTime(null); return }
    if (!['ArrowLeft', 'ArrowRight', 'Home', 'End'].includes(event.key) || !visible.length) return
    event.preventDefault()
    const index = visible.findIndex(record => record.periodStart === activeTime)
    const next = event.key === 'Home' ? 0 : event.key === 'End' ? visible.length - 1
      : index < 0 ? (event.key === 'ArrowLeft' ? visible.length - 1 : 0)
      : Math.max(0, Math.min(visible.length - 1, index + (event.key === 'ArrowLeft' ? -1 : 1)))
    setActiveTime(visible[next].periodStart)
  }

  if (!sorted.length) return <p className="latest-forecast-message">{t('kpChartNoData')}</p>
  const tickCount = width < 600 ? 3 : 5
  const ticks = Array.from({ length: tickCount }, (_, index) => start + span * index / (tickCount - 1))
  return <div className="kp-chart-area">
    <div className="kp-chart-legend" aria-hidden="true">
      <span><i className="observed" />{t('observedKp')}</span><span><i className="estimated" />{t('estimatedKp')}</span><span><i className="predicted" />{t('predictedKp')}</span>
      {hasGap && <span><i className="gap" />{t('kpChartGapLegend')}</span>}
    </div>
    <div ref={wrap} className="kp-chart-wrap kp-interactive-chart">
      <svg className={`kp-chart${dragging ? ' is-dragging' : ''}`} viewBox={`0 0 ${width} ${height}`} role="group" tabIndex={0} aria-label={`${t('kpChartTitle')}. ${t('kpChartInteraction')}`} onKeyDown={onKeyDown} onBlur={() => setActiveTime(null)} onPointerMove={onPointerMove} onPointerLeave={() => { if (!drag.current) setActiveTime(null) }} onPointerDown={event => {
        if (event.button !== 0) return
        drag.current = { x: event.clientX, start, moved: false }
        setDragging(true)
        event.currentTarget.setPointerCapture(event.pointerId)
        hoverAt(event)
      }} onPointerUp={finishDrag} onPointerCancel={finishDrag}>
        <title>{t('kpChartTitle')}</title><desc>{t('kpChartDescription')}</desc>
        <defs><clipPath id={clipId}><rect x={left - 5} y={top - 5} width={right - left + 10} height={bottom - top + 10} /></clipPath></defs>
        {[0, 3, 5, 6, 7, 9].map(tick => <g key={tick}><line className={tick >= 5 && tick <= 7 ? `kp-gridline kp-threshold kp-storm-g${tick - 4}` : 'kp-gridline'} x1={left} x2={right} y1={yFor(tick)} y2={yFor(tick)} /><text className="kp-axis-label" x={left - 9} y={yFor(tick) + 4} textAnchor="end">{tick}</text></g>)}
        {[5, 6, 7].map(threshold => <text key={threshold} className={`kp-threshold-label kp-storm-g${threshold - 4}`} x={right} y={yFor(threshold) - 6} textAnchor="end">{t('kpThresholdLabel').replace('G1', `G${threshold - 4}`)} · Kp {threshold}</text>)}
        <g clipPath={`url(#${clipId})`}>
          {sorted.slice(0, -1).map((record, index) => {
            const next = sorted[index + 1]
            if (Date.parse(next.periodStart) < start || Date.parse(record.periodStart) > end || !canConnectKpPeriods(record, next)) return null
            return <path key={record.periodStart} className={`kp-chart-segment ${record.type.toLowerCase()}`} d={`M ${xFor(record.periodStart)} ${yFor(record.kp)} L ${xFor(next.periodStart)} ${yFor(next.kp)}`} />
          })}
          {visible.map(record => <g key={record.periodStart} className={`kp-chart-mark ${record.type.toLowerCase()} ${kpStormBand(record.kp)}`}>
            {record.type === 'ESTIMATED' ? <rect x={xFor(record.periodStart) - 4} y={yFor(record.kp) - 4} width="8" height="8" transform={`rotate(45 ${xFor(record.periodStart)} ${yFor(record.kp)})`} /> : <circle cx={xFor(record.periodStart)} cy={yFor(record.kp)} r="4" />}
          </g>)}
          {active && <g className={`kp-hover-guide ${kpStormBand(active.kp)}`}><line x1={xFor(active.periodStart)} x2={xFor(active.periodStart)} y1={top} y2={bottom} /><circle cx={xFor(active.periodStart)} cy={yFor(active.kp)} r="6" /></g>}
        </g>
        {ticks.map((time, index) => <text key={time} className="kp-time-label" x={xFor(time)} y={height - 28} textAnchor={index === 0 ? 'start' : index === ticks.length - 1 ? 'end' : 'middle'}>
          <tspan x={xFor(time)}>{new Intl.DateTimeFormat(locale, { month: 'short', day: 'numeric', timeZone }).format(time)}</tspan>
          <tspan x={xFor(time)} dy="18">{new Intl.DateTimeFormat(locale, { hour: '2-digit', minute: '2-digit', hourCycle: 'h23', timeZone }).format(time)}</tspan>
        </text>)}
      </svg>
      {active && <div className="kp-hover-readout" role="status" style={{ left: Math.max(8, Math.min(width - Math.min(240, width - 16) - 8, xFor(active.periodStart) + 12)), top: Math.max(8, Math.min(bottom - 110, yFor(active.kp) - 105)) }}>
        <time dateTime={active.periodStart}>{format(active.periodStart)}</time><strong>Kp {active.kp.toFixed(2)}</strong><span>{typeLabel(active)} · {t('noaaScaleLabel')}: {scale(active)}</span>
      </div>}
    </div>
    <div className="kp-time-navigation">
      <button type="button" aria-label={t('kpEarlier')} title={t('kpEarlier')} disabled={start <= earliest} onClick={() => moveWindow(start - KP_PERIOD_MS)}>←</button>
      <label className="kp-time-slider"><span className="sr-only">{t('kpTimeline')}</span><input type="range" min={earliest} max={lastStart} step="any" value={start} disabled={lastStart === earliest} aria-valuetext={`${format(start)} – ${format(end)}`} onChange={event => moveWindow(Number(event.target.value))} /></label>
      <button type="button" aria-label={t('kpLater')} title={t('kpLater')} disabled={start >= lastStart} onClick={() => moveWindow(start + KP_PERIOD_MS)}>→</button>
      <button type="button" className="kp-time-reset" onClick={() => moveWindow(defaultStart)}>{t('kpRecentWindow')}</button>
    </div>
    <ol className="sr-only">{visible.map(record => <li key={record.periodStart}>{format(record.periodStart)} · {typeLabel(record)} · Kp {record.kp.toFixed(2)} · {scale(record)}</li>)}</ol>
  </div>
}
