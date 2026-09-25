import { useEffect, useMemo, useRef, useState } from 'react'
import { Map as MapLibreMap, NavigationControl, Popup, setWorkerUrl, type GeoJSONSource, type MapEventType } from 'maplibre-gl'
import workerUrl from 'maplibre-gl/dist/maplibre-gl-worker.mjs?worker&url'
import type { FeatureCollection, Point } from 'geojson'
import type { AuroraMapData, AuroraMapPoint } from '../../types/auroraMap'
import type { Location } from '../../types/location'
import { localizeError, useI18n } from '../../i18n'
import 'maplibre-gl/dist/maplibre-gl.css'
import './AuroraMap.css'

setWorkerUrl(workerUrl)

function asGeoJson(data: AuroraMapData): FeatureCollection<Point, { auroraValue: number }> {
  const forecastDeadline = Date.parse(data.forecastTime)
  const forecastIsCurrent = data.status === 'CURRENT'
    && Number.isFinite(forecastDeadline)
    && Date.now() <= forecastDeadline
  return {
    type: 'FeatureCollection',
    features: (forecastIsCurrent ? data.points : []).map(point => ({
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

function asSelectedLocationGeoJson(location: Location | null): FeatureCollection<Point, { name: string; timezone: string }> {
  return {
    type: 'FeatureCollection',
    features: location ? [{
      type: 'Feature',
      geometry: { type: 'Point', coordinates: [location.longitude, location.latitude] },
      properties: { name: `${location.name}, ${location.country}`, timezone: location.timezone },
    }] : [],
  }
}

function asActivityGeoJson(points: AuroraMapPoint[]): FeatureCollection<Point, { index: number; auroraValue: number }> {
  return {
    type: 'FeatureCollection',
    features: points.map((point, index) => ({
      type: 'Feature',
      geometry: { type: 'Point', coordinates: [point.longitude, point.latitude] },
      properties: { index, auroraValue: point.auroraValue },
    })),
  }
}

function createActivityPopup(number: number, value: number, pointLabel: string, signalLabel: string) {
  const content = document.createElement('div')
  const title = document.createElement('strong')
  title.textContent = `${number}. ${pointLabel}`
  const signal = document.createElement('div')
  signal.textContent = `${signalLabel}: ${value}`
  content.append(title, signal)
  return content
}

type Props = {
  data: AuroraMapData | null
  forecastError: string
  forecastLoading: boolean
  activityPoints: AuroraMapPoint[]
  selectedActivityIndex: number | null
  onSelectActivity: (index: number) => void
  selectedLocation?: Location | null
}

const baseMaps = [
  { id: 'dataviz-dark', label: 'mapStyleDark' },
  { id: 'streets-v4', label: 'mapStyleStreets' },
  { id: 'hybrid-v4', label: 'mapStyleSatellite' },
] as const
const initialBaseMapId = baseMaps[0].id

export function AuroraMap({ data, forecastError, forecastLoading, activityPoints, selectedActivityIndex, onSelectActivity, selectedLocation = null }: Props) {
  const { language, t } = useI18n()
  const locale = language === 'zh' ? 'zh-CN' : 'en'
  const mapTilerKey = import.meta.env.VITE_MAPTILER_KEY?.trim()
  const container = useRef<HTMLDivElement>(null)
  const map = useRef<MapLibreMap | null>(null)
  const dataRef = useRef<AuroraMapData | null>(null)
  const activityPointsRef = useRef(activityPoints)
  const selectedLocationRef = useRef(selectedLocation)
  const activityPopup = useRef<Popup | null>(null)
  const selectActivityRef = useRef(onSelectActivity)
  const translateRef = useRef(t)
  const [tilesReady, setTilesReady] = useState(false)
  const [mapError, setMapError] = useState('')
  const [baseMapId, setBaseMapId] = useState<(typeof baseMaps)[number]['id']>(initialBaseMapId)
  const geoJson = useMemo(() => data ? asGeoJson(data) : null, [data])
  const error = !mapTilerKey
    ? 'Add VITE_MAPTILER_KEY to frontend/.env.local to load the map.'
    : mapError || forecastError
  const loading = Boolean(mapTilerKey) && (!tilesReady || forecastLoading) && !error

  useEffect(() => {
    dataRef.current = data
  }, [data])

  useEffect(() => {
    activityPointsRef.current = activityPoints
  }, [activityPoints])

  useEffect(() => {
    selectedLocationRef.current = selectedLocation
  }, [selectedLocation])

  useEffect(() => {
    selectActivityRef.current = onSelectActivity
  }, [onSelectActivity])

  useEffect(() => {
    translateRef.current = t
  }, [t])

  useEffect(() => {
    if (!mapTilerKey) {
      return
    }
    if (!container.current) return

    const instance = new MapLibreMap({
      container: container.current,
      style: `https://api.maptiler.com/maps/${initialBaseMapId}/style.json?key=${encodeURIComponent(mapTilerKey)}`,
      center: selectedLocationRef.current ? [selectedLocationRef.current.longitude, selectedLocationRef.current.latitude] : [0, 62],
      zoom: selectedLocationRef.current ? 3.2 : 1.15,
      minZoom: 0.6,
      maxZoom: 8,
    })
    map.current = instance
    instance.addControl(new NavigationControl({ showCompass: false }), 'top-right')

    let keyRejected = false
    let initialStyleLoaded = false
    const installAuroraLayer = () => {
      const sourceData = dataRef.current ? asGeoJson(dataRef.current) : asGeoJson({
        status: 'CURRENT', observationTime: '', forecastTime: '', retrievedAt: '', source: '', points: [],
      })
      if (!instance.getSource('ovation-grid')) {
        instance.addSource('ovation-grid', { type: 'geojson', data: sourceData })
      }
      if (!instance.getLayer('ovation-heatmap')) {
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
      }
      if (!instance.getLayer('ovation-points')) {
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
      }
      if (!instance.getSource('strong-activity-points')) {
        instance.addSource('strong-activity-points', { type: 'geojson', data: asActivityGeoJson(activityPointsRef.current) })
      }
      if (!instance.getSource('selected-location')) {
        instance.addSource('selected-location', { type: 'geojson', data: asSelectedLocationGeoJson(selectedLocationRef.current) })
      }
      if (!instance.getLayer('selected-location-circle')) {
        instance.addLayer({
          id: 'selected-location-circle',
          type: 'circle',
          source: 'selected-location',
          paint: {
            'circle-radius': 9,
            'circle-color': '#d8c5ff',
            'circle-stroke-color': '#241d31',
            'circle-stroke-width': 3,
          },
        })
      }
      if (!instance.getLayer('selected-location-label')) {
        instance.addLayer({
          id: 'selected-location-label',
          type: 'symbol',
          source: 'selected-location',
          layout: {
            'text-field': ['get', 'name'],
            'text-size': 13,
            'text-font': ['Open Sans Bold'],
            'text-offset': [0, 1.5],
            'text-allow-overlap': true,
          },
          paint: {
            'text-color': '#f4edff',
            'text-halo-color': '#241d31',
            'text-halo-width': 1.5,
          },
        })
      }
      if (!instance.getLayer('strong-activity-circles')) {
        instance.addLayer({
          id: 'strong-activity-circles',
          type: 'circle',
          source: 'strong-activity-points',
          paint: {
            'circle-radius': 10,
            'circle-color': '#a889ef',
            'circle-stroke-color': '#17131f',
            'circle-stroke-width': 2,
          },
        })
      }
      if (!instance.getLayer('strong-activity-labels')) {
        instance.addLayer({
          id: 'strong-activity-labels',
          type: 'symbol',
          source: 'strong-activity-points',
          layout: {
            'text-field': ['to-string', ['+', ['get', 'index'], 1]],
            'text-size': 12,
            'text-font': ['Open Sans Bold'],
            'text-allow-overlap': true,
          },
          paint: { 'text-color': '#17131f' },
        })
      }
    }
    const loadTimeout = window.setTimeout(() => {
      if (!keyRejected) {
        setMapError('The map could not finish loading. Check the browser console or try reloading the page.')
      }
    }, 20_000)
    instance.once('load', () => {
      initialStyleLoaded = true
      window.clearTimeout(loadTimeout)
      setMapError('')
      installAuroraLayer()
      instance.on('click', 'strong-activity-circles', event => {
        const feature = event.features?.[0]
        const coordinates = feature?.geometry.type === 'Point' ? feature.geometry.coordinates : null
        const properties = feature?.properties as { index?: number; auroraValue?: number } | undefined
        if (!coordinates || properties?.index === undefined) return
        activityPopup.current?.remove()
        selectActivityRef.current(properties.index)
        activityPopup.current = new Popup({ closeButton: true, closeOnClick: true })
          .setLngLat(coordinates as [number, number])
          .setDOMContent(createActivityPopup(properties.index + 1, properties.auroraValue ?? 0, translateRef.current('modelGridPoint'), translateRef.current('activityValue')))
          .addTo(instance)
      })
      instance.on('click', 'selected-location-circle', event => {
        const feature = event.features?.[0]
        const coordinates = feature?.geometry.type === 'Point' ? feature.geometry.coordinates : null
        const properties = feature?.properties as { name?: string; timezone?: string } | undefined
        if (!coordinates || !properties) return
        const content = document.createElement('div')
        const name = document.createElement('strong')
        name.textContent = properties.name ?? translateRef.current('selectedPlaceLabel')
        const timezone = document.createElement('div')
        timezone.textContent = properties.timezone ?? ''
        content.append(name, timezone)
        new Popup({ closeButton: true, closeOnClick: true })
          .setLngLat(coordinates as [number, number])
          .setDOMContent(content)
          .addTo(instance)
      })
      instance.on('mouseenter', 'selected-location-circle', () => { instance.getCanvas().style.cursor = 'pointer' })
      instance.on('mouseleave', 'selected-location-circle', () => { instance.getCanvas().style.cursor = '' })
      instance.on('mouseenter', 'strong-activity-circles', () => { instance.getCanvas().style.cursor = 'pointer' })
      instance.on('mouseleave', 'strong-activity-circles', () => { instance.getCanvas().style.cursor = '' })
      setTilesReady(true)
    })
    instance.on('style.load', () => {
      if (!initialStyleLoaded) return
      installAuroraLayer()
      instance.once('idle', () => {
        setMapError('')
        setTilesReady(true)
      })
    })
    instance.on('error', (event: MapEventType['error']) => {
      if (event.error.message.toLowerCase().includes('401')
          || event.error.message.toLowerCase().includes('403')) {
        keyRejected = true
        setMapError('MapTiler rejected this key. Check its allowed website origins and usage quota.')
      } else if (!instance.loaded() && !keyRejected) {
        setMapError('The base map could not load. Check the browser console for the failed request.')
      }
    })
    return () => {
      window.clearTimeout(loadTimeout)
      instance.remove()
      activityPopup.current?.remove()
      activityPopup.current = null
      map.current = null
    }
  }, [mapTilerKey])

  function changeBaseMap(baseMap: (typeof baseMaps)[number]['id']) {
    setBaseMapId(baseMap)
    if (!map.current || !mapTilerKey || baseMap === baseMapId) return
    setTilesReady(false)
    map.current.setStyle(`https://api.maptiler.com/maps/${baseMap}/style.json?key=${encodeURIComponent(mapTilerKey)}`)
  }

  useEffect(() => {
    const instance = map.current
    const source = instance?.getSource('selected-location') as GeoJSONSource | undefined
    source?.setData(asSelectedLocationGeoJson(selectedLocation))
    if (selectedLocation && instance) {
      instance.flyTo({
        center: [selectedLocation.longitude, selectedLocation.latitude],
        zoom: Math.max(instance.getZoom(), 3.2),
        duration: 700,
      })
    }
  }, [selectedLocation])

  useEffect(() => {
    const instance = map.current
    const source = instance?.getSource('ovation-grid') as GeoJSONSource | undefined
    if (source) source.setData(geoJson ?? { type: 'FeatureCollection', features: [] })
  }, [geoJson])

  useEffect(() => {
    const instance = map.current
    if (!instance?.getStyle()) return
    const source = instance.getSource('strong-activity-points') as GeoJSONSource | undefined
    source?.setData(asActivityGeoJson(activityPoints))
    if (instance.getLayer('strong-activity-circles')) {
      instance.setPaintProperty('strong-activity-circles', 'circle-radius', ['case', ['==', ['get', 'index'], selectedActivityIndex ?? -1], 12, 10])
    }
    if (selectedActivityIndex !== null) {
      const point = activityPoints[selectedActivityIndex]
      if (!point) return
      activityPopup.current?.remove()
      instance.flyTo({ center: [point.longitude, point.latitude], zoom: Math.max(instance.getZoom(), 3.2), duration: 900 })
      activityPopup.current = new Popup({ closeButton: true, closeOnClick: true })
        .setLngLat([point.longitude, point.latitude])
        .setDOMContent(createActivityPopup(selectedActivityIndex + 1, point.auroraValue, t('modelGridPoint'), t('activityValue')))
        .addTo(instance)
    }
  }, [activityPoints, selectedActivityIndex, t])

  const mapLabel = selectedLocation
    ? `${t('mapAria')}: ${selectedLocation.name}, ${selectedLocation.country}`
    : t('mapAria')

  return <section className="aurora-map-panel" aria-label={mapLabel}>
    <div className="aurora-map-canvas" ref={container} />
    <label className="map-style-picker">
      <span>{t('mapStyleLabel')}</span>
      <select value={baseMapId} onChange={event => changeBaseMap(event.target.value as (typeof baseMaps)[number]['id'])} disabled={!tilesReady || !mapTilerKey || Boolean(mapError)}>
        {baseMaps.map(baseMap => <option key={baseMap.id} value={baseMap.id}>{t(baseMap.label)}</option>)}
      </select>
    </label>
    {(loading || error) && <div className="map-message" role={error ? 'alert' : 'status'}>
      {loading ? t('loading') : localizeError(new Error(error), t)}
    </div>}
    {!loading && !error && data?.status === 'EXPIRED' && <div className="map-message" role="status">{t('mapForecastExpired')}</div>}
    <div className="map-key" aria-label={t('relativeModelValue')}>
      <span>{t('modelSignal')}</span><div className="map-key-gradient" /><div className="map-key-labels"><span>{t('lower')}</span><span>{t('higher')}</span></div>
    </div>
    <a className="maptiler-logo" href="https://www.maptiler.com/" target="_blank" rel="noreferrer" aria-label="MapTiler website">
      <img src="https://api.maptiler.com/resources/logo.svg" alt="MapTiler" width="92" height="20" />
    </a>
    <div className="map-credit"><a href={data?.source ?? 'https://www.swpc.noaa.gov/products/aurora-30-minute-forecast'} target="_blank" rel="noreferrer">NOAA SWPC {language === 'zh' ? '数据' : 'data'} ↗</a></div>
    {data && <p className="map-timestamps">{t('observed')} {formatUtc(data.observationTime, locale)} · {t('forecastValid')} {formatUtc(data.forecastTime, locale)} · {t('dataRetrieved')} {formatUtc(data.retrievedAt, locale)}</p>}
  </section>
}
