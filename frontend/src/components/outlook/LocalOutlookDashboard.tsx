import { InfoHint } from '../InfoHint'
import { ViewingConditionsSummary } from './ViewingConditionsSummary'
import { useState } from 'react'
import type { ObservationFacts } from '../../types/observationFacts'
import { useI18n } from '../../i18n'
import { CloudForecastCard } from '../weather/CloudForecastCard'
import { LocalAuroraActivityCard } from '../aurora/LocalAuroraActivityCard'
import { NightOutlookCard } from './NightOutlookCard'
import { NightDarknessChart } from './NightDarknessChart'
import './VisualOutlook.css'

export function LocalOutlookDashboard({ facts }: { facts: ObservationFacts }) {
  const { language, t } = useI18n()
  const [selectedIndex, setSelectedIndex] = useState(0)
  const { outlook } = facts
  const night = outlook.nights[selectedIndex] ?? outlook.nights[0]
  const activity = facts.auroraActivity
  const value = activity.status === 'CURRENT' && activity.data?.status === 'CURRENT' ? activity.data.modelValue : null
  const locale = language === 'zh' ? 'zh-CN' : 'en'
  const generatedAt = new Intl.DateTimeFormat(locale, { dateStyle: 'medium', timeStyle: 'short', timeZone: outlook.location.timezone }).format(new Date(facts.generatedAtUtc))
  return <section className="selected-location-outlook" aria-live="polite">
    <div className="selected-location-heading"><h2>{outlook.location.name}, {outlook.location.country}<InfoHint>{t(outlook.ruleStatus === 'VALIDATED' ? 'rulesValidated' : 'rulesNotValidatedShort')}</InfoHint></h2><p>{outlook.location.timezone}</p></div>
    <ViewingConditionsSummary facts={facts} />
    <div className="local-summary-strip">
      <div><span>{t('nearbyActivityShort')}</span><strong>{value === null ? t('insufficientData') : t(value < 18 ? 'localActivityLow' : value < 50 ? 'localActivityMedium' : 'localActivityHigh')}</strong></div>
      <div><span>{t('dataStatusShort')}</span><strong>{t(facts.sourceStatus === 'CURRENT' ? 'sourcesCurrentShort' : facts.sourceStatus === 'PARTIAL' ? 'sourcesPartialShort' : 'sourceUnavailable')}</strong></div>
    </div>
    {night && <>
      <div className="local-night-switch" role="group" aria-label={t('chooseNight')}>
        {outlook.nights.map((item, index) => <button type="button" key={item.localDate} aria-pressed={night.localDate === item.localDate} onClick={() => setSelectedIndex(index)}>{index === 0 ? t('tonight') : `${t('nightNumber')}${index + 1}${language === 'zh' ? '晚' : ''}`}</button>)}
      </div>
      <CloudForecastCard fact={facts.cloudForecast} timezone={outlook.location.timezone} night={night} auroraFact={activity} />
      <NightDarknessChart nights={outlook.nights} timezone={outlook.location.timezone} selectedIndex={selectedIndex} onSelect={setSelectedIndex} />
    </>}
    <LocalAuroraActivityCard fact={activity} timezone={outlook.location.timezone} />
    <details className="visual-details"><summary>{t('nightDetails')}</summary>{night && <NightOutlookCard night={night} index={selectedIndex} timezone={outlook.location.timezone} />}<p className="timestamp">{t('generatedAt')} {generatedAt}. {t('localTimeNote')}</p></details>
  </section>
}
