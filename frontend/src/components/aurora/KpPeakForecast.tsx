import { recentActivityEvents } from './recentActivityEvents'
import { useEffect, useState } from 'react'
import { getKpIndex } from '../../api/kpIndex'
import type { KpIndexData } from '../../types/kpIndex'
import { useI18n } from '../../i18n'
import { InfoHint } from '../InfoHint'
import { dailyForecastPeaks } from './dailyForecastPeaks'

export function KpPeakForecast() {
  const { language, t } = useI18n()
  const [view, setView] = useState<'past' | 'future'>('future')
  const [failed, setFailed] = useState(false)
  const [data, setData] = useState<KpIndexData | null>(null)
  const [now, setNow] = useState(() => Date.now())
  const [loading, setLoading] = useState(true)
  useEffect(() => {
    let request: AbortController | undefined
    async function load() {
      request?.abort()
      const current = new AbortController(); request = current
      try {
        const response = await getKpIndex(current.signal)
        if (!current.signal.aborted) { setData(response); setFailed(false) }
      } catch { if (!current.signal.aborted) { setData(null); setFailed(true) } }
      finally { if (!current.signal.aborted) { setLoading(false); setNow(Date.now()) } }
    }
    void load()
    const timer = window.setInterval(() => { void load() }, 120000)
    return () => { request?.abort(); window.clearInterval(timer) }
  }, [])
  const peaks = dailyForecastPeaks(data?.records ?? [], now)
  const events = recentActivityEvents(data?.records ?? [], now)
  const c = language === 'zh' ? { title: '近期与未来活动时段', past: '近期已发生', future: '未来预报', peak: '峰值', estimated: '含估算', observed: '观测读数', empty: '可用记录中，过去 72 小时没有达到 Kp 5 的已结束时段。', missing: '历史数据暂不可用', note: '历史栏归并过去 72 小时内相邻的 Kp ≥ 5 已结束时段，最多展示三次事件。缺测不会连成一次事件。这表示全球地磁活动，不证明某座城市实际看到了极光。没有事件记录也不表示当地没出现极光。未来栏显示预测，可能变化。' } : { title: 'Recent and upcoming activity', past: 'Recent activity', future: 'Forecast', peak: 'Peak', estimated: 'Includes estimates', observed: 'Observed readings', empty: 'No completed Kp 5+ intervals in the available records for the past 72 hours.', missing: 'Historical data unavailable', note: 'Groups adjacent completed Kp 5+ intervals from the past 72 hours into up to three events. Gaps are not joined. These are global geomagnetic readings, not confirmed local sightings. No listed event does not mean no aurora occurred locally. Forecast values may change.' }
  const formatter = new Intl.DateTimeFormat(language === 'zh' ? 'zh-CN' : 'en-IE', { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit', timeZoneName: 'short' })
  return <section className="activity-peak-forecast" aria-label={c.title}>
    <h3>{c.title}<InfoHint>{c.note}</InfoHint></h3>
    <div className="activity-period-switch" role="group" aria-label={c.title}>
      <button type="button" aria-pressed={view === 'past'} onClick={() => setView('past')}>{c.past}{events.length > 0 && ` (${events.length})`}</button>
      <button type="button" aria-pressed={view === 'future'} onClick={() => setView('future')}>{c.future}</button>
    </div>
    {view === 'past' ? loading ? <p role="status">{t('loading')}</p> : failed || !data?.records.some(r => r.type !== 'PREDICTED') ? <p>{c.missing}</p> : events.length === 0 ? <p>{c.empty}</p> : <ul>
      {events.map(event => <li key={event.start}><strong>{c.peak} Kp {event.peak.toFixed(2)}</strong><span>{formatter.format(new Date(event.start))} – {formatter.format(new Date(event.end))}<br />{event.estimated ? c.estimated : c.observed}</span></li>)}
    </ul> : loading ? <p role="status">{t('loading')}</p> : peaks.length === 0 ? <p>{t('kpPeakUnavailable')}</p> : <ul>
      {peaks.map(point => <li key={point.periodStart}>
        <strong>Kp {point.kp.toFixed(2)}</strong>
        <span>{formatter.format(new Date(point.periodStart))} – {formatter.format(new Date(Date.parse(point.periodStart) + 10800000))}</span>
      </li>)}
    </ul>}
  </section>
}
