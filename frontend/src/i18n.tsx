/* oxlint-disable react/only-export-components */
import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'

export type Language = 'en' | 'zh'

const messages = {
  en: {
    documentTitle: 'Aurora Finder | Aurora forecasts',
    metaDescription: 'Explore local nights and the data behind aurora viewing conditions.',
    brand: 'Aurora Finder',
    projectStatus: 'Project foundation',
    languageLabel: 'Language',
    askAboutNight: 'Ask about a night',
    askUnavailable: 'AI questions will be available after the shared facts service is validated',
    footer: 'Location search uses Open-Meteo geocoding data under CC BY 4.0. Aurora viewing advice is not available in this foundation release.',
    mapAndSearch: 'Aurora map and location search',
    globalActivity: 'GLOBAL ACTIVITY',
    auroraForecast: 'NOAA OVATION forecast',
    shortRange: 'Short range · both hemispheres',
    cityOutlooks: 'CITY OUTLOOKS',
    localConditionsTitle: 'Local conditions, when the data is ready',
    cityOutlooksNote: 'City rankings need cloud cover and local darkness as well as aurora activity. Those sources and the viewing rules are still being checked, so this page does not invent low, medium, high, or percentage ratings.',
    planNight: 'PLAN A NIGHT OUTSIDE',
    heroTitle: 'A clearer view of the northern and southern lights.',
    heroCopy: 'Follow the latest global aurora forecast, then choose a place from search results to explore its local nights. The map is a global overview and does not select locations. It shows a short-range model forecast, not a promise of what will be visible from the ground.',
    findPlace: 'Find a place',
    selectPlaceHelp: 'Select a result to confirm the place and its time zone.',
    placeNameLabel: 'City or place name',
    placePlaceholder: 'Try Dublin, Tromsø, or Dunedin',
    search: 'Search',
    loading: 'Loading…',
    matchingLocations: 'Matching locations',
    emptySearch: 'No matching places found. Try another name or spelling.',
    queryTooShort: 'Enter at least two characters to search.',
    insufficientData: 'Insufficient data',
    localOutlook: 'LOCAL OUTLOOK',
    chooseAnotherPlace: 'Choose another place',
    rulesNotValidated: 'Viewing rules not validated',
    rulesValidated: 'Viewing rules validated',
    generatedAt: 'Response generated',
    localTimeNote: 'Dates and times follow the selected place’s local time.',
    tonight: 'Tonight',
    nightNumber: 'Night ',
    highLevel: 'High outlook level',
    mediumLevel: 'Medium outlook level',
    lowLevel: 'Low outlook level',
    pendingReason: 'Aurora, cloud, darkness, and freshness rules are pending validation.',
    mapFeature: 'AURORA MAP',
    shortRangeActivity: 'Short-range activity',
    mapFeatureNote: 'The NOAA OVATION layer will appear here only when its forecast time and data freshness have been checked. The model’s aurora area is not a ground visibility boundary.',
    awaitingIntegration: 'Awaiting integration',
    sourceNotes: 'SOURCE NOTES',
    sourceNotesTitle: 'Know what supports the outlook',
    sourceNotesCopy: 'NOAA aurora and Kp forecasts, MET Norway cloud forecasts, and local darkness need separate timestamps and coverage checks. Missing data must stay visible.',
    upcomingFeatures: 'Upcoming features and sources',
    observed: 'Observed',
    forecastValid: 'Forecast valid',
    modelSignal: 'Model signal',
    lower: 'Lower',
    higher: 'Higher',
    mapAria: 'NOAA aurora forecast map',
    relativeModelValue: 'Relative NOAA model value',
    baseMapCredit: 'Base map © MapTiler',
    errorMapKey: 'Add VITE_MAPTILER_KEY to frontend/.env.local to load the map.',
    errorMapTimeout: 'The map could not finish loading. Check the browser console or try reloading the page.',
    errorMapTiler: 'MapTiler rejected this key. Check its allowed website origins and usage quota.',
    errorBaseMap: 'The base map could not load. Check the browser console for the failed request.',
    errorAuroraData: 'Aurora forecast data is unavailable right now.',
    errorLocationSearch: 'Location search is unavailable right now.',
    errorOutlook: 'The selected location could not be loaded.',
    errorGeneric: 'Something went wrong. Please try again.',
    errorNetwork: 'Could not connect to the service. Check your connection and try again.',
    errorForecastLoad: 'Aurora forecast data could not be loaded.',
    errorKpForecast: 'The global aurora forecast is unavailable right now.',
    errorSearch: 'Location search failed.',
    latestForecast: 'LATEST FORECAST',
    globalKpActivity: 'Global geomagnetic activity',
    activityLow: 'Low activity',
    activityMedium: 'Moderate activity',
    activityHigh: 'High activity',
    forecastPeriod: 'Next predicted 3-hour period',
    noUpcomingForecast: 'No upcoming NOAA Kp forecast is available.',
    dataRetrieved: 'Data retrieved',
    globalKpNote: 'Kp is a global geomagnetic index. This level describes predicted auroral activity, not visibility from a particular place or a probability.',
  },
  zh: {
    documentTitle: 'Aurora Finder｜极光预报',
    metaDescription: '查看全球极光活动地图、当地夜晚时间和预报数据来源。',
    brand: 'Aurora Finder',
    projectStatus: '项目基础版本',
    languageLabel: '语言',
    askAboutNight: '询问今晚情况',
    askUnavailable: '共享观测数据服务完成核验后，将开放 AI 问答',
    footer: '地点搜索使用 Open-Meteo 地理编码数据，遵循 CC BY 4.0。当前基础版本暂不提供极光观测建议。',
    mapAndSearch: '极光地图与地点搜索',
    globalActivity: '全球极光活动',
    auroraForecast: 'NOAA OVATION 极光预报',
    shortRange: '短时预报 · 南北半球',
    cityOutlooks: '城市观测信息',
    localConditionsTitle: '数据就绪后提供当地情况',
    cityOutlooksNote: '城市排序需要结合云量、当地黑暗时段和极光活动。目前相关数据源和观测规则仍在核验，因此页面不会编造低、中、高等级或概率。',
    planNight: '规划一次户外观测',
    heroTitle: '更清晰地了解北极光与南极光。',
    heroCopy: '查看最新的全球极光预报，再从搜索结果中选择地点，了解当地夜间情况。地图展示全球概况，不能用于选择地点；它显示的是短时模型预报，不代表地面上一定能看到极光。',
    findPlace: '查找地点',
    selectPlaceHelp: '选择一个搜索结果，以确认地点和当地时区。',
    placeNameLabel: '城市或地点名称',
    placePlaceholder: '例如 Dublin、Tromsø 或 Dunedin',
    search: '搜索',
    loading: '加载中…',
    matchingLocations: '匹配的地点',
    emptySearch: '没有找到匹配地点，请尝试其他名称或拼写。',
    queryTooShort: '请输入至少两个字符进行搜索。',
    insufficientData: '数据不足',
    localOutlook: '当地预报',
    chooseAnotherPlace: '选择其他地点',
    rulesNotValidated: '观测规则尚未验证',
    rulesValidated: '观测规则已验证',
    generatedAt: '响应生成于',
    localTimeNote: '日期和时间均按所选地点的当地时间显示。',
    tonight: '今晚',
    nightNumber: '第',
    highLevel: '观测条件：高',
    mediumLevel: '观测条件：中',
    lowLevel: '观测条件：低',
    pendingReason: '极光、云量、黑暗时段和数据时效规则仍待验证。',
    mapFeature: '极光地图',
    shortRangeActivity: '短时活动',
    mapFeatureNote: '完成预报时间和数据时效核验后，才会在此显示 NOAA OVATION 图层。模型中的极光区域不代表地面可见范围。',
    awaitingIntegration: '等待接入',
    sourceNotes: '数据来源说明',
    sourceNotesTitle: '了解预报依据',
    sourceNotesCopy: 'NOAA 极光和 Kp 预报、MET Norway 云量预报以及当地黑暗时段，需要分别核对时间和覆盖范围。缺失的数据也必须明确显示。',
    upcomingFeatures: '后续功能与数据来源',
    observed: '观测时间',
    forecastValid: '预报有效时间',
    modelSignal: '模型信号',
    lower: '较低',
    higher: '较高',
    mapAria: 'NOAA 极光预报地图',
    relativeModelValue: 'NOAA 模型相对数值',
    baseMapCredit: '底图 © MapTiler',
    errorMapKey: '请在 frontend/.env.local 中设置 VITE_MAPTILER_KEY 以加载地图。',
    errorMapTimeout: '地图未能完成加载。请检查浏览器控制台或尝试重新加载页面。',
    errorMapTiler: 'MapTiler 未接受此密钥。请检查允许的网站来源和使用额度。',
    errorBaseMap: '底图加载失败。请检查浏览器控制台中的失败请求。',
    errorAuroraData: '极光预报数据暂时不可用。',
    errorLocationSearch: '地点搜索暂时不可用。',
    errorOutlook: '无法加载所选地点的信息。',
    errorGeneric: '发生错误，请重试。',
    errorNetwork: '无法连接到服务，请检查网络后重试。',
    errorForecastLoad: '无法加载极光预报数据。',
    errorKpForecast: '全球极光预报暂时不可用。',
    errorSearch: '地点搜索失败。',
    latestForecast: '最新预报',
    globalKpActivity: '全球地磁活动',
    activityLow: '活动较弱',
    activityMedium: '中等活动',
    activityHigh: '活动较强',
    forecastPeriod: '下一个三小时预报时段',
    noUpcomingForecast: '目前没有可用的 NOAA Kp 后续预报。',
    dataRetrieved: '数据获取时间',
    globalKpNote: 'Kp 是全球地磁活动指数。此等级描述的是预报的极光活动，不代表某个地点实际可见，也不是观测概率。',
  },
} as const

type TranslationKey = keyof typeof messages.en
type I18nValue = {
  language: Language
  setLanguage: (language: Language) => void
  t: (key: TranslationKey) => string
}

const I18nContext = createContext<I18nValue | null>(null)

function getInitialLanguage(): Language {
  try {
    return localStorage.getItem('aurora-language') === 'zh' ? 'zh' : 'en'
  } catch {
    return 'en'
  }
}

export function I18nProvider({ children }: { children: ReactNode }) {
  const [language, setLanguage] = useState<Language>(getInitialLanguage)

  useEffect(() => {
    document.documentElement.lang = language === 'zh' ? 'zh-CN' : 'en'
    document.title = messages[language].documentTitle
    document.querySelector('meta[name="description"]')?.setAttribute('content', messages[language].metaDescription)
    try {
      localStorage.setItem('aurora-language', language)
    } catch {
      // The selected language still works for this session if storage is unavailable.
    }
  }, [language])

  const value = useMemo<I18nValue>(() => ({
    language,
    setLanguage,
    t: key => messages[language][key],
  }), [language])

  return <I18nContext.Provider value={value}>{children}</I18nContext.Provider>
}

export function useI18n() {
  const value = useContext(I18nContext)
  if (!value) throw new Error('useI18n must be used inside I18nProvider')
  return value
}

const errorKeys: Record<string, TranslationKey> = {
  'Add VITE_MAPTILER_KEY to frontend/.env.local to load the map.': 'errorMapKey',
  'The map could not finish loading. Check the browser console or try reloading the page.': 'errorMapTimeout',
  'MapTiler rejected this key. Check its allowed website origins and usage quota.': 'errorMapTiler',
  'The base map could not load. Check the browser console for the failed request.': 'errorBaseMap',
  'Aurora forecast data is unavailable right now.': 'errorAuroraData',
  'Location search is unavailable right now.': 'errorLocationSearch',
  'The selected location could not be loaded.': 'errorOutlook',
  'Aurora forecast data could not be loaded.': 'errorForecastLoad',
  'The global aurora forecast is unavailable right now.': 'errorKpForecast',
  'Location search failed.': 'errorSearch',
}

export function localizeError(error: unknown, t: I18nValue['t']) {
  if (!(error instanceof Error)) return t('errorGeneric')
  if (error instanceof TypeError || ['Failed to fetch', 'Load failed', 'NetworkError when attempting to fetch resource.'].includes(error.message)) {
    return t('errorNetwork')
  }
  return errorKeys[error.message] ? t(errorKeys[error.message]) : error.message
}

export function localizeReason(reason: string, t: I18nValue['t']) {
  if (reason === 'Aurora, cloud, darkness, and freshness rules are pending validation.') {
    return t('pendingReason')
  }
  return reason
}
