import { useI18n } from '../../i18n'

const resources = [
  { title: 'resourceForecastTitle', copy: 'resourceForecastCopy', href: 'https://www.spaceweather.gov/products/aurora-30-minute-forecast' },
  { title: 'resourceTutorialTitle', copy: 'resourceTutorialCopy', href: 'https://www.spaceweather.gov/content/aurora-tutorial' },
  { title: 'resourceViewingTitle', copy: 'resourceViewingCopy', href: 'https://www.spaceweather.gov/content/tips-viewing-aurora' },
] as const

export function AuroraResources() {
  const { t } = useI18n()
  return <section className="aurora-resources" aria-labelledby="aurora-resources-title">
    <p className="eyebrow">{t('auroraResourcesEyebrow')}</p>
    <h2 id="aurora-resources-title">{t('auroraResourcesTitle')}</h2>
    <div className="aurora-resource-list">
      {resources.map(resource => <a
        className="aurora-resource"
        href={resource.href}
        key={resource.title}
        target="_blank"
        rel="noreferrer"
      >
        <span className="aurora-resource-title">{t(resource.title)} <span aria-hidden="true">↗</span></span>
        <span className="aurora-resource-copy">{t(resource.copy)}</span>
        <span className="aurora-resource-source">NOAA / NWS Space Weather Prediction Center</span>
      </a>)}
    </div>
  </section>
}
