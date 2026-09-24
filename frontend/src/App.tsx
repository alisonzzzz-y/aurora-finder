import { useEffect, useRef, useState } from 'react'
import { getObservationFacts } from './api/observationFacts'
import type { Location } from './types/location'
import type { ObservationFacts } from './types/observationFacts'
import { LocationSearchPage } from './pages/LocationSearchPage'
import { OutlookPage } from './pages/OutlookPage'
import { I18nProvider, localizeError, useI18n } from './i18n'
import './App.css'
import { isProductionApiConfigured } from './api/apiUrl'

function AppContent() {
  const { language, setLanguage, t } = useI18n()
  const [facts, setFacts] = useState<ObservationFacts | null>(null)
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
      const result = await getObservationFacts(location.id, controller.signal)
      if (!controller.signal.aborted && outlookRequest.current === controller) setFacts(result)
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

  const selectedLocationId = facts?.outlook.location.id
  useEffect(() => {
    if (!selectedLocationId) return
    let request: AbortController | null = null
    const interval = window.setInterval(() => {
      request?.abort()
      request = new AbortController()
      const currentRequest = request
      getObservationFacts(selectedLocationId, currentRequest.signal)
        .then(result => {
          if (!currentRequest.signal.aborted) setFacts(current =>
            current?.outlook.location.id === selectedLocationId ? result : current)
        })
        .catch(() => {
          if (!currentRequest.signal.aborted) setFacts(current => {
            if (current?.outlook.location.id !== selectedLocationId) return current
            return {
              ...current,
              sourceStatus: 'UNAVAILABLE',
              auroraActivity: { ...current.auroraActivity, status: 'UNAVAILABLE', failureCode: 'NETWORK_ERROR', data: null },
              cloudForecast: { ...current.cloudForecast, status: 'UNAVAILABLE', failureCode: 'NETWORK_ERROR', data: null },
            }
          })
        })
    }, 5 * 60 * 1000)
    return () => {
      window.clearInterval(interval)
      request?.abort()
    }
  }, [selectedLocationId])

  function changeLocation() {
    outlookRequest.current?.abort()
    outlookRequest.current = null
    setFacts(null)
    setError('')
    setBusy(false)
  }

  return <div className={`page-shell${facts ? '' : ' home-page-shell'}`}>
    <header className="site-header">
      <div className="brand"><span className="brand-mark">✦</span> {t('brand')}</div>
      <div className="header-tools">
        <div className="language-switch" role="group" aria-label={t('languageLabel')}>
          <button type="button" aria-label="English" aria-pressed={language === 'en'} className={language === 'en' ? 'active' : ''} onClick={() => setLanguage('en')}>EN</button>
          <button type="button" aria-label="简体中文" aria-pressed={language === 'zh'} className={language === 'zh' ? 'active' : ''} onClick={() => setLanguage('zh')}>中文</button>
        </div>
      </div>
    </header>
    {!isProductionApiConfigured && <p className="deployment-config-alert" role="alert">{t('apiOriginMissing')}</p>}
    <main>
      {facts
        ? <OutlookPage facts={facts} onChangeLocation={changeLocation} />
        : <LocationSearchPage busy={busy} onSelect={selectLocation} />}
      {error && !facts && <p className="error page-error" role="alert">{localizeError(new Error(error), t)}</p>}
      {!facts && <button className="agent-button" type="button" disabled title={t('askUnavailable')}>{t('askAboutNight')} <span>✦</span></button>}
    </main>
    <footer>{t('footer')}</footer>
  </div>
}

export default function App() {
  return <I18nProvider><AppContent /></I18nProvider>
}
