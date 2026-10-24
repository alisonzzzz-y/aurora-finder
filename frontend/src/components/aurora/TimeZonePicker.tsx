import { useEffect, useId, useMemo, useRef, useState } from 'react'

const commonZones = [
  ['UTC', '协调世界时', 'Universal time'],
  ['Europe/London', '英国', 'UK'],
  ['Europe/Dublin', '爱尔兰', 'Ireland'],
  ['Europe/Paris', '中欧', 'Central Europe'],
  ['Atlantic/Reykjavik', '冰岛', 'Iceland'],
  ['America/New_York', '北美东部', 'Eastern North America'],
  ['America/Los_Angeles', '北美太平洋', 'Pacific North America'],
  ['Asia/Shanghai', '中国', 'China'],
  ['Asia/Tokyo', '日本', 'Japan'],
  ['Australia/Sydney', '澳大利亚东部', 'Eastern Australia'],
  ['Pacific/Auckland', '新西兰', 'New Zealand'],
] as const

function offset(zone: string, locale: string, at: Date) {
  return new Intl.DateTimeFormat(locale, { timeZone: zone, timeZoneName: 'shortOffset' }).formatToParts(at).find(part => part.type === 'timeZoneName')?.value ?? ''
}

export function TimeZonePicker({ value, onChange, language }: { value: string; onChange: (zone: string) => void; language: string }) {
  const zh = language === 'zh', locale = zh ? 'zh-CN' : 'en'
  const [deviceZone] = useState(() => Intl.DateTimeFormat().resolvedOptions().timeZone)
  const [searchOpen, setSearchOpen] = useState(false)
  const [query, setQuery] = useState('')
  const inputRef = useRef<HTMLInputElement>(null)
  const toggleRef = useRef<HTMLButtonElement>(null)
  const searchId = useId()
  const options = useMemo(() => {
    const at = new Date()
    const zones = [deviceZone, ...commonZones.map(([zone]) => zone), value]
    return [...new Set(zones)].map(zone => {
      const common = commonZones.find(([id]) => id === zone)
      const name = common ? common[zh ? 1 : 2] : zone.replaceAll('_', ' ')
      return { zone, label: `${zone === deviceZone ? zh ? '设备时区' : 'Device time zone' : name} · ${offset(zone, locale, at)}` }
    })
  }, [deviceZone, value, locale, zh])
  const searchableZones = useMemo(() => {
    if (!searchOpen) return []
    const at = new Date()
    const zones = [...new Set([deviceZone, 'UTC', ...commonZones.map(([zone]) => zone), ...Intl.supportedValuesOf('timeZone')])]
    return zones.map(zone => {
      const common = commonZones.find(([id]) => id === zone)
      const name = new Intl.DateTimeFormat(locale, { timeZone: zone, timeZoneName: 'long' }).formatToParts(at).find(part => part.type === 'timeZoneName')?.value ?? ''
      return { zone, label: `${zone.replaceAll('_', ' ')} · ${offset(zone, locale, at)}`, search: `${zone} ${zone.replaceAll('_', ' ')} ${name} ${common?.join(' ') ?? ''}`.toLocaleLowerCase() }
    })
  }, [searchOpen, deviceZone, locale])
  const matches = query.trim() ? searchableZones.filter(zone => zone.search.includes(query.trim().toLocaleLowerCase())) : []
  const results = matches.slice(0, 8)
  useEffect(() => { if (searchOpen) inputRef.current?.focus() }, [searchOpen])
  function close() { setSearchOpen(false); setQuery(''); toggleRef.current?.focus() }
  function choose(zone: string) { onChange(zone); close() }
  return <div className="kp-timezone-picker">
    <label><span>{zh ? '时区' : 'Time zone'}</span><select value={value} onChange={event => { onChange(event.target.value); setSearchOpen(false); setQuery('') }}>{options.map(option => <option key={option.zone} value={option.zone}>{option.label}</option>)}</select></label>
    <button ref={toggleRef} type="button" className="timezone-search-toggle" aria-expanded={searchOpen} aria-controls={searchId} onClick={() => searchOpen ? close() : setSearchOpen(true)}>{zh ? '搜索其他时区' : 'Find another time zone'}</button>
    {searchOpen && <div id={searchId} className="timezone-search-panel" onKeyDown={event => { if (event.key === 'Escape') { event.preventDefault(); close() } }}>
      <label htmlFor={`${searchId}-input`}>{zh ? '输入城市或时区名称' : 'Enter a city or time zone'}</label>
      <input ref={inputRef} id={`${searchId}-input`} type="search" value={query} placeholder={zh ? '例如 London、日本、America' : 'e.g. London, Japan, America'} onChange={event => setQuery(event.target.value)} onKeyDown={event => { if (event.key === 'Enter' && !event.nativeEvent.isComposing && results.length) { event.preventDefault(); choose(results[0].zone) } }} />
      <ul>{results.map(result => <li key={result.zone}><button type="button" onClick={() => choose(result.zone)}>{result.label}</button></li>)}</ul>
      <p role="status">{query.trim() ? matches.length ? zh ? `找到 ${matches.length} 个时区${matches.length > 8 ? '，请继续输入缩小范围' : ''}` : `${matches.length} matches${matches.length > 8 ? '. Keep typing to narrow the list.' : ''}` : zh ? '没有匹配的时区' : 'No matching time zones' : zh ? '输入名称即可查找' : 'Start typing to find a time zone'}</p>
      <button type="button" className="timezone-search-toggle" onClick={close}>{zh ? '取消' : 'Cancel'}</button>
    </div>}
  </div>
}
