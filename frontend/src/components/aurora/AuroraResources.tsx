import { InfoHint } from '../InfoHint'
import { useI18n } from '../../i18n'

const resources = [
  { title: 'resourceForecastTitle', copy: 'resourceForecastCopy', href: 'https://www.spaceweather.gov/products/aurora-30-minute-forecast' },
  { title: 'resourceTutorialTitle', copy: 'resourceTutorialCopy', href: 'https://www.spaceweather.gov/content/aurora-tutorial' },
  { title: 'resourceViewingTitle', copy: 'resourceViewingCopy', href: 'https://www.spaceweather.gov/content/tips-viewing-aurora' },
] as const

export function AuroraResources() {
  const { t } = useI18n()
  return <section className="aurora-resources" aria-labelledby="aurora-resources-title">

    <h2 id="aurora-resources-title">{t('auroraResourcesTitle')}</h2>
    <div className="aurora-resource-list">
      {resources.map(resource => <article
        className="aurora-resource"
        key={resource.title}
      >
        <div className="aurora-resource-title"><a href={resource.href} target="_blank" rel="noreferrer">{t(resource.title)} ↗</a><InfoHint>{t(resource.copy)}</InfoHint></div>
      </article>)}
    </div>
    <p className="forecast-source-note">{t('noaaSource')}</p>
  </section>
}
