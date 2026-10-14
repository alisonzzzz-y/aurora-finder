import type { Location } from '../../types/location'
import { useI18n } from '../../i18n'
import { InfoHint } from '../InfoHint'
import './PopularPlaces.css'
const places = [
  {id:6185377,name:'Yellowknife',zh:'耶洛奈夫',region:'Northwest Territories',subregion:'North Slave Region',country:'Canada',countryZh:'加拿大',latitude:62.45411,longitude:-114.37248,timezone:'America/Edmonton',source:'https://www.yellowknife.ca/weather-and-aurora-borealis'},
  {id:3413829,name:'Reykjavik',zh:'雷克雅未克',region:'Capital Region',subregion:'Reykjavíkurborg',country:'Iceland',countryZh:'冰岛',latitude:64.13548,longitude:-21.89541,timezone:'Atlantic/Reykjavik',source:'https://www.visiticeland.com/the-auroras/'},
  {id:2191562,name:'Dunedin',zh:'但尼丁',region:'Otago',subregion:'Dunedin City',country:'New Zealand',countryZh:'新西兰',latitude:-45.87416,longitude:170.50362,timezone:'Pacific/Auckland',source:'https://media.newzealand.com/home/natural-phenomena-in-new-zealand/'},
  {id:2163355,name:'Hobart',zh:'霍巴特',region:'Tasmania',subregion:'Hobart',country:'Australia',countryZh:'澳大利亚',latitude:-42.87936,longitude:147.3294,timezone:'Australia/Hobart',source:'https://production.tourismtasmania.com.au/experiences/stories/southern-lights/'},
]
export function PopularPlaces({onSelect}:{onSelect:(place:Location)=>void}) {
  const {language}=useI18n(), zh=language==='zh'
  const [north,south]=[places.filter(p=>p.latitude>0),places.filter(p=>p.latitude<0)]
  return <section className="popular-places" aria-labelledby="popular-title"><h2 id="popular-title">{zh?'热门观测目的地':'Popular aurora destinations'}<InfoHint>{zh?'根据当地官方旅游资料精选，非用户访问量排名，也不是今晚观测保证。点击查看当前条件，市区光污染与具体观测点视野需另行确认。':'Selected from official destination guides, not ranked by visitor counts. This does not guarantee aurora tonight. Check current conditions; city lights and sightlines vary.'}</InfoHint></h2>
    {[north,south].map((group,index)=><div key={index}><h3>{zh?index===0?'北极光':'南极光':index===0?'Northern lights':'Southern lights'}</h3><div className="popular-grid">{group.map(place=><article key={place.id}>
      <h4>{zh?place.zh:place.name}</h4><p>{zh?place.countryZh:place.country}</p>
      <button type="button" className="comparison-action" onClick={()=>onSelect(place)}>{zh?'查看当前条件':'Check current conditions'}</button>
      <a href={place.source} target="_blank" rel="noreferrer">{zh?'来源：目的地指南':'Source: destination guide'} ↗</a>
    </article>)}</div></div>)}
  </section>
}
