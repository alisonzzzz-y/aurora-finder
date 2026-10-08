import { useEffect, useState } from 'react'
import { getKpIndex } from '../../api/kpIndex'
import type { KpIndexData } from '../../types/kpIndex'
import { useI18n } from '../../i18n'
import { InfoHint } from '../InfoHint'
import { dailyForecastPeaks } from './dailyForecastPeaks'

export function KpPeakForecast() {
  const { language, t } = useI18n()
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
        if (!current.signal.aborted) setData(response)
      } catch { if (!current.signal.aborted) setData(null) }
      finally { if (!current.signal.aborted) { setLoading(false); setNow(Date.now()) } }
    }
    void load()
    const timer = window.setInterval(() => { void load() }, 120000)
    return () => { request?.abort(); window.clearInterval(timer) }
  }, [])
  const peaks = dailyForecastPeaks(data?.records ?? [], now)
  const formatter = new Intl.DateTimeFormat(language === 'zh' ? 'zh-CN' : 'en-IE', { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit', timeZoneName: 'short' })
  return <section className="activity-peak-forecast" aria-label={t('activityPeakTitle')}>
    <h3>{t('activityPeakTitle')}<InfoHint>{t('activityPeakNote')}</InfoHint></h3>
    {loading ? <p role="status">{t('loading')}</p> : peaks.length === 0 ? <p>{t('kpPeakUnavailable')}</p> : <ul>
      {peaks.map(point => <li key={point.periodStart}>
        <strong>Kp {point.kp.toFixed(2)}</strong>
        <span>{formatter.format(new Date(point.periodStart))} – {formatter.format(new Date(Date.parse(point.periodStart) + 10800000))}</span>
      </li>)}
    </ul>}
  </section>
}
