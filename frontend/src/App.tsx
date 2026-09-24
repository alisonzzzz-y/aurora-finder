import { useEffect, useRef, useState } from 'react'
import { getOutlook } from './api/outlooks'
import type { Location } from './types/location'
import type { Outlook } from './types/outlook'
import { LocationSearchPage } from './pages/LocationSearchPage'
import { OutlookPage } from './pages/OutlookPage'
import './App.css'

function App() {
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
        setError(cause instanceof Error ? cause.message : 'The outlook could not be loaded.')
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

  return <div className="page-shell">
    <header className="site-header">
      <div className="brand"><span className="brand-mark">✦</span> Aurora Outlook</div>
      <span className="status-pill">Project foundation</span>
    </header>
    <main>
      {outlook
        ? <OutlookPage outlook={outlook} onChangeLocation={changeLocation} />
        : <LocationSearchPage busy={busy} onSelect={selectLocation} />}
      {error && !outlook && <p className="error page-error" role="alert">{error}</p>}
      {!outlook && <button className="agent-button" type="button" disabled title="AI questions will be available after the shared facts service is validated">Ask about a night <span>✦</span></button>}
    </main>
    <footer>Location search uses Open-Meteo geocoding data under CC BY 4.0. Aurora viewing advice is not available in this foundation release.</footer>
  </div>
}

export default App
