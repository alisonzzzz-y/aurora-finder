import type { GeomagneticStormDay } from '../../types/geomagneticStorm'
import type { GeomagneticWarning } from '../../types/geomagneticWarnings'
import { useI18n } from '../../i18n'
import '../outlook/VisualOutlook.css'
const colors = ['#c8b8ed', '#9b7dcf', '#725397']
export function StormComparisonChart({ days }: { days: GeomagneticStormDay[] }) {
  const { language, t } = useI18n()
  const categories = [ ['activeGeomagnetic', 'activeChancePercent'], ['minorStorm', 'minorStormChancePercent'], ['moderateStorm', 'moderateStormChancePercent'], ['strongStorm', 'strongExtremeStormChancePercent'] ] as const
  const date = (value: string) => new Intl.DateTimeFormat(language === 'zh' ? 'zh-CN' : 'en', {month:'short',day:'numeric',timeZone:'UTC'}).format(new Date(`${value}T12:00:00Z`))
  return <figure className="comparison-chart"><div className="chart-legend">{days.map((day,index)=><span key={day.date}><i style={{background:colors[index % colors.length]}} />{date(day.date)} (UTC)</span>)}</div>
    <svg viewBox="0 0 1340 270" role="group" aria-label={t('stormOutlookTitle')}>
      {[0,25,50,75,100].map(value=><g key={value}><line x1={235+value*8.7} x2={235+value*8.7} y1={12} y2={240}/><text x={235+value*8.7} y={262} textAnchor="middle">{value}%</text></g>)}
      {categories.map(([label,field],category)=><g key={field}><text x={0} y={34+category*56} textAnchor="start">{t(label)}</text>{days.map((day,index)=><g key={day.date}><rect x={235} y={14+category*56+index*13} width={day[field]*8.7} height={9} fill={colors[index % colors.length]} tabIndex={0} role="img" aria-label={`${date(day.date)} UTC, ${t(label)}: ${day[field]}%`}><title>{date(day.date)}: {t(label)} {day[field]}%</title></rect><text x={243+day[field]*8.7} y={23+category*56+index*13}>{day[field]}%</text></g>)}</g>)}
    </svg><details className="visual-details"><summary>{t('cloudDetails')}</summary>{days.map(day=><p key={day.date}>{date(day.date)} (UTC): {categories.map(([label,field])=>`${t(label)} ${day[field]}%`).join(' · ')}</p>)}</details>
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
