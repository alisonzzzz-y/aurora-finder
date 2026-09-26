import { useState, type FormEvent } from 'react'
import { askAssistant, type AssistantMessage } from '../../api/assistant'
import type { Location } from '../../types/location'
import { useI18n } from '../../i18n'

const exampleQuestions = [
  'assistantExampleTonight',
  'assistantExampleKp',
  'assistantExampleClouds',
] as const

type AssistantChatPanelProps = {
  locationId?: number
}

export function AssistantChatPanel({ locationId }: AssistantChatPanelProps) {
  const { language, t } = useI18n()
  const [open, setOpen] = useState(false)
  const [draft, setDraft] = useState('')
  const [messages, setMessages] = useState<AssistantMessage[]>([])
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  async function submitQuestion(event: FormEvent<HTMLFormElement>, selectedLocation?: Location) {
    event.preventDefault()
    await sendQuestion(selectedLocation ? selectionLabel(selectedLocation) : draft.trim(), selectedLocation)
  }

  function selectionLabel(location: Location) {
    return [location.name, location.region, location.subregion, location.country].filter(Boolean).join(', ')
  }

  async function sendQuestion(question: string, selectedLocation?: Location) {
    if (!question || busy) return
    const history = messages
    setMessages(current => [...current, { role: 'user', content: question }])
    setDraft('')
    setError('')
    setBusy(true)
    try {
      const response = await askAssistant(question, language, history, selectedLocation?.id ?? locationId)
      setMessages(current => [...current, {
        role: 'assistant', content: response.answer,
        ...(response.locationCandidates?.length ? { locationCandidates: response.locationCandidates } : {}),
      }])
    } catch {
      setError(t('assistantRequestFailed'))
    } finally {
      setBusy(false)
    }
  }

  return <div className="assistant-widget">
    <section id="assistant-panel" className="assistant-panel" aria-labelledby="assistant-title" hidden={!open}>
      <header className="assistant-panel-header">
        <div>
          <p className="eyebrow">{t('assistantEyebrow')}</p>
          <h2 id="assistant-title">{t('assistantTitle')}</h2>
        </div>
        <button type="button" className="assistant-collapse" aria-label={t('assistantCollapse')} onClick={() => setOpen(false)}>−</button>
      </header>
      <p className="assistant-intro">{t('assistantIntro')}</p>
      {messages.length === 0 && <div className="assistant-examples" aria-label={t('assistantExamplesLabel')}>
        {exampleQuestions.map(key => <button type="button" key={key} onClick={() => { setDraft(t(key)); setError('') }}>{t(key)}</button>)}
      </div>}
      {messages.length > 0 && <div className="assistant-messages" aria-live="polite">
        {messages.map((message, index) => <div key={`${message.role}-${index}`} className={`assistant-message ${message.role}`}>
          <span>{message.role === 'assistant' ? t('assistantRole') : t('assistantYou')}</span>
          <div>{message.content}</div>
          {message.role === 'assistant' && message.locationCandidates?.map((candidate, candidateIndex) => <button
            type="button" className="assistant-location-choice" key={candidate.id} disabled={busy}
            onClick={() => void sendQuestion(selectionLabel(candidate), candidate)}>
            {candidateIndex + 1}. {selectionLabel(candidate)}
          </button>)}
        </div>)}
        {busy && <p className="assistant-message assistant pending"><span>{t('assistantRole')}</span>{t('assistantWorking')}</p>}
      </div>}
      {error && <p className="assistant-notice error" role="alert">{error}</p>}
      <form className="assistant-composer" onSubmit={submitQuestion}>
        <label className="sr-only" htmlFor="assistant-question">{t('assistantInputLabel')}</label>
        <textarea id="assistant-question" rows={2} maxLength={1000} value={draft} disabled={busy} placeholder={t('assistantInputPlaceholder')} onChange={event => { setDraft(event.target.value); setError('') }} onKeyDown={event => {
          if (event.key !== 'Enter' || event.shiftKey || event.nativeEvent.isComposing) return
          event.preventDefault()
          event.currentTarget.form?.requestSubmit()
        }} />
        <div className="assistant-composer-actions">
          <small>{t('assistantSourceNote')}</small>
          <button type="submit" disabled={!draft.trim() || busy}>{busy ? t('assistantWorkingShort') : t('assistantSend')}</button>
        </div>
      </form>
    </section>
    <button className="agent-button" type="button" aria-expanded={open} aria-controls="assistant-panel" onClick={() => setOpen(value => !value)}>{open ? t('assistantClose') : t('askAboutNight')} <span>✦</span></button>
  </div>
}
