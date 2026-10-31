import { useEffect, useState } from 'react'
import { applicationServerKey, getPushConfig, removeSubscription, saveSubscription } from '../../api/push'

type State = 'checking' | 'unsupported' | 'off' | 'on' | 'busy'

const supported = () => typeof window !== 'undefined' && 'serviceWorker' in navigator && 'PushManager' in window && 'Notification' in window

/** Opt-in Web Push so strong-activity alerts still arrive after the page is closed. */
export function BackgroundAlerts({ language }: { language: 'en' | 'zh' }) {
  const zh = language === 'zh'
  const [state, setState] = useState<State>(() => supported() ? 'checking' : 'unsupported')
  const [publicKey, setPublicKey] = useState<string | null>(null)
  const [notice, setNotice] = useState('')

  useEffect(() => {
    if (!supported()) return
    const controller = new AbortController()
    getPushConfig(controller.signal).then(async config => {
      if (!config.enabled || !config.publicKey) { setState('unsupported'); return }
      setPublicKey(config.publicKey)
      const registration = await navigator.serviceWorker.getRegistration('/')
      const subscription = await registration?.pushManager.getSubscription()
      setState(subscription && Notification.permission === 'granted' ? 'on' : 'off')
    }).catch(() => { if (!controller.signal.aborted) setState('unsupported') })
    return () => controller.abort()
  }, [])

  // Keep the language of pushed messages in step with the page.
  useEffect(() => {
    if (state !== 'on') return
    void navigator.serviceWorker.getRegistration('/')
      .then(registration => registration?.pushManager.getSubscription())
      .then(subscription => subscription ? saveSubscription(subscription, language) : undefined)
      .catch(() => undefined)
  }, [language, state])

  async function enable() {
    if (!publicKey) return
    setState('busy'); setNotice('')
    try {
      if (await Notification.requestPermission() !== 'granted') {
        setState('off')
        setNotice(zh ? '未开启通知权限，无法在关闭网页后提醒。' : 'Notification permission is off, so alerts cannot arrive after the page closes.')
        return
      }
      const registration = await navigator.serviceWorker.register('/sw.js', { scope: '/' })
      await navigator.serviceWorker.ready
      const subscription = await registration.pushManager.getSubscription()
        ?? await registration.pushManager.subscribe({ userVisibleOnly: true, applicationServerKey: applicationServerKey(publicKey) })
      await saveSubscription(subscription, language)
      setState('on')
      setNotice(zh ? '已开启：关闭网页后，Kp 达到 5 时也会收到浏览器通知。' : 'On: you will get a browser notification at Kp 5 even after closing the page.')
    } catch {
      setState('off')
      setNotice(zh ? '无法开启后台提醒，请稍后重试。' : 'Background alerts could not be turned on. Please try again later.')
    }
  }

  async function disable() {
    setState('busy'); setNotice('')
    try {
      const registration = await navigator.serviceWorker.getRegistration('/')
      const subscription = await registration?.pushManager.getSubscription()
      if (subscription) {
        await removeSubscription(subscription.endpoint).catch(() => undefined)
        await subscription.unsubscribe()
      }
      setState('off')
    } catch {
      setState('on')
      setNotice(zh ? '无法关闭后台提醒，请稍后重试。' : 'Background alerts could not be turned off. Please try again later.')
    }
  }

  if (state === 'checking' || state === 'unsupported') return null
  const on = state === 'on'
  return <div className="background-alerts">
    <button type="button" className="comparison-action" aria-pressed={on} disabled={state === 'busy'} onClick={() => { void (on ? disable() : enable()) }}>
      {zh ? on ? '关闭后台提醒' : '关闭网页后也提醒' : on ? 'Turn off background alerts' : 'Alert me after closing the page'}
    </button>
    {notice && <p role="status">{notice}</p>}
  </div>
}
