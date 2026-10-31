// Shows strong-activity alerts pushed by the Aurora Finder backend while the page is closed.
// It has no fetch handler, so it never caches or intercepts page requests.
self.addEventListener('push', event => {
  let message = { title: 'Aurora Finder', body: '', tag: 'aurora-finder', url: '/' }
  try { message = { ...message, ...event.data.json() } } catch { /* Keep the default text for an unreadable payload. */ }
  event.waitUntil(self.registration.showNotification(message.title, {
    body: message.body, tag: message.tag, icon: '/favicon.svg', data: { url: message.url },
  }))
})

self.addEventListener('notificationclick', event => {
  event.notification.close()
  const url = new URL(event.notification.data?.url || '/', self.location.origin).href
  event.waitUntil(self.clients.matchAll({ type: 'window', includeUncontrolled: true }).then(windows => {
    const open = windows.find(client => client.url.startsWith(self.location.origin))
    return open ? open.focus() : self.clients.openWindow(url)
  }))
})
