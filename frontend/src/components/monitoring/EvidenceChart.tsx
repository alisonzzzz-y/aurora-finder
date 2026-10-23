import { useEffect, useRef, useState, type CSSProperties } from 'react'

export type EvidencePoint = { at: string; value: number; source?: string }
export function EvidenceChart({ points, unit, label, locale, maxGapMinutes = 5, bounds, color = '#83b8ee' }: {
  points: EvidencePoint[]; unit: string; label: string; locale: string; maxGapMinutes?: number; bounds?: [number, number]; color?: string
}) {
  const ref = useRef<HTMLDivElement>(null)
  const [width, setWidth] = useState(360)
  const [selected, setSelected] = useState<number | null>(null)
  useEffect(() => {
    if (!ref.current) return
    const observer = new ResizeObserver(entries => setWidth(Math.max(1, entries[0].contentRect.width)))
    observer.observe(ref.current)
    return () => observer.disconnect()
  }, [])
  const data = points.filter(p => Number.isFinite(Date.parse(p.at)) && Number.isFinite(p.value)).toSorted((a, b) => Date.parse(a.at) - Date.parse(b.at))
  const start = data.length ? Date.parse(data[0].at) : 0
  const end = data.length ? Date.parse(data[data.length - 1].at) : 1
  const min = bounds?.[0] ?? Math.min(0, ...data.map(p => p.value))
  const max = bounds?.[1] ?? Math.max(min + 1, ...data.map(p => p.value))
  const x = (at: string) => 48 + (Date.parse(at) - start) / Math.max(1, end - start) * Math.max(1, width - 62)
  const y = (v: number) => 18 + (max - v) / (max - min) * 150
  const format = (at: string) => new Intl.DateTimeFormat(locale, { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit', timeZone: 'UTC' }).format(new Date(at))
  const point = selected === null ? null : data[selected]
  const path = data.map((p, i) => `${i === 0 || Date.parse(p.at) - Date.parse(data[i - 1].at) > maxGapMinutes * 60000 ? 'M' : 'L'} ${x(p.at)} ${y(p.value)}`).join(' ')
  return <div ref={ref} className="evidence-chart" style={{ '--evidence-color': color } as CSSProperties}>
    <svg viewBox={`0 0 ${width} 210`} role="img" aria-label={`${label} (${unit}), UTC`} tabIndex={0}
      onPointerMove={event => {
        if (!data.length) return
        const cursor = event.clientX - event.currentTarget.getBoundingClientRect().left
        let closest = 0
        data.forEach((p, i) => { if (Math.abs(x(p.at) - cursor) < Math.abs(x(data[closest].at) - cursor)) closest = i })
        setSelected(closest)
      }} onPointerLeave={() => setSelected(null)} onKeyDown={event => {
        if (event.key === 'Escape') setSelected(null)
        if (event.key === 'ArrowLeft' || event.key === 'ArrowRight') {
          event.preventDefault()
          setSelected(Math.max(0, Math.min(data.length - 1, (selected ?? data.length - 1) + (event.key === 'ArrowRight' ? 1 : -1))))
        }
      }}>
      {[min, (min + max) / 2, max].map(v => <g key={v}><line x1="48" x2={width - 14} y1={y(v)} y2={y(v)} /><text x="42" y={y(v) + 4} textAnchor="end">{Number(v.toFixed(1))}</text></g>)}
      {data.slice(1).map((p, i) => Date.parse(p.at) - Date.parse(data[i].at) > maxGapMinutes * 60000 ? <rect key={p.at} className="evidence-gap" x={x(data[i].at)} y="18" width={x(p.at)-x(data[i].at)} height="150"><title>{locale.startsWith('zh') ? '此时段缺少连续记录' : 'No continuous records in this interval'}</title></rect> : null)}
      <path d={path} />
      {data.map(p => <circle key={p.at} cx={x(p.at)} cy={y(p.value)} r="2.5" />)}
      {data.length > 0 && (width < 420 ? [0, 1] : [0, 0.5, 1]).map((ratio, i) => <text key={i} x={48 + ratio * (width - 62)} y="193" textAnchor={i === 0 ? 'start' : ratio === 1 ? 'end' : 'middle'}>{new Intl.DateTimeFormat(locale, { month: 'numeric', day: 'numeric', hour: '2-digit', minute: '2-digit', timeZone: 'UTC' }).format(new Date(start + ratio * (end - start)))}</text>)}
      {point && <g className="evidence-cursor"><line x1={x(point.at)} x2={x(point.at)} y1="18" y2="168" /><circle cx={x(point.at)} cy={y(point.value)} r="5" /></g>}
    </svg>
    <div className="evidence-readout" role="status">{point ? `${format(point.at)} UTC · ${point.value} ${unit}${point.source ? ` · ${point.source}` : ''}` : data.length === 0 ? (locale.startsWith('zh') ? '暂无有效记录' : 'No valid records') : ''}</div>
  </div>
}
