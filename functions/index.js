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
  uids = [...new Set(uids)]; if (!uids.length) return;
  const refs = [];
  for (const u of uids) { const s = await db.collection('teams').doc(t).collection('users').doc(u).collection('tokens').get(); s.forEach(d => refs.push(d)); }
  if (!refs.length) return;
  const res = await admin.messaging().sendEachForMulticast({
    tokens: refs.map(r => r.id),
    data: { title: msg.title, body: msg.body || '', url: msg.url || './', tag: msg.tag || '' },
    webpush: { headers: { Urgency: 'high', TTL: '86400' } },
  });
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
