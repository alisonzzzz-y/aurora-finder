import { useEffect, useId, useRef, useState, type ReactNode } from 'react'
import { createPortal } from 'react-dom'
import { useI18n } from '../i18n'
import './InfoHint.css'

export function InfoHint({ children }: { children: ReactNode }) {
  const id = useId()
  const { language } = useI18n()
  const anchor = useRef<HTMLSpanElement>(null)
  const timer = useRef<ReturnType<typeof setTimeout> | undefined>(undefined)
  const [position, setPosition] = useState<{ left: number; top: number; width: number } | null>(null)
  function show() {
    clearTimeout(timer.current)
    const rect = anchor.current?.getBoundingClientRect()
    if (!rect) return
    const width = Math.min(320, window.innerWidth - 32)
    setPosition({ width, left: Math.max(16, Math.min(rect.left - 80, window.innerWidth - width - 16)),
      top: Math.max(16, Math.min(rect.bottom + 8, window.innerHeight - 200)) })
  }
  function hideSoon() {
    clearTimeout(timer.current)
    timer.current = setTimeout(() => setPosition(null), 150)
  }
  useEffect(() => {
    if (!position) return
    const hide = () => setPosition(null)
    const escape = (event: KeyboardEvent) => { if (event.key === 'Escape') hide() }
    window.addEventListener('scroll', hide, true)
    window.addEventListener('resize', hide)
    window.addEventListener('keydown', escape)
    return () => {
      window.removeEventListener('scroll', hide, true)
      window.removeEventListener('resize', hide)
      window.removeEventListener('keydown', escape)
    }
  }, [position])
  useEffect(() => () => clearTimeout(timer.current), [])
  return <span className="info-hint" ref={anchor} onMouseEnter={show} onMouseLeave={hideSoon}>
    <button type="button" className="info-hint-icon" onClick={show} onFocus={show} onBlur={hideSoon}
      aria-label={language === 'zh' ? '查看说明' : 'Information'} aria-describedby={position ? id : undefined}>i</button>
    {position && createPortal(<span className="info-hint-content" id={id} role="tooltip" style={position}
      onFocus={() => clearTimeout(timer.current)} onBlur={hideSoon}
      onMouseEnter={() => clearTimeout(timer.current)} onMouseLeave={hideSoon}>{children}</span>, document.body)}
  </span>
}
