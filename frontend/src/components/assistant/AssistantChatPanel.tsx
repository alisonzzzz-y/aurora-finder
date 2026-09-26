import { useState, type FormEvent } from 'react'
import { useI18n } from '../../i18n'

const exampleQuestions = [
  'assistantExampleTonight',
  'assistantExampleKp',
  'assistantExampleClouds',
] as const

export function AssistantChatPanel() {
  const { t } = useI18n()
  const [open, setOpen] = useState(false)
  const [draft, setDraft] = useState('')
  const [notice, setNotice] = useState('')

  function submitQuestion(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!draft.trim()) return
    setNotice(t('assistantNotConnected'))
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
      <div className="assistant-examples" aria-label={t('assistantExamplesLabel')}>
        {exampleQuestions.map(key => <button type="button" key={key} onClick={() => { setDraft(t(key)); setNotice('') }}>{t(key)}</button>)}
      </div>
      {notice && <p className="assistant-notice" role="status">{notice}</p>}
      <form className="assistant-composer" onSubmit={submitQuestion}>
        <label className="sr-only" htmlFor="assistant-question">{t('assistantInputLabel')}</label>
        <textarea id="assistant-question" rows={2} maxLength={1000} value={draft} placeholder={t('assistantInputPlaceholder')} onChange={event => { setDraft(event.target.value); setNotice('') }} />
        <button type="submit" disabled={!draft.trim()}>{t('assistantSend')}</button>
      </form>
    </section>
    <button className="agent-button" type="button" aria-expanded={open} aria-controls="assistant-panel" onClick={() => setOpen(value => !value)}>{open ? t('assistantClose') : t('askAboutNight')} <span>✦</span></button>
  </div>
}
