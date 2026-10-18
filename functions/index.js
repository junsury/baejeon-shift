/* 배전센터 근무표 — 푸시 알림 서버 (Firestore 변화 → 휴대폰 알림)
   알림 받을 곳: teams/{t}/users/{uid}/tokens/{token} */
const functions = require('firebase-functions/v1');
const admin = require('firebase-admin');
admin.initializeApp();
const db = admin.firestore();
const REGION = 'asia-northeast3';
const fn = functions.region(REGION).firestore;

const WD = ['일', '월', '화', '수', '목', '금', '토'];
const md = s => { if (!s) return ''; const [y, m, d] = s.split('-').map(Number); const dt = new Date(Date.UTC(y, m - 1, d)); return m + '/' + d + '(' + WD[dt.getUTCDay()] + ')'; };
const SH = { '주': '주간', '야': '야간', T: 'TSO' };
const LT = { '연차': '연차', '경조': '경조휴가', '대체': '휴일의대체', '시간': '시간휴가' };

async function names(t) {
  const s = await db.collection('teams').doc(t).collection('members').get();
  const m = {}; s.forEach(d => { m[d.id] = d.data().name || d.id; }); return id => m[id] || id || '';
}
function desc(ev, nm) {
  if (!ev) return '';
  if (ev.type === 'swap' || ev.aId) return md(ev.aDate) + ' ' + nm(ev.aId) + ' ↔ ' + md(ev.bDate) + ' ' + nm(ev.bId) + ' ' + (SH[ev.shift] || '') + ' 맞교환';
  const cover = ev.coverId ? (ev.coverType === 'tso' ? ' · 근무지원 ' : ' · 대근 ') + nm(ev.coverId) : '';
  return md(ev.date) + ' ' + nm(ev.memberId) + ' ' + (SH[ev.shift] || '') + ' ' + (LT[ev.leaveType || '연차'] || '') + cover;
}
/* 근무자 id → 그 사람 계정들 */
async function uidsOfMembers(t, mids) {
  const want = new Set(mids.filter(Boolean)); if (!want.size) return [];
  const s = await db.collection('teams').doc(t).collection('users').where('role', 'in', ['main', 'sub', 'member']).get();
  return s.docs.filter(d => want.has(d.data().memberId)).map(d => d.id);
}
async function adminUids(t) {
  const s = await db.collection('teams').doc(t).collection('users').where('role', 'in', ['main', 'sub']).get();
  return s.docs.map(d => d.id);
}
async function send(t, uids, msg) {
  uids = [...new Set(uids)]; if (!uids.length) { console.log('push', msg.title, 'no recipients'); return; }
  const refs = [];
  for (const u of uids) { const s = await db.collection('teams').doc(t).collection('users').doc(u).collection('tokens').get(); s.forEach(d => refs.push(d)); }
  if (!refs.length) { console.log('push', msg.title, 'recipients', uids.length, 'but no devices'); return; }
  const res = await admin.messaging().sendEachForMulticast({
    tokens: refs.map(r => r.id),
    data: { title: msg.title, body: msg.body || '', url: msg.url || './', tag: msg.tag || '' },
    webpush: { headers: { Urgency: 'high', TTL: '86400' } },
  });
  console.log('push', msg.title, 'devices', refs.length, 'ok', res.successCount, 'fail', res.failureCount,
    res.responses.filter(r => r.error).map(r => r.error.code).join(','));
  /* 더 이상 안 쓰는 기기는 정리 */
  await Promise.all(res.responses.map((r, i) => {
    const c = r.error && r.error.code;
    if (c === 'messaging/registration-token-not-registered' || c === 'messaging/invalid-registration-token' || c === 'messaging/invalid-argument') return refs[i].ref.delete();
    return null;
  }));
}

/* 요청·알림이 새로 생김 → 받는 사람 */
exports.onRequestCreated = fn.document('teams/{t}/requests/{id}').onCreate(async (snap, ctx) => {
  const r = snap.data(); const t = ctx.params.t; const nm = await names(t);
  const uids = await uidsOfMembers(t, [r.to]);
  let title, body;
  if (r.status === 'pending') {
    title = r.kind === 'swap' ? '근무변경 요청' : (r.ev && r.ev.coverType === 'tso' ? '근무지원 요청' : '대근 요청');
    body = nm(r.from) + ' 님: ' + desc(r.ev, nm) + (r.note ? ' · "' + r.note + '"' : '');
  } else if (r.status === 'notice' && r.canceled) {
    title = '근태가 취소됐어요'; body = nm(r.from) + ' 님이 취소: ' + desc(r.ev, nm);
  } else if (r.status === 'notice') {
    title = '근무표가 바뀌었어요'; body = nm(r.from) + ' 님이 구두 합의로 반영: ' + desc(r.ev, nm) + (r.note ? ' · "' + r.note + '"' : '');
  } else return;
  await send(t, uids, { title, body, url: './?go=inbox', tag: 'rq-' + ctx.params.id });
});

/* 요청이 승인·거절됨 → 보낸 사람 */
exports.onRequestUpdated = fn.document('teams/{t}/requests/{id}').onUpdate(async (chg, ctx) => {
  const a = chg.before.data(), b = chg.after.data(); const t = ctx.params.t;
  if (a.status !== 'pending' || !['approved', 'rejected'].includes(b.status)) return;
  const nm = await names(t);
  const ok = b.status === 'approved';
  await send(t, await uidsOfMembers(t, [b.from]), {
    title: ok ? '요청이 승인됐어요' : '요청이 거절됐어요',
    body: nm(b.to) + ' 님이 ' + (ok ? '승인' : '거절') + ': ' + desc(b.ev, nm), url: './?go=inbox', tag: 'rq-' + ctx.params.id,
  });
});

/* 관리자가 근태를 등록함 → 관련된 사람 (본인 제외) */
exports.onEventCreated = fn.document('teams/{t}/events/{id}').onCreate(async (snap, ctx) => {
  const e = snap.data(); const t = ctx.params.t;
  if (e.src !== 'admin') return;
  const nm = await names(t);
  const mids = (e.type === 'swap' ? [e.aId, e.bId] : [e.memberId, e.coverId]).filter(m => m && m !== e.byMember);
  await send(t, await uidsOfMembers(t, mids), { title: '관리자가 근태를 등록했어요', body: desc(e, nm), url: './', tag: 'ev-' + ctx.params.id });
});

/* 가입 승인 요청 → 관리자 / 승인됨 → 본인 */
exports.onUserCreated = fn.document('teams/{t}/users/{u}').onCreate(async (snap, ctx) => {
  const d = snap.data(); const t = ctx.params.t;
  if (d.role !== 'pending') return;
  const nm = await names(t);
  await send(t, await adminUids(t), { title: '가입 승인 요청', body: nm(d.requestedMemberId) + ' 님으로 요청 (' + (d.email || d.name || '') + ')', url: './?go=admin', tag: 'join-' + ctx.params.u });
});
exports.onUserUpdated = fn.document('teams/{t}/users/{u}').onUpdate(async (chg, ctx) => {
  const a = chg.before.data(), b = chg.after.data(); const t = ctx.params.t;
  if (a.role === 'pending' && b.role === 'pending' && a.requestedMemberId !== b.requestedMemberId) return;
  if (a.role === 'pending' && b.role === 'member') await send(t, [ctx.params.u], { title: '가입이 승인됐어요', body: '이제 근무표를 쓸 수 있어요.', url: './' });
  if (a.role !== 'sub' && b.role === 'sub') await send(t, [ctx.params.u], { title: '서브관리자가 됐어요', body: '관리 탭을 쓸 수 있어요.', url: './' });
  if (a.role !== 'main' && b.role === 'main') await send(t, [ctx.params.u], { title: '메인관리자가 됐어요', body: '메인관리자 권한을 넘겨받았어요.', url: './' });
});

/* ---------------- 내일 근무 알림 (매일 저녁 8시) ---------------- */
const makeEngine = require('./engine');
async function loadTeam(t) {
  const T = db.collection('teams').doc(t);
  const [ms, as, es, hol] = await Promise.all([T.collection('members').get(), T.collection('assignments').get(), T.collection('events').get(), T.collection('config').doc('holidays').get()]);
  const members = ms.docs.map(d => ({ id: d.id, ...d.data() }));
  return { members, engine: makeEngine({ members, assignments: as.docs.map(d => d.data()), events: es.docs.map(d => ({ _id: d.id, ...d.data() })), holidayDates: hol.exists ? hol.data().dates : null }) };
}
const SHN = { '주': '주간 08:00', '야': '야간 18:00', T: 'TSO 08:00', '지원': '근무지원 08:00' };
function tomorrowText(E, members, mid, n) {
  const cells = E.dayCells(n); const c = cells[mid]; if (!c) return null;
  const nm = id => (members.find(m => m.id === id) || {}).name || '';
  const active = members.filter(m => m.active !== false && m.id !== mid);
  const mates = s => active.filter(m => { const x = cells[m.id]; return E.hasShift(x, s) || (s === '주' && x.code === '지원'); }).map(m => m.name).join(', ');
  const day = E.WD[E.dow(n)]; const md = (new Date(n * 864e5).getUTCMonth() + 1) + '/' + new Date(n * 864e5).getUTCDate() + '(' + day + ')';
  const notes = [];
  if (c.tags && c.tags.includes('변경')) notes.push(nm(c.with) + '와 맞교환');
  if (c.cover) notes.push(nm(c.for) + ' 대신 대근');
  if (c.code === '지원') notes.push(nm(c.for) + ' 대신 근무지원');
  if (c.part) notes.push('시간휴가 ' + c.part.from + '~' + c.part.to);
  const changed = notes.length ? ' · 변경됨' : '';
  if (c.code === '연') return { title: '내일 ' + md + ' 휴가', body: (c.by ? (c.byType === 'tso' ? '근무지원 ' : '대근 ') + nm(c.by) : '대체 근무자 미정') };
  if (c.cover && E.HOURS[c.code]) return { title: '내일 ' + md + ' 주간+야간 24시간' + changed, body: '주간 같이: ' + (mates('주') || '없음') + ' · 야간 같이: ' + (mates('야') || '없음') + ' · ' + notes.join(' · ') };
  const s = c.cover || c.code;
  if (SHN[s]) { const w = s === '지원' ? '주' : s; return { title: '내일 ' + md + ' ' + SHN[s] + changed, body: (s === 'T' ? '' : '같이: ' + (mates(w) || '없음')) + (notes.length ? (s === 'T' ? '' : ' · ') + notes.join(' · ') : '') }; }
  if (notes.length) return { title: '내일 ' + md + ' 휴무' + changed, body: notes.join(' · ') };
  return null; /* 원래 쉬는 날은 알림 안 보냄 */
}
exports.dailyReminder = functions.region(REGION).pubsub.schedule('0 20 * * *').timeZone('Asia/Seoul').onRun(async () => {
  const teams = await db.collection('teams').get();
  const kst = new Date(Date.now() + 9 * 3600e3); const today = Math.floor(Date.UTC(kst.getUTCFullYear(), kst.getUTCMonth(), kst.getUTCDate()) / 864e5);
  for (const td of teams.docs) {
    const t = td.id; const { members, engine } = await loadTeam(t);
    const us = await db.collection('teams').doc(t).collection('users').where('role', 'in', ['main', 'sub', 'member']).get();
    for (const u of us.docs) {
      const d = u.data(); if (!d.memberId || d.remind === false) continue;
      const msg = tomorrowText(engine, members, d.memberId, today + 1); if (!msg) continue;
      await send(t, [u.id], { ...msg, url: './', tag: 'tomorrow' });
    }
  }
});

/* ---------------- 자동 백업 (매일 새벽 3시, 30일 보관) ---------------- */
const BACKUP_COLS = ['members', 'assignments', 'budgets', 'events', 'requests', 'config'];
async function makeBackup(t) {
  const T = db.collection('teams').doc(t); const data = { version: 1, team: t, at: new Date().toISOString() };
  for (const c of BACKUP_COLS) { const s = await T.collection(c).get(); data[c] = s.docs.map(d => ({ _id: d.id, ...d.data() })); }
  const json = JSON.stringify(data); const CH = 900000; const parts = Math.ceil(json.length / CH) || 1;
  const kst = new Date(Date.now() + 9 * 3600e3); const id = kst.toISOString().slice(0, 10);
  const B = T.collection('backups').doc(id);
  const old = await B.collection('parts').get(); await Promise.all(old.docs.map(d => d.ref.delete()));
  for (let i = 0; i < parts; i++) await B.collection('parts').doc(String(i)).set({ data: json.slice(i * CH, (i + 1) * CH) });
  await B.set({ at: Date.now(), parts, size: json.length, counts: Object.fromEntries(BACKUP_COLS.map(c => [c, data[c].length])) });
  /* 30일 지난 백업 지우기 */
  const all = await T.collection('backups').orderBy('at', 'desc').get();
  for (const d of all.docs.slice(30)) { const ps = await d.ref.collection('parts').get(); await Promise.all(ps.docs.map(p => p.ref.delete())); await d.ref.delete(); }
  return id;
}
exports.dailyBackup = functions.region(REGION).pubsub.schedule('0 3 * * *').timeZone('Asia/Seoul').onRun(async () => {
  const teams = await db.collection('teams').get();
  for (const td of teams.docs) await makeBackup(td.id);
});
/* 관리자가 "지금 백업" 누르면 */
exports.backupNow = functions.region(REGION).https.onCall(async (data, ctx) => {
  const t = (data && data.team) || 'gyeonggi';
  if (!ctx.auth) throw new functions.https.HttpsError('unauthenticated', '로그인이 필요해요');
  const u = await db.collection('teams').doc(t).collection('users').doc(ctx.auth.uid).get();
  if (!u.exists || !['main', 'sub'].includes(u.data().role)) throw new functions.https.HttpsError('permission-denied', '관리자만 할 수 있어요');
  return { id: await makeBackup(t) };
});

/* ---------------- 캘린더 구독: 기능 없앰 ----------------
   예전에 구독한 캘린더가 빈 캘린더가 되도록 빈 일정만 돌려줌 (근무 일정이 캘린더에서 사라짐) */
exports.calendar = functions.region(REGION).https.onRequest((req, res) => {
  res.set('Content-Type', 'text/calendar; charset=utf-8');
  res.send(['BEGIN:VCALENDAR', 'VERSION:2.0', 'PRODID:-//baejeon-shift//KO', 'X-WR-CALNAME:근무표(사용 안 함)', 'END:VCALENDAR'].join('\r\n') + '\r\n');
});

/* ---------------- 갤럭시 위젯 데이터 (앱 위젯이 30분마다 가져감) ---------------- */
exports.widgetData = functions.region(REGION).https.onRequest(async (req, res) => {
  try {
    const tok = String(req.query.t || ''); const t = String(req.query.team || 'gyeonggi');
    if (!/^[A-Za-z0-9]{12,40}$/.test(tok)) { res.status(404).json({ error: 'bad token' }); return; }
    const link = await db.collection('teams').doc(t).collection('widgetLinks').doc(tok).get();
    if (!link.exists) { res.status(404).json({ error: 'unlinked' }); return; }
    const { memberId } = link.data();
    const { members, engine: E } = await loadTeam(t);
    const me = members.find(m => m.id === memberId);
    const kst = new Date(Date.now() + 9 * 3600e3); const today = Math.floor(Date.UTC(kst.getUTCFullYear(), kst.getUTCMonth(), kst.getUTCDate()) / 864e5);
    const hol = await db.collection('teams').doc(t).collection('config').doc('holidays').get();
    const holSet = new Set(hol.exists && Array.isArray(hol.data().dates) ? hol.data().dates : []);
    const others = members.filter(m => m.active !== false && m.id !== memberId);
    const memoDoc = await db.collection('teams').doc(t).collection('memos').doc(memberId).get();
    const notes = (memoDoc.exists && memoDoc.data().notes) || {};
    const days = {};
    for (let n = today - 365; n <= today + 365; n++) {
      const cells = E.dayCells(n); const c = cells[memberId]; if (!c) continue;
      const d = { c: c.code };
      if (c.cover) d.cv = c.cover;
      if ((c.tags && (c.tags.includes('변경') || c.tags.includes('대근'))) || c.code === '지원') d.ch = 1;
      if (holSet.has(E.ds(n))) d.h = 1;
      if (c.part) d.p = c.part.from + '~' + c.part.to;
      if (notes[E.ds(n)]) d.n = String(notes[E.ds(n)]).slice(0, 200);
      if (n >= today - 1 && n <= today + 7) {
        const s = c.cover || (c.code === '지원' ? '주' : c.code);
        if (s === '주' || s === '야') d.m = others.filter(m => { const x = cells[m.id]; return E.hasShift(x, s) || (s === '주' && x.code === '지원'); }).map(m => m.name);
      }
      days[E.ds(n)] = d;
    }
    res.set('Cache-Control', 'no-store');
    res.json({ v: 1, name: me ? me.name : '', gen: Date.now(), days });
  } catch (e) { console.error(e); res.status(500).json({ error: 'server' }); }
});

/* ---------------- 갤럭시 앱 → 내 휴대폰 캘린더 일정 (근무표 앱 '내 근무 · 월'에 보이기) ---------------- */
// 본인만 읽을 수 있는 곳(teams/{t}/calcache/{memberId})에 저장. 관리자도 못 봐요.
exports.widgetEvents = functions.region(REGION).https.onRequest(async (req, res) => {
  try {
    if (req.method !== 'POST') { res.status(405).end(); return; }
    const b = typeof req.body === 'object' && req.body ? req.body : JSON.parse(req.body || '{}');
    const tok = String(b.t || ''); const t = String(b.team || 'gyeonggi');
    if (!/^[A-Za-z0-9]{12,40}$/.test(tok)) { res.status(404).json({ error: 'bad token' }); return; }
    const link = await db.collection('teams').doc(t).collection('widgetLinks').doc(tok).get();
    if (!link.exists) { res.status(404).json({ error: 'unlinked' }); return; }
    const { memberId } = link.data();
    const ref = db.collection('teams').doc(t).collection('calcache').doc(memberId);
    if (!Array.isArray(b.items) || b.off) { await ref.delete(); res.json({ ok: true, cleared: true }); return; }
    const items = b.items.slice(0, 2000).map(x => ({
      d: String(x.d || '').slice(0, 10), t: String(x.t || '').slice(0, 80), h: String(x.h || '').slice(0, 5), c: Number(x.c) || 0, w: String(x.w || '').slice(0, 20), id: String(x.id || '').slice(0, 300),
    })).filter(x => /^\d{4}-\d\d-\d\d$/.test(x.d));
    await ref.set({ at: Date.now(), from: String(b.from || ''), to: String(b.to || ''), items });
    res.json({ ok: true, n: items.length });
  } catch (e) { console.error(e); res.status(500).json({ error: 'server' }); }
});

/* ---------------- 갤럭시 앱 공유: 웹에서 만든 이미지·PDF를 앱이 받아 휴대폰 공유창으로 ---------------- */
// 웹이 teams/{t}/shares/{id}(+ p/0,1,…에 나눠 담은 base64)를 쓰면, 앱이 이 주소로 받아 가요. 받은 뒤·30분 지나면 지워요.
exports.shareFile = functions.region(REGION).https.onRequest(async (req, res) => {
  try {
    const id = String(req.query.id || ''); const t = String(req.query.t || 'gyeonggi');
    if (!/^[A-Za-z0-9]{24,48}$/.test(id) || !/^[a-z0-9-]{1,40}$/.test(t)) { res.status(404).end(); return; }
    const col = db.collection('teams').doc(t).collection('shares');
    // 오래된 것 정리
    const old = await col.where('at', '<', Date.now() - 30 * 60e3).limit(20).get();
    for (const d of old.docs) { const ps = await d.ref.collection('p').get(); for (const p of ps.docs) await p.ref.delete(); await d.ref.delete(); }
    const ref = col.doc(id); const s = await ref.get();
    if (!s.exists) { res.status(404).end(); return; }
    const m = s.data(); const ps = await ref.collection('p').get();
    const parts = ps.docs.map(d => [Number(d.id), d.data().d || '']).sort((a, b) => a[0] - b[0]);
    if (parts.length !== m.n) { res.status(409).end(); return; }
    const buf = Buffer.from(parts.map(x => x[1]).join(''), 'base64');
    res.set('Content-Type', /^(image\/png|application\/pdf|image\/jpeg)$/.test(m.type) ? m.type : 'application/octet-stream');
    res.set('X-Name', encodeURIComponent(String(m.name || 'file').slice(0, 80)));
    res.set('Cache-Control', 'no-store');
    res.send(buf);
    for (const p of ps.docs) await p.ref.delete(); await ref.delete();
  } catch (e) { console.error(e); res.status(500).end(); }
});
