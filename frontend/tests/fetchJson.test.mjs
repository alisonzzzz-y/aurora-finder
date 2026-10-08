import test from 'node:test'
import assert from 'node:assert/strict'
import { fetchJson } from '../src/api/fetchJson.ts'
import { ApiRequestError, transientRetryDelay } from '../src/api/requestError.ts'

test('returns parsed JSON and preserves HTTP status for rate-limit feedback', async (t) => {
  const fetch = t.mock.method(globalThis, 'fetch', async () => new Response('{"ok":true}'))
  assert.deepEqual(await fetchJson('https://example.test'), { ok: true })
  fetch.mock.mockImplementation(async () => new Response('', { status: 429 }))
  await assert.rejects(fetchJson('https://example.test'), error =>
    error instanceof ApiRequestError && error.status === 429 && transientRetryDelay(error, 0) === null)
})

test('stalled response body times out and is eligible for bounded retry', async (t) => {
  t.mock.method(globalThis, 'fetch', async (_url, { signal }) => ({
    ok: true,
    json: () => new Promise((_resolve, reject) => {
      signal.addEventListener('abort', () => reject(signal.reason), { once: true })
    }),
  }))
  await assert.rejects(fetchJson('https://example.test', {}, 'Unavailable', 10), error =>
    error instanceof ApiRequestError && error.status === 408 && transientRetryDelay(error, 0) === 10_000)
})

test('caller cancellation is preserved instead of becoming a timeout', async (t) => {
  const controller = new AbortController()
  t.mock.method(globalThis, 'fetch', (_url, { signal }) => new Promise((_resolve, reject) => {
    signal.addEventListener('abort', () => reject(signal.reason), { once: true })
  }))
  const request = fetchJson('https://example.test', { signal: controller.signal }, 'Unavailable', 1000)
  controller.abort()
  await assert.rejects(request, { name: 'AbortError' })
})

test('preserves record-service error code without exposing server details', async (t) => {
  t.mock.method(globalThis, 'fetch', async () => new Response(JSON.stringify({
    code: 'RUN_RECORD_UNAVAILABLE', detail: 'Internal database details',
  }), { status: 503 }))
  await assert.rejects(fetchJson('https://example.test', {}, 'Unavailable'), error =>
    error instanceof ApiRequestError && error.status === 503
      && error.code === 'RUN_RECORD_UNAVAILABLE' && error.message === 'Unavailable')
})
