// 예전 주소(github.io)의 앱 캐시를 지우고 새 주소로 보내요.
self.addEventListener('install', () => self.skipWaiting());
self.addEventListener('activate', e => e.waitUntil((async () => {
  for (const k of await caches.keys()) await caches.delete(k);
  await self.registration.unregister();
  for (const c of await self.clients.matchAll({ type: 'window' })) {
    try { const u = new URL(c.url); c.navigate('https://baejeon-shift.web.app/' + u.search + u.hash); } catch (err) {}
  }
})()));
