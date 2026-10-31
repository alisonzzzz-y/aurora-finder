import { useEffect, useRef, useState } from 'react'
import { loadYouTubeApi, PLAYING, type YouTubePlayer } from './youtubePlayer'

type Status = 'starting' | 'playing' | 'stalled' | 'unavailable' | 'fallback'
// If a muted live stream has not started by then, it is most likely offline or blocked.
const STALL_MS = 20_000

export function CameraViewer({ name, source, url, videoId, language }: { name: string; source: string; url: string; videoId?: string; language: string }) {
  const [open, setOpen] = useState(false)
  const [status, setStatus] = useState<Status>('starting')
  const zh = language === 'zh'
  const target = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (!open || !videoId) return
    let player: YouTubePlayer | null = null
    let cancelled = false
    let playing = false
    const stall = window.setTimeout(() => { if (!playing && !cancelled) setStatus(current => current === 'starting' ? 'stalled' : current) }, STALL_MS)
    loadYouTubeApi().then(api => {
      if (cancelled || !target.current) return
      player = new api.Player(target.current, {
        host: 'https://www.youtube-nocookie.com',
        videoId,
        playerVars: { autoplay: 1, mute: 1, controls: 1, playsinline: 1, rel: 0 },
        events: {
          onStateChange: event => {
            if (event.data !== PLAYING) return
            playing = true
            setStatus('playing')
          },
          // Errors cover removed, private, ended or non-embeddable streams.
          onError: () => { if (!cancelled) setStatus('unavailable') },
        },
      })
      player.getIframe?.()?.setAttribute('title', name)
    }).catch(() => { if (!cancelled) setStatus('fallback') })
    return () => {
      cancelled = true
      window.clearTimeout(stall)
      player?.destroy()
    }
  }, [open, videoId, name])

  const start = () => { setStatus('starting'); setOpen(true) }
  const embed = videoId ? `https://www.youtube-nocookie.com/embed/${videoId}?autoplay=1&mute=1&controls=1&playsinline=1&rel=0` : url
  const notice = status === 'unavailable'
    ? zh ? '这路直播目前无法播放，可能已结束、离线或不允许嵌入。请打开原站查看。' : 'This stream cannot play right now. It may have ended, be offline or block embedding. Open the source website instead.'
    : status === 'stalled'
      ? zh ? '画面还没有开始播放。直播可能离线；可以点击播放器重试，或打开原站查看。' : 'The stream has not started yet. It may be offline; press play in the player to retry, or open the source website.'
      : ''
  return <article>
    <h3>{name}</h3>
    <div className={`camera-viewer${videoId ? ' camera-video' : ''}`} data-status={open ? status : 'closed'}>
      {!open && <button type="button" className="camera-start" onClick={start} aria-label={`${zh ? '开始查看' : 'Start viewing'}: ${name}`}><span aria-hidden="true">▶</span>{zh ? '开始查看' : 'Start viewing'}</button>}
      {open && (status === 'fallback' || !videoId) && <iframe className="aurora-camera-frame" src={embed} title={name} referrerPolicy="strict-origin-when-cross-origin" allow="autoplay; encrypted-media; picture-in-picture; fullscreen" allowFullScreen />}
      {open && videoId && status !== 'fallback' && status !== 'unavailable' && <div className="camera-player"><div ref={target} /></div>}
      {open && status === 'unavailable' && <p className="camera-notice" role="status">{notice}</p>}
    </div>
    {open && status === 'stalled' && <p className="camera-notice" role="status">{notice}</p>}
    {open && <button className="camera-stop" type="button" onClick={() => setOpen(false)}>{zh ? '■ 关闭画面' : '■ Close viewer'}</button>}
    <p className="monitoring-source">{zh ? '来源' : 'Source'}: <a href={url} target="_blank" rel="noreferrer">{source} ↗</a></p>
  </article>
}
