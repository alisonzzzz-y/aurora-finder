import { useEffect, useRef, useState } from 'react'
import type { Location } from '../../types/location'
import type { ObservationFacts } from '../../types/observationFacts'
import { nearbyLocations } from '../../api/nearbyLocations'
import { getObservationFacts } from '../../api/observationFacts'
import { useI18n } from '../../i18n'
import { InfoHint } from '../InfoHint'
import { commonCloudTimes, darknessAt, distanceKm } from './nearbyMath'
import './NearbyComparison.css'

type Row = { place: Location; facts: ObservationFacts | null }
export function NearbyComparison({ origin, onSelect }: { origin: Location; onSelect:(place:Location)=>void }) {
  const { language } = useI18n()
  const zh = language==='zh'
  const [radius,setRadius] = useState(50)
  const [rows,setRows] = useState<Row[]>([])
  const [busy,setBusy] = useState(false)
  const [message,setMessage] = useState<'error'|'empty'|null>(null)
  const [selectedTime,setSelectedTime] = useState<number|null>(null)
  const [now,setNow] = useState(Date.now)
  const request = useRef<AbortController|null>(null)
  useEffect(()=>{
    const timer=window.setInterval(()=>setNow(Date.now()),30000)
    return ()=>{request.current?.abort();window.clearInterval(timer)}
  },[])
  async function search() {
    request.current?.abort()
    const controller = new AbortController(); request.current=controller
    setBusy(true);setMessage(null);setRows([]);setSelectedTime(null)
    try {
      const places = await nearbyLocations(origin,radius,controller.signal)
      const loaded = await Promise.all([origin,...places].map(async place=>{
        try { return {place,facts:await getObservationFacts(place.id,controller.signal)} }
        catch { return {place,facts:null} }
      }))
      if(controller.signal.aborted) return
      setRows(loaded);setNow(Date.now())
      if(!places.length) setMessage('empty')
    } catch { if(!controller.signal.aborted) setMessage('error') }
    finally { if(!controller.signal.aborted) setBusy(false) }
  }
  const available=rows.flatMap(r=>r.facts?[r.facts]:[])
  const times=commonCloudTimes(available,now)
  const time=selectedTime!==null && times.includes(selectedTime)?selectedTime:times[0]
  const format=(value:number)=>new Intl.DateTimeFormat(zh?'zh-CN':'en-IE',{month:'short',day:'numeric',hour:'2-digit',minute:'2-digit',timeZone:origin.timezone,timeZoneName:'short'}).format(new Date(value))
  const clouds=rows.map(r=>r.facts?.cloudForecast.status==='CURRENT' && r.facts.cloudForecast.data && Date.parse(r.facts.cloudForecast.data.expiresAt)>now ? r.facts.cloudForecast.data.cloudForecast.find(p=>Date.parse(p.validAt)===time)?.cloudCoverPercent : null)
  const validClouds=clouds.filter((c):c is number=>c!==null && c!==undefined && Number.isFinite(c) && c>=0 && c<=100)
  return <section className="nearby-comparison" aria-labelledby="nearby-title">
    <header><h2 id="nearby-title" tabIndex={-1}>{zh?'比较附近地点':'Compare nearby places'}<InfoHint>{zh?'按 GeoNames 城市目录选择范围内最近的三个候选；目录以人口超过 15,000 的城市及首府为主，不覆盖所有村镇。距离为直线距离，不是驾车距离。云量按同一时刻比较，天黑指太阳低于地平线 18°；极光模型另列有效时间。云少不代表一定能看到极光，地点的通行与视野尚未核实。':'Shows the three nearest candidates in the GeoNames city index, mainly cities above 15,000 people and capitals. Smaller settlements may be absent. Distances are straight-line distances. Cloud cover uses the same time; darkness means the Sun is below −18°. Aurora readings have their own validity time. Less cloud does not guarantee aurora. Access and sightlines have not been checked.'}</InfoHint></h2>
      <div className="nearby-controls"><label>{zh?'范围':'Range'} <select value={radius} disabled={busy} onChange={e=>{setRadius(Number(e.target.value));setRows([]);setMessage(null)}}>{[25,50,100].map(km=><option key={km} value={km}>{km} km</option>)}</select></label>
      <button className="comparison-action" type="button" disabled={busy} onClick={()=>{void search()}}>{zh?busy?'正在比较…':'查找并比较':busy?'Comparing…':'Find and compare'}</button></div></header>
    {message && <p role="status">{zh?message==='error'?'附近地点暂时无法加载，请重试。':'此范围内未找到可用候选，可扩大范围。':message==='error'?'Nearby places could not be loaded. Please retry.':'No candidates found in this range. Try a wider range.'}</p>}
    {rows.length>0 && <>
      {times.length>0 ? <label className="nearby-time">{zh?'云量与天黑对比时间':'Cloud and darkness comparison time'} <select value={time} onChange={e=>setSelectedTime(Number(e.target.value))}>{times.map(t=><option key={t} value={t}>{format(t)}</option>)}</select></label> : <p role="status">{zh?'暂无共同有效的云量时段':'No shared valid cloud forecast period'}</p>}
      <div className="nearby-grid">{rows.map((row,index)=>{
        const cloud=clouds[index], dark=row.facts && time!==undefined?darknessAt(row.facts,time):null
        const activity=row.facts?.auroraActivity
        const current=activity?.status==='CURRENT' && activity.data?.status==='CURRENT' && Date.parse(activity.data.forecastTime)>now
        const value=current?activity.data?.modelValue:null
        return <article key={row.place.id}><h3>{row.place.name}</h3><span>{index===0?(zh?'当前地点':'Selected place'):`${distanceKm(origin,row.place).toFixed(1)} km · ${zh?'直线距离':'straight line'}`}</span>
          {!row.facts && <p role="status">{zh?'预报暂不可用':'Forecast unavailable'}</p>}
          <dl><div><dt>{zh?'云量':'Cloud cover'}</dt><dd>{cloud==null || time===undefined?'—':`${cloud}%`}{validClouds.length>=2 && cloud===Math.min(...validClouds) && <span className="nearby-best">{zh?'云量最少':'Least cloud'}</span>}</dd></div>
          <div><dt>{zh?'天色足够暗':'Dark sky'}</dt><dd>{dark===null?'—':dark?(zh?'是':'Yes'):(zh?'否':'No')}</dd></div>
          <div><dt>{zh?'短时极光模型':'Short-range aurora model'}</dt><dd>{value==null?'—':`${value}/100`}</dd></div></dl>
          {current && activity.data && <time className="nearby-model-time">{zh?'模型有效时间：':'Model valid at: '}{format(Date.parse(activity.data.forecastTime))}</time>}
          <button className="comparison-action" type="button" onClick={()=>onSelect(row.place)}>{zh?'在地图上查看':'View on map'}</button>
        </article>
      })}</div>
      <p className="nearby-source">{zh?'来源：':'Sources: '}<a href="https://www.geonames.org/" target="_blank" rel="noreferrer">GeoNames</a> · <a href="https://open-meteo.com/" target="_blank" rel="noreferrer">Open-Meteo</a> · NOAA · MET Norway <InfoHint>CC BY 4.0</InfoHint></p>
    </>}
  </section>
}
