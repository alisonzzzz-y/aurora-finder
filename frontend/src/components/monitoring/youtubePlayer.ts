// Minimal typing for the official YouTube IFrame Player API, loaded only when a camera is opened.
export type YouTubePlayer = { destroy(): void; getIframe?(): HTMLIFrameElement }
type PlayerOptions = {
  host?: string
  videoId: string
  playerVars: Record<string, number>
  events: { onStateChange?(event: { data: number }): void; onError?(event: { data: number }): void }
}
export type YouTubeApi = { Player: new (element: HTMLElement, options: PlayerOptions) => YouTubePlayer }
declare global {
  interface Window { YT?: YouTubeApi; onYouTubeIframeAPIReady?: () => void }
}

export const PLAYING = 1
let loading: Promise<YouTubeApi> | null = null

export function loadYouTubeApi(timeoutMs = 10_000): Promise<YouTubeApi> {
  if (window.YT?.Player) return Promise.resolve(window.YT)
  loading ??= new Promise<YouTubeApi>((resolve, reject) => {
    const previous = window.onYouTubeIframeAPIReady
    const timer = window.setTimeout(() => reject(new Error('YouTube player API did not load')), timeoutMs)
    window.onYouTubeIframeAPIReady = () => {
      previous?.()
      window.clearTimeout(timer)
      if (window.YT?.Player) resolve(window.YT)
      else reject(new Error('YouTube player API is unavailable'))
    }
    const script = document.createElement('script')
    script.src = 'https://www.youtube.com/iframe_api'
    script.async = true
    script.onerror = () => { window.clearTimeout(timer); reject(new Error('YouTube player API could not load')) }
    document.head.appendChild(script)
  }).catch(error => {
    // Allow a later attempt, for example after the network recovers.
    loading = null
    throw error
  })
  return loading
}
