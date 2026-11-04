// A read this slow usually means the free-tier backend is waking from sleep, so the page can say so.
const SLOW_REQUEST_MS = 8000

const listeners = new Set<() => void>()
let slowRequests = 0

function notify() {
  listeners.forEach(listener => listener())
}

/** Returns the function that ends tracking; call it when the request settles. */
export function trackSlowRequest(delayMs = SLOW_REQUEST_MS) {
  let slow = false
  const timer = setTimeout(() => {
    slow = true
    slowRequests += 1
    notify()
  }, delayMs)
  return () => {
    clearTimeout(timer)
    if (!slow) return
    slow = false
    slowRequests -= 1
    notify()
  }
}

export function subscribeSlowRequests(listener: () => void) {
  listeners.add(listener)
  return () => { listeners.delete(listener) }
}

export function hasSlowRequest() {
  return slowRequests > 0
}
