import { useEffect, useRef, useState } from 'react'
import type { GeomagneticStormDay } from '../../types/geomagneticStorm'
import type { GeomagneticWarning } from '../../types/geomagneticWarnings'
import { useI18n } from '../../i18n'
import { InfoHint } from '../InfoHint'
import '../outlook/VisualOutlook.css'
const colors = ['#c8b8ed', '#9b7dcf', '#725397']
export function StormComparisonChart({ days }: { days: GeomagneticStormDay[] }) {
  const { language, t } = useI18n()
  const chartRef = useRef<HTMLElement>(null)
  const [width, setWidth] = useState(760)
  const [active, setActive] = useState<{ day: number; category: number; x: number; y: number } | null>(null)
  const showReading = (day: number, category: number, x: number, y: number) => {
    setActive({ day, category, x: Math.max(12, Math.min(x + 14, window.innerWidth - 292)), y: Math.max(12, Math.min(y + 16, window.innerHeight - 190)) })
  }
  useEffect(() => {
    const element = chartRef.current
    if (!element) return
    const observer = new ResizeObserver(entries => {
      const measuredWidth = entries[0]?.contentRect.width
      if (measuredWidth) setWidth(Math.max(760, measuredWidth))
    })
    observer.observe(element)
    return () => observer.disconnect()
  }, [])
  const plotStart = 235
  const plotEnd = width - 28
  const xFor = (value: number) => plotStart + value / 100 * (plotEnd - plotStart)
  const categories = [ ['activeGeomagnetic', 'activeChancePercent'], ['minorStorm', 'minorStormChancePercent'], ['moderateStorm', 'moderateStormChancePercent'], ['strongStorm', 'strongExtremeStormChancePercent'] ] as const
  const date = (value: string) => new Intl.DateTimeFormat(language === 'zh' ? 'zh-CN' : 'en', {month:'short',day:'numeric',timeZone:'UTC'}).format(new Date(`${value}T12:00:00Z`))
  return <figure ref={chartRef} className="comparison-chart">
    <div className="storm-probability-heading">{t('stormProbabilityTitle')}<InfoHint>{t('stormProbabilityExplanation')}</InfoHint></div>
    <div className="chart-legend">{days.map((day,index)=><span key={day.date}><i style={{background:colors[index % colors.length]}} />{date(day.date)} (UTC)</span>)}</div>
    <svg viewBox={`0 0 ${width} 360`} role="group" aria-label={t('stormOutlookTitle')}>
      {[0,25,50,75,100].map(value=><g key={value}><line x1={xFor(value)} x2={xFor(value)} y1={12} y2={326}/><text x={xFor(value)} y={350} textAnchor="middle">{value}%</text></g>)}
      {categories.map(([label,field],category)=><g key={field}>
        <text x={34} y={43+category*78} textAnchor="start">{t(label)}</text>
        {days.map((day,index)=>{
          const selected = active?.day === index && active.category === category
          const y = 14+category*78+index*19
          return <g key={day.date} className="storm-bar-reading" tabIndex={0} role="img"
            aria-label={`${date(day.date)} UTC, ${t(label)}: ${day[field]}%. ${t('stormProbabilityExplanation')}`}
            onPointerMove={event=>showReading(index, category, event.clientX, event.clientY)}
            onPointerLeave={()=>setActive(null)}
            onFocus={event=>{const bounds=event.currentTarget.getBoundingClientRect(); showReading(index, category, bounds.left, bounds.bottom)}}
            onBlur={()=>setActive(null)} onKeyDown={event=>{if(event.key==='Escape') setActive(null)}}>
            <rect className="storm-bar-hit" x={plotStart} y={y-2} width={plotEnd-plotStart} height={19} fill="transparent" />
            {selected && <rect className="storm-bar-highlight" x={plotStart} y={y-2} width={plotEnd-plotStart} height={19} />}
            <rect className="storm-bar" x={plotStart} y={y} width={xFor(day[field])-plotStart} height={14} fill={colors[index % colors.length]} />
            <text x={day[field] > 95 ? xFor(day[field])-6 : xFor(day[field])+8} textAnchor={day[field] > 95 ? 'end' : 'start'} y={y+11}>{day[field]}%</text>
          </g>
        })}
      </g>)}
    </svg>
    {active && <div className="storm-bar-tooltip" role="status" style={{left:active.x,top:active.y}}>
      <span>{date(days[active.day].date)} (UTC)</span>
      <strong>{t(categories[active.category][0])} · {days[active.day][categories[active.category][1]]}%</strong>
      <span>{t('stormProbabilityReading')}</span>
    </div>}
  </figure>
}
export function WarningTimeline({ warnings, now }: { warnings: GeomagneticWarning[]; now: number }) {
  const { language, t } = useI18n()
  const valid = warnings.filter(w=>Number.isFinite(Date.parse(w.validFrom)) && Date.parse(w.validTo)>Date.parse(w.validFrom))
  if (!valid.length) return null
  const start=Math.min(now,...valid.map(w=>Date.parse(w.validFrom)))
  const end=Math.max(now,...valid.map(w=>Date.parse(w.validTo)))
  const position=(time:number)=>(time-start)/(end-start)*100
  const format=(time:number)=>new Intl.DateTimeFormat(language==='zh'?'zh-CN':'en',{month:'short',day:'numeric',hour:'2-digit',minute:'2-digit',timeZoneName:'short'}).format(new Date(time))
  return <div><div className="warning-axis"><span>{format(start)}</span><span>{format(end)}</span></div>{valid.map(w=><div className="warning-time-row" key={`${w.productId}:${w.validFrom}`}><strong>{w.noaaScale ?? `K ${w.expectedKIndex}`}</strong><div className="warning-time-track" role="img" aria-label={`${t('warningValidWindow')}: ${format(Date.parse(w.validFrom))} - ${format(Date.parse(w.validTo))}`}><span className="warning-time-band" style={{left:`${position(Date.parse(w.validFrom))}%`,width:`${position(Date.parse(w.validTo))-position(Date.parse(w.validFrom))}%`}}/><span className="warning-time-now" style={{left:`${position(now)}%`}} title={format(now)}/></div></div>)}<p className="visual-chart-note">{t('warningNowMarker')}</p></div>
}
