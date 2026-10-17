// 앱 껍데기를 저장해 두고, 새 버전이 있으면 받아와요. (데이터는 Firestore가 따로 관리)
const CACHE = 'bj-shell-v5';
const SHELL = ['./', './index.html', './config.js', './manifest.webmanifest', './guide.html', './icons/icon-192.png', './icons/icon-512.png'];
self.addEventListener('install', e => { e.waitUntil(caches.open(CACHE).then(c => c.addAll(SHELL.map(u => new Request(u, { cache: 'reload' })))).then(() => self.skipWaiting())); });
self.addEventListener('activate', e => { e.waitUntil(caches.keys().then(ks => Promise.all(ks.filter(k => k !== CACHE).map(k => caches.delete(k)))).then(() => self.clients.claim())
  // 새 버전이 깔리면 열려 있는 화면을 바로 새로 고침
  .then(() => self.clients.matchAll({ type: 'window' })).then(cs => cs.forEach(c => { try { c.navigate(c.url); } catch (x) {} }))); });
self.addEventListener('fetch', e => {
  const req = e.request; if (req.method !== 'GET') return;
  const url = new URL(req.url);
  // 우리 앱 파일: 인터넷 먼저, 안 되면 저장본
  if (url.origin === location.origin) {
    e.respondWith(fetch(req.url, { cache: 'no-cache', credentials: 'same-origin' }).then(r => { const c = r.clone(); caches.open(CACHE).then(x => x.put(req, c)); return r; }).catch(() => caches.match(req).then(r => r || caches.match('./index.html'))));
    return;
  }
  // Firebase 라이브러리·글꼴: 저장본 먼저
  if (/gstatic\.com\/firebasejs|fonts\.(googleapis|gstatic)\.com|cdnjs\.cloudflare\.com/.test(url.href)) {
    e.respondWith(caches.match(req).then(r => r || fetch(req).then(res => { const c = res.clone(); caches.open(CACHE).then(x => x.put(req, c)); return res; })));
  }
});

// 푸시 알림: 서버(Cloud Functions)가 FCM으로 보낸 내용을 휴대폰 알림으로 띄움
self.addEventListener('push', e => {
  let p = {};
  try { p = e.data ? e.data.json() : {}; } catch (x) { p = { data: { title: '배전센터 근무표', body: e.data && e.data.text() } }; }
  const d = Object.assign({}, p.notification || {}, p.data || {});
  const title = d.title || '배전센터 근무표';
  e.waitUntil((async () => {
    await self.registration.showNotification(title, {
      body: d.body || '', icon: 'icons/icon-192.png', badge: 'icons/icon-192.png',
      tag: d.tag || undefined, renotify: !!d.tag, data: { url: d.url || './' }
    });
    if (self.navigator && 'setAppBadge' in self.navigator) { try { await self.navigator.setAppBadge(); } catch (x) {} }
  })());
});
self.addEventListener('notificationclick', e => {
  e.notification.close();
  const url = new URL((e.notification.data && e.notification.data.url) || './', self.registration.scope).href;
  e.waitUntil((async () => {
    const all = await clients.matchAll({ type: 'window', includeUncontrolled: true });
    for (const c of all) { if (c.url.startsWith(self.registration.scope)) { await c.focus(); try { c.navigate(url); } catch (x) {} return; } }
    await clients.openWindow(url);
  })());
});
