import { useI18n } from '../../i18n'

export function ForecastGuide() {
  const { t } = useI18n()
  return <section className="forecast-guide" aria-labelledby="forecast-guide-title">
    <p className="eyebrow">{t('forecastGuideEyebrow')}</p>
    <h2 id="forecast-guide-title">{t('forecastGuideTitle')}</h2>
    <div className="forecast-guide-grid">
      <article>
        <h3>{t('forecastGuideGlobalTitle')}</h3>
        <p>{t('forecastGuideGlobalCopy')}</p>
      </article>
      <article>
        <h3>{t('forecastGuideLocalTitle')}</h3>
        <p>{t('forecastGuideLocalCopy')}</p>
      </article>
      <article>
        <h3>{t('forecastGuideLimitsTitle')}</h3>
        <p>{t('forecastGuideLimitsCopy')}</p>
      </article>
    </div>
  </section>
}
