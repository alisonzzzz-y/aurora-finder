import { test, expect, type Page } from '@playwright/test'
import { readFileSync } from 'node:fs'
const facts = JSON.parse(readFileSync(new URL('./fixtures/dublin.json', import.meta.url), 'utf8'))
const place = facts.outlook.location
const other = { ...place, id: 4192205, country: 'United States', region: 'Georgia' }
const snapshot = { locationId: place.id, locationName: 'Dublin', timezone: 'Europe/Dublin', auroraStatus: 'CURRENT', cloudStatus: 'CURRENT', modelValue: 1, cloudPercent: 42, skyLevel: 'MEDIUM' }

async function setup(page: Page) {
  await page.clock.setFixedTime(new Date('2026-10-26T11:38:00Z'))
  await page.route('**/*', async route => {
    const url = new URL(route.request().url())
    if (url.pathname.startsWith('/api/')) {
      if (url.pathname === '/api/v1/locations') return route.fulfill({ json: [place, other] })
      if (url.pathname === `/api/v1/facts/${place.id}`) return route.fulfill({ json: facts })
      if (url.pathname.startsWith('/api/v1/history/')) return route.fulfill({ json: { status: 'AVAILABLE', retentionDays: 7, snapshots: [
        { ...snapshot, capturedAt: '2026-10-26T09:00:00Z' }, { ...snapshot, modelValue: 2, capturedAt: '2026-10-26T10:00:00Z' },
      ] } })
      return route.fulfill({ status: 503, json: { message: 'Fixture: source unavailable' } })
    }
    if (url.hostname !== '127.0.0.1') return route.abort()
    return route.continue()
  })
  await page.goto('/')
  await page.getByRole('button', { name: 'English', exact: true }).click()
}

async function selectDublin(page: Page) {
  const input = page.locator('.map-sidebar [role=combobox]')
  await input.fill('Dublin')
  await page.locator('.map-sidebar [role=option]').filter({ hasText: 'Ireland' }).click()
  await expect(input).toBeFocused()
  await input.press('Enter')
  await expect(page.locator('#location-details')).toBeVisible()
}

test.beforeEach(async ({ page }) => { await setup(page) })

test('choosing a place keeps focus and Enter opens details without navigation', async ({ page }) => {
  const errors: string[] = []
  page.on('pageerror', error => errors.push(error.message))
  page.on('console', message => { if (message.text().includes('same key')) errors.push(message.text()) })
  await selectDublin(page)
  await expect(page).toHaveURL('http://127.0.0.1:4175/')
  await expect(page.locator('.map-sidebar')).toContainText('Dublin')
  await expect(page.locator('#location-details')).toContainText('Dublin')
  await expect(page.locator('.map-sidebar [role=combobox]')).not.toHaveAttribute('aria-activedescendant')
  expect(errors).toEqual([])
})

test('IME Enter does not select the highlighted autocomplete option', async ({ page }) => {
  const input = page.locator('.map-sidebar [role=combobox]')
  await input.fill('Dublin')
  await expect(page.locator('.map-sidebar [role=option]')).toHaveCount(2)
  await input.press('ArrowDown')
  await input.dispatchEvent('keydown', { key: 'Enter', code: 'Enter', isComposing: true, keyCode: 229 })
  await expect(page.locator('.map-sidebar [role=option]')).toHaveCount(2)
  await expect(page.locator('#location-details')).toHaveCount(0)
  await input.press('Enter')
  await input.press('Enter')
  await expect(page.locator('#location-details')).toBeVisible()
})

test('assistant remembers a chosen place and retries without duplicating the question', async ({ page }) => {
  const requests: { message: string; locationId?: number; history: unknown[] }[] = []
  await page.route('**/api/v1/assistant/chat', async route => {
    requests.push(route.request().postDataJSON())
    if (requests.length === 1) return route.fulfill({ json: { answer: 'Which Dublin?', locationCandidates: [place, other] } })
    if (requests.length === 3) return route.fulfill({ status: 503, json: { code: 'ASSISTANT_UNAVAILABLE' } })
    return route.fulfill({ json: { answer: 'Source samples for the selected night.', selectedLocationId: place.id, locationCandidates: [] } })
  })
  await page.locator('.agent-button').click()
  const input = page.locator('#assistant-question')
  await input.fill('Dublin tonight?')
  await input.press('Shift+Enter')
  expect(requests).toHaveLength(0)
  await input.press('Enter')
  await page.locator('.assistant-location-choice').first().click()
  await expect(input).toBeEnabled()
  await input.fill('Tomorrow night?')
  await input.press('Enter')
  await page.locator('.assistant-retry').click()
  await expect(input).toBeEnabled()
  expect(requests).toHaveLength(4)
  expect(requests[2].locationId).toBe(place.id)
  expect(requests[3]).toEqual(requests[2])
  await expect(page.locator('.assistant-message.user')).toHaveCount(3)
})

test('assistant uses a newly chosen map place instead of pending choices', async ({ page }) => {
  const requests: { message: string; locationId?: number }[] = []
  await page.route('**/api/v1/assistant/chat', async route => {
    requests.push(route.request().postDataJSON())
    if (requests.length === 1) return route.fulfill({ json: { answer: 'Which Dublin?', locationCandidates: [place, other] } })
    return route.fulfill({ json: { answer: 'Source samples for the selected night.', selectedLocationId: place.id, locationCandidates: [] } })
  })
  await page.locator('.agent-button').click()
  const input = page.locator('#assistant-question')
  await input.fill('Dublin tonight?')
  await input.press('Enter')
  await expect(page.locator('.assistant-location-choice')).toHaveCount(2)
  await selectDublin(page)
  await input.fill('How about here?')
  await input.press('Enter')
  await expect(input).toBeEnabled()
  expect(requests).toHaveLength(2)
  expect(requests[1].locationId).toBe(place.id)
})

test('assistant answers render bold, lists and links without raw Markdown', async ({ page }) => {
  await page.route('**/api/v1/assistant/chat', route => route.fulfill({ json: {
    answer: '**Kp 5** expected.\n- 22:00: 40%\n- 23:00: 20%\nSource: [NOAA](https://www.swpc.noaa.gov/)', locationCandidates: [],
  } }))
  await page.locator('.agent-button').click()
  const input = page.locator('#assistant-question')
  await input.fill('Kp tonight?')
  await input.press('Enter')
  const answer = page.locator('.assistant-message.assistant .assistant-markdown')
  await expect(answer.locator('strong')).toHaveText('Kp 5')
  await expect(answer.locator('li')).toHaveCount(2)
  await expect(answer.locator('a')).toHaveAttribute('href', 'https://www.swpc.noaa.gov/')
  await expect(answer).not.toContainText('**')
})

test('assistant drag resize and close stay inside usable viewport', async ({ page }) => {
  await page.addStyleTag({ content: 'html { overflow-y: scroll !important; }' })
  await page.locator('.agent-button').click()
  const check = async () => {
    const bounds = await page.locator('#assistant-panel').boundingBox()
    const width = await page.evaluate(() => document.documentElement.clientWidth)
    expect(bounds!.x).toBeGreaterThanOrEqual(0)
    expect(bounds!.x + bounds!.width).toBeLessThanOrEqual(width)
  }
  await check()
  const h = (await page.locator('.assistant-drag-handle').boundingBox())!
  await page.mouse.move(h.x + 30, h.y + 15); await page.mouse.down()
  await page.mouse.move(page.viewportSize()!.width, 160, { steps: 8 }); await page.mouse.up()
  await check()
  const r = (await page.locator('.assistant-resize-handle').boundingBox())!
  await page.mouse.move(r.x + 10, r.y + 10); await page.mouse.down()
  await page.mouse.move(0, 20, { steps: 8 }); await page.mouse.up()
  await check()
  await page.locator('.assistant-collapse').click()
  await expect(page.locator('#assistant-panel')).toHaveCount(0)
  expect(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth)).toBe(false)
})

test('history gaps preserve chart nodes and layout', async ({ page }) => {
  const slider = page.locator('.history-timeline input[type=range]')
  await expect(slider).toBeVisible()
  const charts = page.locator('.history-plots svg')
  await expect(charts).toHaveCount(2)
  await charts.first().evaluate(node => node.setAttribute('data-original-node', 'yes'))
  const height = (await page.locator('.history-results').boundingBox())!.height
  await slider.focus()
  for (const key of ['ArrowLeft', 'ArrowLeft', 'ArrowLeft', 'ArrowLeft', 'ArrowRight']) {
    await slider.press(key)
    await expect(charts).toHaveCount(2)
    await expect(charts.first()).toHaveAttribute('data-original-node', 'yes')
    expect((await page.locator('.history-results').boundingBox())!.height).toBeCloseTo(height, 1)
  }
  await expect(page.locator('.history-results')).toContainText('No data')
})

test('timezone search supports keyboard selection and escape', async ({ page }) => {
  await page.locator('.timezone-search-toggle').click()
  const input = page.locator('.timezone-search-panel input')
  await input.fill('Tokyo'); await input.press('Enter')
  await expect(page.locator('.kp-timezone-picker select')).toHaveValue('Asia/Tokyo')
  await page.locator('.timezone-search-toggle').click()
  await input.fill('not-a-timezone')
  await input.press('Escape')
  await expect(input).toHaveCount(0)
})

function fakeYouTubeApi(event: 'play' | 'error') {
  return `window.YT = { Player: class {
    constructor(element, options) {
      this.frame = document.createElement('iframe'); this.frame.src = 'about:blank'; element.replaceWith(this.frame)
      setTimeout(() => ${event === 'play' ? 'options.events.onStateChange({ data: 1 })' : 'options.events.onError({ data: 150 })'}, 50)
    }
    getIframe() { return this.frame }
    destroy() { this.frame.remove() }
  } }; window.onYouTubeIframeAPIReady()`
}

test('camera viewer reports a playing stream', async ({ page }) => {
  await page.route('https://www.youtube.com/iframe_api', route => route.fulfill({ contentType: 'text/javascript', body: fakeYouTubeApi('play') }))
  await page.getByRole('button', { name: 'Start viewing: Churchill, Canada' }).click()
  const viewer = page.locator('.camera-viewer').first()
  await expect(viewer).toHaveAttribute('data-status', 'playing')
  await expect(viewer.locator('iframe')).toHaveAttribute('title', 'Churchill, Canada')
  await expect(page.locator('.camera-notice')).toHaveCount(0)
})

test('camera viewer explains a stream that cannot play', async ({ page }) => {
  await page.route('https://www.youtube.com/iframe_api', route => route.fulfill({ contentType: 'text/javascript', body: fakeYouTubeApi('error') }))
  await page.getByRole('button', { name: 'Start viewing: Churchill, Canada' }).click()
  await expect(page.locator('.camera-viewer').first()).toHaveAttribute('data-status', 'unavailable')
  await expect(page.locator('.camera-notice')).toContainText('cannot play right now')
})

test('camera viewer falls back to the plain embed when the player API is blocked', async ({ page }) => {
  await page.getByRole('button', { name: 'Start viewing: Churchill, Canada' }).click()
  const viewer = page.locator('.camera-viewer').first()
  await expect(viewer).toHaveAttribute('data-status', 'fallback')
  await expect(viewer.locator('iframe.aurora-camera-frame')).toHaveAttribute('src', /youtube-nocookie\.com\/embed\/a0i1Kg6fROg/)
})

test('background alerts subscribe with the page language and unsubscribe', async ({ page }) => {
  await page.addInitScript(() => {
    let subscription: unknown = null
    const fake = {
      endpoint: 'https://fcm.googleapis.com/fcm/send/test',
      toJSON: () => ({ endpoint: 'https://fcm.googleapis.com/fcm/send/test', keys: { p256dh: 'p', auth: 'a' } }),
      unsubscribe: async () => { subscription = null; return true },
    }
    const registration = { pushManager: { getSubscription: async () => subscription, subscribe: async () => (subscription = fake) } }
    let registered = false
    Object.defineProperty(navigator, 'serviceWorker', { value: {
      register: async () => { registered = true; return registration },
      getRegistration: async () => registered ? registration : undefined,
      ready: Promise.resolve(registration),
    } })
    Object.defineProperty(window, 'PushManager', { value: function PushManager() {} })
    Object.defineProperty(Notification, 'permission', { get: () => 'granted' })
    Notification.requestPermission = async () => 'granted'
  })
  const posts: { path: string; body: { language?: string; endpoint: string } }[] = []
  await page.route('**/api/v1/push/**', route => {
    const url = new URL(route.request().url())
    if (url.pathname === '/api/v1/push/config') return route.fulfill({ json: { enabled: true, publicKey: 'BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4' } })
    posts.push({ path: url.pathname, body: route.request().postDataJSON() })
    return route.fulfill({ status: 204 })
  })
  await page.reload()
  await page.getByRole('button', { name: 'English', exact: true }).click()
  await page.getByRole('button', { name: 'Alert me after closing the page' }).click()
  await expect(page.getByRole('button', { name: 'Turn off background alerts' })).toHaveAttribute('aria-pressed', 'true')
  expect(posts[0]).toEqual({ path: '/api/v1/push/subscriptions', body: { endpoint: 'https://fcm.googleapis.com/fcm/send/test', keys: { p256dh: 'p', auth: 'a' }, language: 'en' } })
  await page.getByRole('button', { name: 'Turn off background alerts' }).click()
  await expect(page.getByRole('button', { name: 'Alert me after closing the page' })).toBeVisible()
  expect(posts.at(-1)).toEqual({ path: '/api/v1/push/unsubscribe', body: { endpoint: 'https://fcm.googleapis.com/fcm/send/test' } })
})
