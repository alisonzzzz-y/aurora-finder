import { useEffect, useRef, useState } from 'react'
import type { GeomagneticStormDay } from '../../types/geomagneticStorm'
import type { GeomagneticWarning } from '../../types/geomagneticWarnings'
import { useI18n } from '../../i18n'
import '../outlook/VisualOutlook.css'
const colors = ['#83b8ee', '#f2ce72', '#ffa36b', '#ff7f91']
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
      if (measuredWidth) setWidth(Math.max(280, measuredWidth))
    })
    observer.observe(element)
    return () => observer.disconnect()
  }, [])
  const plotStart = 48
  const plotEnd = width - 28
  const yFor = (value: number) => 330 - value / 100 * 300
  const groupWidth = (plotEnd - plotStart) / Math.max(1, days.length)
  const gap = width < 500 ? 3 : 8
  const barWidth = Math.min(64, (groupWidth * .8 - gap * 3) / 4)
  const center = (index: number) => plotStart + groupWidth * (index + .5)
  const categories = [ ['activeGeomagnetic', 'activeChancePercent'], ['minorStorm', 'minorStormChancePercent'], ['moderateStorm', 'moderateStormChancePercent'], ['strongStorm', 'strongExtremeStormChancePercent'] ] as const
  const date = (value: string) => new Intl.DateTimeFormat(language === 'zh' ? 'zh-CN' : 'en', {month:'short',day:'numeric',timeZone:'UTC'}).format(new Date(`${value}T12:00:00Z`))
  return <figure ref={chartRef} className="comparison-chart storm-daily-chart">
    <div className="chart-legend">{categories.map(([label,field],index)=><span key={field}><i style={{background:colors[index]}} />{t(label)}</span>)}</div>
    <svg viewBox={`0 0 ${width} 385`} role="group" aria-label={t('stormProbabilityExplanation')}>
      {[0,25,50,75,100].map(value=><g key={value}><line x1={plotStart} x2={plotEnd} y1={yFor(value)} y2={yFor(value)}/><text x={plotStart-8} y={yFor(value)+5} textAnchor="end">{value}%</text></g>)}
      {days.map((day,index)=><g key={day.date}>
        <text x={center(index)} y={354} textAnchor="middle">{date(day.date)}<tspan x={center(index)} dy="19">UTC</tspan></text>
        {categories.map(([label,field],category)=>{
          const selected = active?.day === index && active.category === category
          const x = center(index) - (barWidth*4+gap*3)/2 + category*(barWidth+gap)
          const y = yFor(day[field])
          return <g key={field} className="storm-bar-reading" tabIndex={0} role="img"
            aria-label={`${date(day.date)} UTC, ${t(label)}: ${day[field]}%. ${t('stormProbabilityReading')}`}
            onPointerMove={event=>showReading(index, category, event.clientX, event.clientY)}
            onPointerLeave={()=>setActive(null)}
            onFocus={event=>{const bounds=event.currentTarget.getBoundingClientRect(); showReading(index, category, bounds.left, bounds.top)}}
            onBlur={()=>setActive(null)} onKeyDown={event=>{if(event.key==='Escape') setActive(null)}}>
            <rect className="storm-bar-hit" x={x-gap/2} y={30} width={barWidth+gap} height={300} fill="transparent" />
            {selected && <rect className="storm-bar-highlight" x={x-gap/2} y={30} width={barWidth+gap} height={300} />}
            <rect className="storm-bar" x={x} y={y} width={barWidth} height={330-y} fill={colors[category]} />
            {width >= 600 && <text x={x+barWidth/2} textAnchor="middle" y={y-8}>{day[field]}%</text>}
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
