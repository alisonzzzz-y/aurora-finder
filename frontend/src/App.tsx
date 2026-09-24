import { useEffect, useRef, useState } from 'react'
import { getOutlook } from './api/outlooks'
import type { Location } from './types/location'
import type { Outlook } from './types/outlook'
import { LocationSearchPage } from './pages/LocationSearchPage'
import { OutlookPage } from './pages/OutlookPage'
import { I18nProvider, localizeError, useI18n } from './i18n'
import './App.css'

function AppContent() {
  const { language, setLanguage, t } = useI18n()
  const [outlook, setOutlook] = useState<Outlook | null>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const outlookRequest = useRef<AbortController | null>(null)

  useEffect(() => () => outlookRequest.current?.abort(), [])

  async function selectLocation(location: Location) {
    outlookRequest.current?.abort()
    const controller = new AbortController()
    outlookRequest.current = controller
    setBusy(true)
    setError('')
    try {
      const result = await getOutlook(location.id, controller.signal)
      if (!controller.signal.aborted && outlookRequest.current === controller) setOutlook(result)
    } catch (cause) {
      if (!controller.signal.aborted && outlookRequest.current === controller) {
        setError(cause instanceof Error ? cause.message : 'The selected location could not be loaded.')
      }
    } finally {
      if (outlookRequest.current === controller) {
        outlookRequest.current = null
        setBusy(false)
      }
    }
  }

  function changeLocation() {
    outlookRequest.current?.abort()
    outlookRequest.current = null
    setOutlook(null)
    setError('')
    setBusy(false)
  }

  return <div className={`page-shell${outlook ? '' : ' home-page-shell'}`}>
    <header className="site-header">
      <div className="brand"><span className="brand-mark">✦</span> {t('brand')}</div>
      <div className="header-tools">
        <span className="status-pill">{t('projectStatus')}</span>
        <div className="language-switch" role="group" aria-label={t('languageLabel')}>
          <button type="button" aria-label="English" aria-pressed={language === 'en'} className={language === 'en' ? 'active' : ''} onClick={() => setLanguage('en')}>EN</button>
          <button type="button" aria-label="简体中文" aria-pressed={language === 'zh'} className={language === 'zh' ? 'active' : ''} onClick={() => setLanguage('zh')}>中文</button>
        </div>
      </div>
    </header>
    <main>
      {outlook
        ? <OutlookPage outlook={outlook} onChangeLocation={changeLocation} />
        : <LocationSearchPage busy={busy} onSelect={selectLocation} />}
      {error && !outlook && <p className="error page-error" role="alert">{localizeError(new Error(error), t)}</p>}
      {!outlook && <button className="agent-button" type="button" disabled title={t('askUnavailable')}>{t('askAboutNight')} <span>✦</span></button>}
    </main>
    <footer>{t('footer')}</footer>
  </div>
}

export default function App() {
  return <I18nProvider><AppContent /></I18nProvider>
}
