import { useEffect, useRef, useState, type FormEvent, type PointerEvent as ReactPointerEvent } from 'react'
import { askAssistant, type AssistantMessage } from '../../api/assistant'
import type { Location } from '../../types/location'
import { useI18n } from '../../i18n'
import { ApiRequestError } from '../../api/requestError'

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
  const [failedRequest, setFailedRequest] = useState<{ question: string; history: AssistantMessage[]; location?: Location } | null>(null)
  const panelRef = useRef<HTMLElement>(null)
  const dragRef = useRef<{ pointerId: number; offsetX: number; offsetY: number } | null>(null)
  const resizeRef = useRef<{ pointerId: number; startX: number; startY: number; left: number; top: number; width: number; height: number } | null>(null)
  const [position, setPosition] = useState<{ x: number; y: number } | null>(null)
  const [size, setSize] = useState<{ width: number; height: number } | null>(null)
  const positionRef = useRef(position)

  useEffect(() => {
    positionRef.current = position
  }, [position])

  useEffect(() => {
    function keepOnScreen() {
      if (!positionRef.current || !panelRef.current) return
      const bounds = panelRef.current.getBoundingClientRect()
      setPosition(current => current ? {
        x: Math.max(8, Math.min(current.x, window.innerWidth - bounds.width - 8)),
        y: Math.max(8, Math.min(current.y, window.innerHeight - bounds.height - 8)),
      } : null)
    }
    window.addEventListener('resize', keepOnScreen)
    return () => window.removeEventListener('resize', keepOnScreen)
  }, [])

  function startDragging(event: ReactPointerEvent<HTMLDivElement>) {
    if ((event.target as HTMLElement).closest('button')) return
    const bounds = panelRef.current?.getBoundingClientRect()
    if (!bounds) return
    setPosition({ x: bounds.left, y: bounds.top })
    dragRef.current = { pointerId: event.pointerId, offsetX: event.clientX - bounds.left, offsetY: event.clientY - bounds.top }
    event.currentTarget.setPointerCapture(event.pointerId)
  }

  function dragWindow(event: ReactPointerEvent<HTMLDivElement>) {
    const drag = dragRef.current
    const bounds = panelRef.current?.getBoundingClientRect()
    if (!drag || drag.pointerId !== event.pointerId || !bounds) return
    setPosition({
      x: Math.max(8, Math.min(event.clientX - drag.offsetX, window.innerWidth - bounds.width - 8)),
      y: Math.max(8, Math.min(event.clientY - drag.offsetY, window.innerHeight - bounds.height - 8)),
    })
  }

  function stopDragging(event: ReactPointerEvent<HTMLDivElement>) {
    if (dragRef.current?.pointerId !== event.pointerId) return
    dragRef.current = null
    if (event.currentTarget.hasPointerCapture(event.pointerId)) event.currentTarget.releasePointerCapture(event.pointerId)
  }

  function startResizing(event: ReactPointerEvent<HTMLButtonElement>) {
    event.preventDefault()
    event.stopPropagation()
    const bounds = panelRef.current?.getBoundingClientRect()
    if (!bounds) return
    resizeRef.current = {
      pointerId: event.pointerId, startX: event.clientX, startY: event.clientY,
      left: bounds.left, top: bounds.top, width: bounds.width, height: bounds.height,
    }
    event.currentTarget.setPointerCapture(event.pointerId)
  }

  function resizeWindow(event: ReactPointerEvent<HTMLButtonElement>) {
    const start = resizeRef.current
    if (!start || start.pointerId !== event.pointerId) return
    const maxWidth = Math.min(960, window.innerWidth - 24)
    const maxHeight = Math.min(920, window.innerHeight - 24)
    const width = Math.max(Math.min(340, maxWidth), Math.min(maxWidth, start.width + start.startX - event.clientX))
    const height = Math.max(Math.min(320, maxHeight), Math.min(maxHeight, start.height + start.startY - event.clientY))
    setSize({ width, height })
    setPosition({ x: Math.max(8, start.left + start.width - width), y: Math.max(8, start.top + start.height - height) })
  }

  function stopResizing(event: ReactPointerEvent<HTMLButtonElement>) {
    if (resizeRef.current?.pointerId !== event.pointerId) return
    resizeRef.current = null
    if (event.currentTarget.hasPointerCapture(event.pointerId)) event.currentTarget.releasePointerCapture(event.pointerId)
  }

  async function submitQuestion(event: FormEvent<HTMLFormElement>, selectedLocation?: Location) {
    event.preventDefault()
    await sendQuestion(selectedLocation ? selectionLabel(selectedLocation) : draft.trim(), selectedLocation)
  }

  function selectionLabel(location: Location) {
    return [location.name, location.region, location.subregion, location.country].filter(Boolean).join(', ')
  }

  async function sendQuestion(
    question: string,
    selectedLocation?: Location,
    historyOverride?: AssistantMessage[],
    appendUserMessage = true,
  ) {
    if (!question || busy) return
    const history = historyOverride ?? messages
    if (appendUserMessage) setMessages(current => [...current, { role: 'user', content: question }])
    setDraft('')
    setError('')
    setFailedRequest(null)
    setBusy(true)
    try {
      const response = await askAssistant(question, language, history, selectedLocation?.id ?? locationId)
      setMessages(current => [...current, {
        role: 'assistant', content: response.answer,
        ...(response.locationCandidates?.length ? { locationCandidates: response.locationCandidates } : {}),
      }])
    } catch (cause) {
      setError(t(cause instanceof ApiRequestError && cause.status === 429
        ? 'assistantRateLimited'
        : cause instanceof ApiRequestError && cause.status === 408
          ? 'assistantTimedOut' : 'assistantRequestFailed'))
      setFailedRequest({ question, history, ...(selectedLocation ? { location: selectedLocation } : {}) })
    } finally {
      setBusy(false)
    }
  }

  return <div className="assistant-widget">
    {open && <section ref={panelRef} id="assistant-panel" className={`assistant-panel${position ? ' is-moved' : ''}`} aria-labelledby="assistant-title" style={{ ...(position ? { left: position.x, top: position.y } : {}), ...(size ?? {}) }}>
      <button type="button" className="assistant-resize-handle" aria-label={t('assistantResize')} title={t('assistantResize')} onPointerDown={startResizing} onPointerMove={resizeWindow} onPointerUp={stopResizing} onPointerCancel={stopResizing}><span /></button>
      <header className="assistant-panel-header">
        <div className="assistant-drag-handle" title={t('assistantDrag')} onPointerDown={startDragging} onPointerMove={dragWindow} onPointerUp={stopDragging} onPointerCancel={stopDragging}>
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
      {error && <div className="assistant-error-wrap"><p className="assistant-notice error" role="alert">{error}</p>
        {failedRequest && <button type="button" className="assistant-retry" disabled={busy} onClick={() => void sendQuestion(
          failedRequest.question, failedRequest.location, failedRequest.history, false,
        )}>{t('assistantRetry')}</button>}
      </div>}
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
    </section>}
    <button className="agent-button" type="button" aria-expanded={open} aria-controls="assistant-panel" onClick={() => setOpen(!open)}>{open ? t('assistantClose') : t('askAboutNight')} <span>✦</span></button>
  </div>
}
