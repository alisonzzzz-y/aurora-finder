/* oxlint-disable react/only-export-components */
import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'

export type Language = 'en' | 'zh'

const messages = {
  en: {
    documentTitle: 'Aurora Finder | Aurora forecasts',
    metaDescription: 'Explore local nights and the data behind aurora viewing conditions.',
    brand: 'Aurora Finder',
    languageLabel: 'Language',
    askAboutNight: 'Ask AI about Aurora tonight',
    askUnavailable: 'AI questions are not available yet.',
    footer: 'Location search uses Open-Meteo geocoding data under CC BY 4.0. Aurora viewing advice is not available in this foundation release.',
    mapAndSearch: 'Aurora map and location search',
    globalActivity: 'GLOBAL ACTIVITY ·',
    auroraForecast: 'Aurora forecast',
    shortRange: 'Short-range forecast · Northern and Southern Hemispheres',
    currentAreasLabel: 'STRONGEST CURRENT AREAS',
    currentAreasTitle: 'Where activity is strongest now',
    currentAreasNote: 'These are the strongest points in the current aurora model grid. They are coordinates, not city forecasts or chances of seeing aurora from the ground.',
    activityAreasLoading: 'Loading current activity areas…',
    activityAreasUnavailable: 'Current activity areas are unavailable right now.',
    activityAreasExpired: 'The latest model data has expired, so current areas are not shown.',
    modelGridPoint: 'Model grid point',
    activityValue: 'Model signal',
    forecastGuideEyebrow: 'FORECAST GUIDE',
    forecastGuideTitle: 'How to read the data',
    forecastGuideGlobalTitle: 'Global activity',
    forecastGuideGlobalCopy: 'Kp describes geomagnetic activity worldwide. The summary above shows the next predicted three-hour period; it is not a forecast for one particular city.',
    forecastGuideLocalTitle: 'Local map signal',
    forecastGuideLocalCopy: 'The local level uses the nearest 1° model grid point, usually 30–90 minutes ahead. Values below 18 are low, 18–49 moderate, and 50 or higher high.',
    forecastGuideLimitsTitle: 'Seeing the aurora',
    forecastGuideLimitsCopy: 'Model activity is not a viewing probability. Clouds, darkness, and local conditions also affect whether the aurora can be seen.',
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
    mapAria: 'Aurora forecast map',
    relativeModelValue: 'Relative model value',
    baseMapCredit: 'Base map © MapTiler',
    errorMapKey: 'Add VITE_MAPTILER_KEY to frontend/.env.local to load the map.',
    errorMapTimeout: 'The map could not finish loading. Check the browser console or try reloading the page.',
    errorMapTiler: 'MapTiler returned 403. Add aurora-finder.vercel.app to this key’s Allowed HTTP origins (domain only, without https://), then confirm the key is active and the account quota is available.',
    errorBaseMap: 'The base map could not load. Check the browser console for the failed request.',
    errorAuroraData: 'Aurora forecast data is unavailable right now.',
    errorLocationSearch: 'Location search is unavailable right now.',
    errorOutlook: 'The selected location could not be loaded.',
    errorGeneric: 'Something went wrong. Please try again.',
    errorNetwork: 'Could not connect to the service. Check your connection and try again.',
    errorForecastLoad: 'Aurora forecast data could not be loaded.',
    mapForecastExpired: 'The NOAA forecast has passed its valid time. The old aurora layer has been removed.',
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
    localAuroraForecast: 'LOCAL NOAA FORECAST',
    localActivityTitle: 'Aurora activity near this place',
    localActivityLow: 'Low activity',
    localActivityMedium: 'Moderate activity',
    localActivityHigh: 'High activity',
    noaaGridValue: 'Nearest NOAA grid value',
    localAuroraNote: 'A short-range model estimate for the nearest 1° grid cell, usually about 30–90 minutes ahead. It is not the probability that you will see aurora from the ground. Level rule: below 18 is low, 18–49 is moderate, and 50 or above is high.',
    localAuroraExpiredNote: 'This NOAA forecast has passed its valid time. No current local activity level is available.',
    cloudForecast: 'LOCAL CLOUD FORECAST',
    cloudForecastTitle: 'Cloud cover for this place',
    cloudMissing: 'Unavailable',
    cloudNoCoverage: 'The source returned no cloud forecast points for this local night.',
    forecastCacheExpires: 'Cache valid until',
    cloudForecastNote: 'Forecast timestamps and gaps follow the source model output. Missing cloud values stay unavailable and are not treated as clear skies. Cloud cover is one viewing condition, not an aurora visibility estimate.',
    metNoAttribution: 'Weather forecast by MET Norway',
    metNoChanges: 'Data is filtered to this local night; cloud values are unchanged.',
    errorWeatherForecast: 'The local cloud forecast is unavailable right now.',
    apiOriginMissing: 'This deployment has no backend API address configured. Set VITE_API_BASE_URL in Vercel to the deployed Spring Boot API origin.',
  },
  zh: {
    documentTitle: 'Aurora Finder｜极光预报',
    metaDescription: '查看全球极光活动地图、当地夜晚时间和预报数据来源。',
    brand: 'Aurora Finder',
    languageLabel: '语言',
    askAboutNight: '询问 AI 今晚极光情况',
    askUnavailable: 'AI 问答功能暂未开放。',
    footer: '地点搜索使用 Open-Meteo 地理编码数据，遵循 CC BY 4.0。当前基础版本暂不提供极光观测建议。',
    mapAndSearch: '极光地图与地点搜索',
    globalActivity: '全球极光活动 ·',
    auroraForecast: '极光预报',
    shortRange: '短时预报 · 南北半球',
    currentAreasLabel: '当前活动较强区域',
    currentAreasTitle: '此刻哪些区域活动较强',
    currentAreasNote: '以下是当前极光模型网格中信号较强的位置坐标，不代表城市预报，也不等于地面观测概率。',
    activityAreasLoading: '正在加载当前活动区域…',
    activityAreasUnavailable: '当前活动区域暂时无法获取。',
    activityAreasExpired: '最新模型数据已过期，暂不显示当前活动区域。',
    modelGridPoint: '模型网格位置',
    activityValue: '模型信号',
    forecastGuideEyebrow: '预报说明',
    forecastGuideTitle: '如何理解这些数据',
    forecastGuideGlobalTitle: '全球活动',
    forecastGuideGlobalCopy: 'Kp 描述全球地磁活动。上方摘要显示下一个三小时预报时段，不代表某个具体城市的预报。',
    forecastGuideLocalTitle: '当地地图信号',
    forecastGuideLocalCopy: '当地等级取自最近的 1° 模型网格点，通常预报未来约 30–90 分钟。数值低于 18 为低，18–49 为中，50 及以上为高。',
    forecastGuideLimitsTitle: '能否看到极光',
    forecastGuideLimitsCopy: '模型活动等级不是观测概率。云量、黑暗时段和当地环境也会影响能否看到极光。',
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
    mapAria: '极光预报地图',
    relativeModelValue: '模型相对数值',
    baseMapCredit: '底图 © MapTiler',
    errorMapKey: '请在 frontend/.env.local 中设置 VITE_MAPTILER_KEY 以加载地图。',
    errorMapTimeout: '地图未能完成加载。请检查浏览器控制台或尝试重新加载页面。',
    errorMapTiler: 'MapTiler 返回 403。请在该密钥的 Allowed HTTP origins 中添加 aurora-finder.vercel.app（只填域名，不带 https://），再确认密钥有效且账户用量未超限。',
    errorBaseMap: '底图加载失败。请检查浏览器控制台中的失败请求。',
    errorAuroraData: '极光预报数据暂时不可用。',
    errorLocationSearch: '地点搜索暂时不可用。',
    errorOutlook: '无法加载所选地点的信息。',
    errorGeneric: '发生错误，请重试。',
    errorNetwork: '无法连接到服务，请检查网络后重试。',
    errorForecastLoad: '无法加载极光预报数据。',
    mapForecastExpired: 'NOAA 预报已超过有效时间，旧极光图层已移除。',
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
    localAuroraForecast: 'NOAA 当地预报',
    localActivityTitle: '该地点附近的极光活动',
    localActivityLow: '活动较弱',
    localActivityMedium: '中等活动',
    localActivityHigh: '活动较强',
    noaaGridValue: '最近 NOAA 网格值',
    localAuroraNote: '这是离所选地点最近的 1° 网格点短时模型估计，预报通常提前约 30–90 分钟。它不代表人在地面看到极光的概率。分级规则：低于 18 为低，18–49 为中，50 及以上为高。',
    localAuroraExpiredNote: '这份 NOAA 预报已超过有效时间，目前没有可用的当地活动等级。',
    cloudForecast: '当地云量预报',
    cloudForecastTitle: '该地点的云量情况',
    cloudMissing: '暂无数据',
    cloudNoCoverage: '来源没有覆盖这个当地夜晚的云量预报。',
    forecastCacheExpires: '缓存有效至',
    cloudForecastNote: '预报时间和间隔遵循来源模型的输出。缺失的云量数据会保留为暂无数据，不会当成晴空。云量只是观测条件之一，不是极光可见性估计。',
    metNoAttribution: '天气预报来源：MET Norway',
    metNoChanges: '数据按当地今晚筛选，云量数值未修改。',
    errorWeatherForecast: '暂时无法获取当地云量预报。',
    apiOriginMissing: '当前部署尚未配置后端 API 地址。请在 Vercel 中将 VITE_API_BASE_URL 设置为已部署的 Spring Boot API 根地址。',
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
  'Local aurora activity is unavailable right now.': 'errorAuroraData',
  'Local cloud forecast is unavailable right now.': 'errorWeatherForecast',
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
