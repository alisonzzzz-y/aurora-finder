import { useEffect, useState } from 'react'
import { useI18n } from '../../i18n'
import { apiUrl } from '../../api/apiUrl'
import { fetchJson } from '../../api/fetchJson'
import type { ObservationFacts } from '../../types/observationFacts'
import { InfoHint } from '../InfoHint'
import { EvidenceChart, type EvidencePoint } from './EvidenceChart'
import './MonitoringSections.css'

type Series = { id: string; unit: string; status: string; points: EvidencePoint[] }
type Solar = { retrievedAt: string; sourceUrl: string; series: Series[] }
type Regional = { status: string; station: string; updatedAt: string | null; alert: string | null; color: string | null; attribution: string; license: string; sourceUrl: string; points: EvidencePoint[] }
type Snapshot = { locationId: number; name: string; timezone: string; capturedAt: string; modelValue: number | null; auroraStatus: string; auroraObservedAt: string | null; auroraForecastAt: string | null; cloudPercent: number | null; dark: boolean | null; skyLevel: string; sampleAt: string | null; validUntil: string | null; ruleVersion: string; cloudStatus: string; cloudRetrievedAt: string | null }
type History = { status: string; retentionDays: number; snapshots: Snapshot[] }
const copy = {
  zh: {
    regional: '区域地磁监测', regionHelp: '来自 AuroraWatch UK 当前告警测站的小时活动值，单位 nT。预警颜色由原站定义；测站位于英国，不代表所选地点的可见概率。变化只比较最近两个小时读数，最新一小时可能尚未结束。',
    solar: '实时太阳风', solarHelp: '显示 NOAA 标记为当前使用、质量标记正常的卫星测量。Bz 是磁场南北分量，负值表示向南；Bt 是磁场总强度。太阳风尚需传播到地球，这些读数不等于当地看到极光的概率。缺测时曲线留空。',
    speed: '太阳风速度', Bz: '磁场南北分量 Bz', Bt: '磁场总强度 Bt', loading: '正在读取数据…', unavailable: '暂时没有可用数据', stale: '数据已过期', source: '来源', rising: '比上一小时增强', falling: '比上一小时减弱', steady: '与上一小时持平',
    cameras: '极光摄像头', cameraHelp: '点击开始查看。视频使用播放器内的播放、暂停和全屏控件；Poker Flat 为原站更新画面。直播可能离线或播放回放，请以播放器状态和拍摄时间为准。相机画面可能比肉眼更敏感；远处观测站出现极光，不代表你所在地点也能看到。这里不自动判断画面中是否有极光。', cameraTime: '拍摄时间以原站画面为准', view: '查看原站画面 ↗',
    history: '历史回看与核对', historyHelp: '保存最近 7 天的城市预报快照，每个地点每 15 分钟最多一份。都柏林、Carlow 和雷克雅未克定时采样，其他地点在查询时记录。服务休眠或数据故障会留下空白。上线前的时段没有记录。这里回看的是当时的预测，不是已确认的极光实况。', choose: '回看地点', snapshot: '快照时间', empty: '这个地点还没有保存的快照。查询后将开始记录。', disabled: '历史存储尚未启用', model: '当地模型读数', cloud: '预报云量', sky: '天空条件', bright: '天色不够暗', dark: '天色较暗', high: '高', medium: '中', low: '低', unknown: '数据不足',
    review: '核对实际观测', reviewHelp: '选择一份快照，再填写对应时间和地点的真实报告。导出会保留原始快照及你的备注，不会自动认定报告可信，也不会修改观测概率。', report: '报告结果', none: '尚未核实', seen: '报告看到了极光', notSeen: '报告没有看到极光', method: '观测方式', eye: '肉眼', camera: '相机', unspecified: '未说明', reportTime: '实际观测时间（UTC）', link: '报告链接或核对备注', export: '导出核对记录', saved: '原始快照', available: '已有快照', green: '绿色预警', yellow: '黄色预警', amber: '琥珀色预警', red: '红色预警',
  },
  en: {
    regional: 'Regional magnetic activity', regionHelp: 'Hourly activity from the current AuroraWatch UK alerting station, in nT. Alert colours follow the source. This UK station does not give a viewing probability for your location. The change compares the latest two hourly readings; the current hour may be incomplete.',
    solar: 'Live solar wind', solarHelp: 'NOAA satellite measurements marked active with a good quality flag. Bz is the north-south magnetic component; negative values point south. Bt is total magnetic strength. Solar wind still needs to travel to Earth. These readings do not give a local viewing probability. Missing data leaves gaps.',
    speed: 'Solar wind speed', Bz: 'North-south field Bz', Bt: 'Total field Bt', loading: 'Loading data…', unavailable: 'No data available right now', stale: 'Out of date', source: 'Source', rising: 'Higher than the previous hour', falling: 'Lower than the previous hour', steady: 'Unchanged from the previous hour',
    cameras: 'Aurora cameras', cameraHelp: 'Select Start viewing. Use the video player controls to play, pause or go fullscreen. Poker Flat shows the original camera page. Feeds may be offline or show replays; check the player status and capture time. Cameras may detect more than the naked eye. Aurora at a distant station does not confirm visibility at your location. We do not automatically classify the images.', cameraTime: 'Check capture times on the source image', view: 'Open camera website ↗',
    history: 'History and observation review', historyHelp: 'City forecast snapshots are kept for 7 days, at most one per 15 minutes. Dublin, Carlow and Reykjavik are sampled regularly; other places are recorded when searched. Service sleep or data failures leave gaps. There are no records from before this feature launched. These are past forecasts, not confirmed sightings.', choose: 'Review location', snapshot: 'Snapshot time', empty: 'No saved snapshots for this place yet. Searching will start recording them.', disabled: 'History storage is not enabled yet', model: 'Local model reading', cloud: 'Forecast cloud', sky: 'Sky conditions', bright: 'Not dark enough', dark: 'Dark', high: 'High', medium: 'Medium', low: 'Low', unknown: 'Not enough data',
    review: 'Review an observation', reviewHelp: 'Choose a snapshot and add a report for the matching place and time. The export preserves the original snapshot and your notes. It does not verify the report or change viewing probabilities.', report: 'Reported result', none: 'Not verified', seen: 'Aurora reported', notSeen: 'No aurora reported', method: 'Viewing method', eye: 'Naked eye', camera: 'Camera', unspecified: 'Not specified', reportTime: 'Observation time (UTC)', link: 'Report link or review notes', export: 'Export review', saved: 'Original snapshot', available: 'Saved snapshots', green: 'Green alert', yellow: 'Yellow alert', amber: 'Amber alert', red: 'Red alert',
  },
}
function useMonitoring<T>(path: string, revision = '') {
  const [result, setResult] = useState<{ path: string; data: T | null; failed: boolean }>({ path, data: null, failed: false })
  useEffect(() => {
    const controller = new AbortController()
    let timer: ReturnType<typeof setTimeout>
    async function load() {
      try { const data = await fetchJson<T>(apiUrl(path), { signal: controller.signal }); if (!controller.signal.aborted) setResult({ path, data, failed: false }) }
      catch { if (!controller.signal.aborted) setResult({ path, data: null, failed: true }) }
      if (!controller.signal.aborted) timer = setTimeout(load, 180000)
    }
    void load()
    return () => { controller.abort(); clearTimeout(timer) }
  }, [path, revision])
  return result.path === path ? result : { path, data: null, failed: false }
}
export function MonitoringSections({ facts }: { facts: ObservationFacts | null }) {
  const { language } = useI18n()
  const c = copy[language]
  const locale = language === 'zh' ? 'zh-CN' : 'en'
  const solar = useMonitoring<Solar>('/api/v1/solar-wind')
  const regional = useMonitoring<Regional>('/api/v1/regional-activity')
  const r = regional.data
  const last = r?.points.at(-1), previous = r?.points.at(-2)
  const recentPair = last && previous && Date.parse(last.at) - Date.parse(previous.at) <= 7200000
  const time = (at: string) => new Intl.DateTimeFormat(locale, { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit', timeZone: 'UTC' }).format(new Date(at)) + ' UTC'
  return <>
    <section className="home-section-band home-section-band-odd monitoring-section">
      <h2>{c.regional}<InfoHint>{c.regionHelp}</InfoHint></h2>
      {!r ? <p role="status">{regional.failed ? c.unavailable : c.loading}</p> : r.status === 'UNAVAILABLE' ? <p>{c.unavailable}</p> : <>
        <div className="monitoring-headline"><strong>{r.station}</strong><span style={{ color: r.color ?? undefined }}>{r.status === 'STALE' ? c.stale : c[r.alert as 'green' | 'yellow' | 'amber' | 'red'] ?? r.alert}</span></div>
        {r.status === 'CURRENT' && recentPair && <p>{last.value > previous.value ? c.rising : last.value < previous.value ? c.falling : c.steady}</p>}
        <EvidenceChart points={r.points} unit="nT" label={c.regional} locale={locale} maxGapMinutes={90} />
        <p className="monitoring-source">{c.source}: <a href={r.sourceUrl} target="_blank" rel="noreferrer">AuroraWatch UK ↗</a><InfoHint>{r.attribution} · {r.license}{r.updatedAt && ` · ${time(r.updatedAt)}`}</InfoHint></p>
      </>}
    </section>
    <section className="home-section-band home-section-band-even monitoring-section">
      <h2>{c.solar}<InfoHint>{c.solarHelp}</InfoHint></h2>
      {!solar.data ? <p role="status">{solar.failed ? c.unavailable : c.loading}</p> : <>
        <div className="monitoring-plots">{solar.data.series.map(series => <article key={series.id}>
          <h3>{c[series.id as 'Bz' | 'Bt' | 'speed']}</h3>
          <strong className="monitoring-value">{series.status !== 'CURRENT' ? series.status === 'STALE' ? c.stale : c.unavailable : `${series.points.at(-1)?.value.toFixed(2)} ${series.unit}`}</strong>
          {series.points.length > 0 && <EvidenceChart points={series.points} unit={series.unit} label={c[series.id as 'Bz' | 'Bt' | 'speed']} locale={locale} />}
        </article>)}</div>
        <p className="monitoring-source">{c.source}: <a href={solar.data.sourceUrl} target="_blank" rel="noreferrer">NOAA ↗</a><InfoHint>{time(solar.data.retrievedAt)}</InfoHint></p>
      </>}
    </section>
    <section className="home-section-band home-section-band-odd monitoring-section">
      <h2>{c.cameras}<InfoHint>{c.cameraHelp} {c.cameraTime}</InfoHint></h2>
      <div className="camera-links">
        <CameraViewer name={language === 'zh' ? '丘吉尔，加拿大' : 'Churchill, Canada'} source="Explore.org · Polar Bears International" url="https://polarbearsinternational.org/education/live/northern-lights-cams/" videoId="a0i1Kg6fROg" language={language} />
        <CameraViewer name={language === 'zh' ? 'Poker Flat，阿拉斯加' : 'Poker Flat, Alaska'} source="University of Alaska Fairbanks" url="https://allsky.gi.alaska.edu/" language={language} />
        <CameraViewer name={language === 'zh' ? '费尔班克斯，阿拉斯加' : 'Fairbanks, Alaska'} source="Explore.org · University of Alaska Fairbanks" url="https://explore.org/livecams/currently-live/aurora-cam" videoId="O52zDyxg5QI" language={language} />
      </div>
    </section>
    <ObservationHistory key={facts?.outlook.location.id ?? 'default'} facts={facts} />
  </>
}
function CameraViewer({ name, source, url, videoId, language }: { name: string; source: string; url: string; videoId?: string; language: string }) {
  const [open, setOpen] = useState(false)
  const zh = language === 'zh'
  const embed = videoId ? `https://www.youtube-nocookie.com/embed/${videoId}?autoplay=1&mute=1&controls=1&playsinline=1&rel=0` : url
  return <article>
    <h3>{name}</h3>
    <div className={`camera-viewer${videoId ? ' camera-video' : ''}`}>
      {open ? <iframe className="aurora-camera-frame" src={embed} title={name} referrerPolicy="strict-origin-when-cross-origin" allow="autoplay; encrypted-media; picture-in-picture; fullscreen" allowFullScreen /> : <button type="button" className="camera-start" onClick={() => setOpen(true)} aria-label={`${zh ? '开始查看' : 'Start viewing'}: ${name}`}><span aria-hidden="true">▶</span>{zh ? '开始查看' : 'Start viewing'}</button>}
    </div>
    {open && <button className="camera-stop" type="button" onClick={() => setOpen(false)}>{zh ? '■ 关闭画面' : '■ Close viewer'}</button>}
    <p className="monitoring-source">{zh ? '来源' : 'Source'}: <a href={url} target="_blank" rel="noreferrer">{source} ↗</a></p>
  </article>
}
function ObservationHistory({ facts }: { facts: ObservationFacts | null }) {
  const { language } = useI18n(), c = copy[language], locale = language === 'zh' ? 'zh-CN' : 'en'
  const places = [{ id: 2964574, name: 'Dublin' }, { id: 2965768, name: 'Carlow' }, { id: 3413829, name: 'Reykjavik' }]
  if (facts && !places.some(p => p.id === facts.outlook.location.id)) places.unshift(facts.outlook.location)
  const [locationId, setLocationId] = useState(facts?.outlook.location.id ?? 2964574)
  const { data, failed } = useMonitoring<History>(`/api/v1/history/${locationId}`, facts?.generatedAtUtc)
  const [selectedAt, setSelectedAt] = useState('')
  const snapshots = data?.snapshots ?? [], selected = snapshots.find(p => p.capturedAt === selectedAt) ?? snapshots[0]
  const format = (at: string) => new Intl.DateTimeFormat(locale, { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit', timeZone: 'UTC' }).format(new Date(at)) + ' UTC'
  return <section className="home-section-band home-section-band-even monitoring-section">
    <h2>{c.history}<InfoHint>{c.historyHelp}</InfoHint></h2>
    <div className="history-controls"><label>{c.choose}<select value={locationId} onChange={e => { setLocationId(Number(e.target.value)); setSelectedAt('') }}>{places.map(p => <option key={p.id} value={p.id}>{p.name}</option>)}</select></label>
      {snapshots.length > 0 && <label>{c.snapshot}<select value={selected?.capturedAt} onChange={e => { setSelectedAt(e.target.value) }}>{snapshots.map(p => <option key={p.capturedAt} value={p.capturedAt}>{format(p.capturedAt)}</option>)}</select></label>}
    </div>
    {!data ? <p role="status">{failed ? c.unavailable : c.loading}</p> : data.status !== 'AVAILABLE' ? <p>{data.status === 'DISABLED' ? c.disabled : c.unavailable}</p> : !selected ? <p>{c.empty}</p> : <>
      <div className="monitoring-plots history-plots">
        <article><h3>{c.model}</h3><strong className="monitoring-value">{selected.auroraStatus === 'CURRENT' ? selected.modelValue ?? '—' : selected.auroraStatus === 'EXPIRED' ? c.stale : c.unavailable}/100</strong><EvidenceChart points={snapshots.filter(p => p.modelValue != null && p.auroraStatus === 'CURRENT').map(p => ({ at: p.capturedAt, value: p.modelValue! }))} label={c.model} unit="/100" locale={locale} bounds={[0, 100]} maxGapMinutes={30} color="#83b8ee" /></article>
        <article><h3>{c.cloud}</h3><strong className="monitoring-value">{selected.cloudStatus === 'CURRENT' ? selected.cloudPercent ?? '—' : selected.cloudStatus === 'EXPIRED' ? c.stale : c.unavailable}%</strong><EvidenceChart points={snapshots.filter(p => p.cloudPercent != null && p.cloudStatus === 'CURRENT').map(p => ({ at: p.capturedAt, value: p.cloudPercent! }))} label={c.cloud} unit="%" locale={locale} bounds={[0, 100]} maxGapMinutes={30} color="#eef3fa" /></article>
      </div>
      <p>{c.sky}: {selected.skyLevel === 'HIGH' ? c.high : selected.skyLevel === 'MEDIUM' ? c.medium : selected.skyLevel === 'LOW' ? c.low : c.unknown}</p>
      <p className="monitoring-source">{c.source}: <a href="https://www.spaceweather.gov/products/aurora-30-minute-forecast" target="_blank" rel="noreferrer">NOAA ↗</a> · <a href="https://api.met.no/weatherapi/locationforecast/2.0/documentation" target="_blank" rel="noreferrer">MET Norway ↗</a></p>
    </>}
  </section>
}
