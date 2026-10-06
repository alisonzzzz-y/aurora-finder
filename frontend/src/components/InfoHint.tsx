import { useId, type ReactNode } from 'react'
import { useI18n } from '../i18n'
import './InfoHint.css'

export function InfoHint({ children }: { children: ReactNode }) {
  const id = useId()
  const { language } = useI18n()
  return <span className="info-hint">
    <span className="info-hint-icon" tabIndex={0} aria-label={language === 'zh' ? '查看说明' : 'Information'} aria-describedby={id}>i</span>
    <span className="info-hint-content" id={id} role="tooltip">{children}</span>
  </span>
}
