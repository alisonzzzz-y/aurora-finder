import { useEffect, useMemo, useRef, useState } from 'react'
import { Map as MapLibreMap, NavigationControl, setWorkerUrl, type GeoJSONSource, type MapEventType } from 'maplibre-gl'
import workerUrl from 'maplibre-gl/dist/maplibre-gl-worker.mjs?worker&url'
import type { FeatureCollection, Point } from 'geojson'
import { getAuroraMap } from '../../api/auroraMap'
import type { AuroraMapData } from '../../types/auroraMap'
import { localizeError, useI18n } from '../../i18n'
import 'maplibre-gl/dist/maplibre-gl.css'
import './AuroraMap.css'

setWorkerUrl(workerUrl)

function asGeoJson(data: AuroraMapData): FeatureCollection<Point, { auroraValue: number }> {
  return {
    type: 'FeatureCollection',
    features: data.points.map(point => ({
      type: 'Feature',
      geometry: { type: 'Point', coordinates: [point.longitude, point.latitude] },
      properties: { auroraValue: point.auroraValue },
    })),
  }
}

function formatUtc(instant: string, locale: string) {
  return `${new Intl.DateTimeFormat(locale, {
    dateStyle: 'medium', timeStyle: 'short', timeZone: 'UTC',
  }).format(new Date(instant))} UTC`
}

export function AuroraMap() {
  const { language, t } = useI18n()
  const locale = language === 'zh' ? 'zh-CN' : 'en'
  const mapTilerKey = import.meta.env.VITE_MAPTILER_KEY?.trim()
  const container = useRef<HTMLDivElement>(null)
  const map = useRef<MapLibreMap | null>(null)
  const dataRef = useRef<AuroraMapData | null>(null)
  const [data, setData] = useState<AuroraMapData | null>(null)
  const [tilesReady, setTilesReady] = useState(false)
  const [mapError, setMapError] = useState('')
  const [forecastError, setForecastError] = useState('')
  const geoJson = useMemo(() => data ? asGeoJson(data) : null, [data])
  const error = !mapTilerKey
    ? 'Add VITE_MAPTILER_KEY to frontend/.env.local to load the map.'
    : mapError || forecastError
  const loading = Boolean(mapTilerKey) && (!tilesReady || !data) && !error

  useEffect(() => {
    dataRef.current = data
  }, [data])

  useEffect(() => {
    if (!mapTilerKey) {
      return
    }
    if (!container.current) return

    const instance = new MapLibreMap({
      container: container.current,
      style: `https://api.maptiler.com/maps/dataviz-dark/style.json?key=${encodeURIComponent(mapTilerKey)}`,
      center: [0, 62],
      zoom: 1.15,
      minZoom: 0.6,
      maxZoom: 8,
    })
    map.current = instance
    instance.addControl(new NavigationControl({ showCompass: false }), 'top-right')

    const loadTimeout = window.setTimeout(() => {
      setMapError('The map could not finish loading. Check the browser console or try reloading the page.')
    }, 20_000)
    instance.once('load', () => {
      window.clearTimeout(loadTimeout)
      setMapError('')
      const sourceData = dataRef.current ? asGeoJson(dataRef.current) : asGeoJson({
        observationTime: '', forecastTime: '', source: '', points: [],
      })
      instance.addSource('ovation-grid', { type: 'geojson', data: sourceData })
      instance.addLayer({
        id: 'ovation-heatmap',
        type: 'heatmap',
        source: 'ovation-grid',
        maxzoom: 7,
        paint: {
          'heatmap-weight': ['interpolate', ['linear'], ['get', 'auroraValue'], 0, 0, 25, 0.35, 100, 1],
          'heatmap-intensity': ['interpolate', ['linear'], ['zoom'], 0, 0.8, 7, 2.2],
          'heatmap-radius': ['interpolate', ['linear'], ['zoom'], 0, 9, 7, 24],
          'heatmap-opacity': 0.82,
          'heatmap-color': ['interpolate', ['linear'], ['heatmap-density'],
            0, 'rgba(32, 210, 157, 0)',
            0.18, 'rgba(32, 210, 157, 0.34)',
            0.42, 'rgba(73, 235, 174, 0.72)',
            0.68, 'rgba(225, 234, 107, 0.85)',
            1, 'rgba(255, 135, 94, 0.92)',
          ],
        },
      })
      instance.addLayer({
        id: 'ovation-points',
        type: 'circle',
        source: 'ovation-grid',
        minzoom: 5,
        paint: {
          'circle-radius': ['interpolate', ['linear'], ['zoom'], 5, 2, 8, 5],
          'circle-color': ['interpolate', ['linear'], ['get', 'auroraValue'],
            1, '#20d29d', 25, '#49ebae', 60, '#e1ea6b', 100, '#ff875e',
          ],
          'circle-opacity': 0.8,
          'circle-blur': 0.35,
        },
      })
      setTilesReady(true)
    })
    instance.on('error', (event: MapEventType['error']) => {
      if (event.error.message.toLowerCase().includes('401')
          || event.error.message.toLowerCase().includes('403')) {
        setMapError('MapTiler rejected this key. Check its allowed website origins and usage quota.')
      } else if (!instance.loaded()) {
        setMapError('The base map could not load. Check the browser console for the failed request.')
      }
    })

    return () => {
      window.clearTimeout(loadTimeout)
      instance.remove()
      map.current = null
    }
  }, [mapTilerKey])

  useEffect(() => {
    if (!mapTilerKey) return
    let controller: AbortController | null = null
    async function refreshForecast() {
      controller?.abort()
      const currentController = new AbortController()
      controller = currentController
      try {
        setData(await getAuroraMap(currentController.signal))
        setForecastError('')
      } catch (cause) {
        if (currentController.signal.aborted) return
        setData(null)
        setForecastError(cause instanceof Error ? cause.message : 'Aurora forecast data could not be loaded.')
      }
    }
    void refreshForecast()
    const timer = window.setInterval(() => { void refreshForecast() }, 5 * 60 * 1000)
    return () => {
      window.clearInterval(timer)
      controller?.abort()
    }
  }, [mapTilerKey])

  useEffect(() => {
    const instance = map.current
    const source = instance?.getSource('ovation-grid') as GeoJSONSource | undefined
    if (source) source.setData(geoJson ?? { type: 'FeatureCollection', features: [] })
  }, [geoJson])

  return <section className="aurora-map-panel" aria-label={t('mapAria')}>
    <div className="aurora-map-canvas" ref={container} />
    {(loading || error) && <div className="map-message" role={error ? 'alert' : 'status'}>
      {loading ? t('loading') : localizeError(new Error(error), t)}
    </div>}
    <div className="map-key" aria-label={t('relativeModelValue')}>
      <span>{t('modelSignal')}</span><div className="map-key-gradient" /><div className="map-key-labels"><span>{t('lower')}</span><span>{t('higher')}</span></div>
    </div>
    <div className="map-credit">{t('baseMapCredit')} · <a href={data?.source ?? 'https://www.swpc.noaa.gov/products/aurora-30-minute-forecast'} target="_blank" rel="noreferrer">NOAA SWPC {language === 'zh' ? '数据' : 'data'} ↗</a></div>
    {data && <p className="map-timestamps">{t('observed')} {formatUtc(data.observationTime, locale)} · {t('forecastValid')} {formatUtc(data.forecastTime, locale)}</p>}
  </section>
}
