import { useState } from 'react'
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

  async function selectLocation(location: Location) {
    setBusy(true)
    setError('')
    try {
      setOutlook(await getOutlook(location.id))
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'The outlook could not be loaded.')
    } finally {
      setBusy(false)
    }
  }

  return <div className="page-shell">
    <header className="site-header">
      <div className="brand"><span className="brand-mark">✦</span> Aurora Outlook</div>
      <span className="status-pill">Project foundation</span>
    </header>
    <main>
      {outlook
        ? <OutlookPage outlook={outlook} onChangeLocation={() => { setOutlook(null); setError('') }} />
        : <LocationSearchPage busy={busy} onSelect={selectLocation} />}
      {error && !outlook && <p className="error page-error" role="alert">{error}</p>}
      {!outlook && <button className="agent-button" type="button" disabled title="AI questions will be available after the shared facts service is validated">Ask about a night <span>✦</span></button>}
    </main>
    <footer>Location search uses Open-Meteo geocoding data under CC BY 4.0. Aurora viewing advice is not available in this foundation release.</footer>
  </div>
}

export default App
