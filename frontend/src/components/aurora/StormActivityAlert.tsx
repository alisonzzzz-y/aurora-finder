import { useEffect, useRef, useState } from 'react'
import { getKpIndex } from '../../api/kpIndex'
import { useI18n } from '../../i18n'
import { InfoHint } from '../InfoHint'
import { freshStormData, stormAlerts, type StormAlert } from './stormAlerts'
import { BackgroundAlerts } from './BackgroundAlerts'
import './StormActivityAlert.css'

export function StormActivityAlert() {
  const { language } = useI18n()
  const zh = language === 'zh'
  const [alerts, setAlerts] = useState<StormAlert[]>([])
  const [status, setStatus] = useState<'loading' | 'ready' | 'unavailable'>('loading')
  const [enabled, setEnabled] = useState(false)
  const [notice, setNotice] = useState('')
  const seen = useRef(new Set<string>())
  const text = (alert: StormAlert) => `${zh ? alert.kind === 'current' ? '当前时段地磁活动增强' : '未来强活动时段' : alert.kind === 'current' ? 'Elevated activity in the current period' : 'Upcoming strong activity'} · Kp ${alert.kp.toFixed(2)}`
  useEffect(() => {
    let active = true
    let controller: AbortController | undefined
    async function load() {
      controller?.abort()
      controller = new AbortController()
      const signal = controller.signal
      try {
        const data = await getKpIndex(signal)
        if (!active || signal.aborted) return
        const now = Date.now()
        setStatus(freshStormData(data, now) ? 'ready' : 'unavailable')
        setAlerts(stormAlerts(data, now))
      } catch {
        if (!active || signal.aborted) return
        setStatus('unavailable')
        setAlerts([])
      }
    }
    void load()
    const timer = window.setInterval(() => { void load() }, 120000)
    return () => { active = false; controller?.abort(); window.clearInterval(timer) }
  }, [])

  useEffect(() => {
    if (!enabled || !('Notification' in window) || Notification.permission !== 'granted') return
    for (const alert of alerts) {
      if (seen.current.has(alert.key)) continue
      try {
        const notification = new Notification('Aurora Finder', {
          body: `${language === 'zh' ? alert.kind === 'current' ? '当前时段地磁活动增强' : '未来强活动时段' : alert.kind === 'current' ? 'Elevated activity in the current period' : 'Upcoming strong activity'} · Kp ${alert.kp.toFixed(2)}`,
          tag: alert.key,
        })
        notification.onclick = () => { window.focus(); notification.close() }
        seen.current.add(alert.key)
      } catch {
        // Synchronize UI with a failed external browser notification API.
        // oxlint-disable-next-line react/set-state-in-effect
        setEnabled(false)
        setNotice(language === 'zh' ? '浏览器通知不可用，页面提醒仍会更新。' : 'Browser notifications are unavailable. In-page alerts will still update.')
        break
      }
    }
  }, [alerts, enabled, language])

  async function toggle() {
    if (enabled) { setEnabled(false); return }
    if (!('Notification' in window)) {
      setNotice(zh ? '此浏览器不支持通知，页面提醒仍会更新。' : 'This browser does not support notifications. In-page alerts will still update.')
      return
    }
    try {
      const permission = await Notification.requestPermission()
      setEnabled(permission === 'granted')
      setNotice(permission === 'granted' ? '' : zh ? '未开启通知权限，页面提醒仍会更新。' : 'Notification permission is off. In-page alerts will still update.')
    } catch { setNotice(zh ? '无法开启浏览器通知。' : 'Could not enable browser notifications.') }
  }
  const time = (value: string) => new Intl.DateTimeFormat(zh ? 'zh-CN' : 'en-IE', { month:'short', day:'numeric', hour:'2-digit', minute:'2-digit', timeZoneName:'short' }).format(new Date(value))
  return <section className="storm-activity-alert" data-severity={status === 'ready' && alerts.some(alert => alert.kp >= 7) ? 'severe' : 'standard'} aria-label={zh ? '强活动提醒' : 'Strong activity alerts'}>
    <header><h2>{zh ? '强活动提醒' : 'Strong activity alerts'}<InfoHint>{zh ? 'Kp 达到 5 时提醒。当前时段使用观测或估算值，未来时段使用预报值。全球活动不代表当地可见概率。数据来源：NOAA。网站打开期间每两分钟检查一次；开启“关闭网页后也提醒”后，服务器每五分钟检查并推送浏览器通知（iPhone 和 iPad 需先把网站添加到主屏幕）。每个时段及等级本次访问只通知一次。' : 'Alerts start at Kp 5. Current periods use observed or estimated readings; future periods use forecasts. Global activity is not a local viewing probability. Source: NOAA. Checks run every two minutes while this page is open. With background alerts on, the server checks every five minutes and sends a browser notification after the page is closed (on iPhone and iPad, add the site to the Home Screen first). Each period and level is notified once per visit.'}</InfoHint></h2>
      <button type="button" className="comparison-action" aria-pressed={enabled} onClick={() => { void toggle() }}>{zh ? enabled ? '关闭提醒' : '开启提醒' : enabled ? 'Turn off alerts' : 'Enable alerts'}</button></header>
    <div role="status" aria-live="polite">{alerts.length ? alerts.map(alert => <article className={`storm-alert-reading ${alert.kp >= 7 ? 'storm-alert-severe' : ''}`} key={alert.key}>
      <strong>{text(alert)}</strong><time dateTime={alert.start}>{time(alert.start)} – {time(alert.end)}</time>
    </article>) : <p>{zh ? status === 'loading' ? '正在检查活动…' : status === 'unavailable' ? '提醒数据暂不可用' : '当前及未来 72 小时暂无强活动提醒' : status === 'loading' ? 'Checking activity…' : status === 'unavailable' ? 'Alert data is unavailable' : 'No strong activity alerts now or in the next 72 hours'}</p>}</div>
    {notice && <p role="status">{notice}</p>}
    <BackgroundAlerts language={language} />
  </section>
}
