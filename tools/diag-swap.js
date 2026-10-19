// 근무변경 진단: 특정 날짜가 들어간 근무변경 기록·요청의 상태만 (이름 없이 근무자 ID만 출력 — 공개 저장소 로그)
const fs = require('fs');
const admin = require('firebase-admin');
admin.initializeApp({ credential: admin.credential.cert(JSON.parse(fs.readFileSync(process.argv[2], 'utf8'))) });
const DATES = (process.argv[3] || '').split(',').filter(Boolean);
const t = ms => ms ? new Date(ms + 9 * 3600e3).toISOString().slice(5, 16).replace('T', ' ') : '-';
(async () => {
  const T = admin.firestore().collection('teams').doc('gyeonggi');
  const evs = await T.collection('events').get();
  const hit = e => DATES.some(d => [e.aDate, e.bDate, e.date].includes(d));
  if (['color','memo'].includes(process.argv[4])) DATES.length = 0;
  console.log(`::notice::events total=${evs.size}`);
  for (const d of evs.docs) { const e = d.data(); if (!hit(e)) continue;
    console.log(`::notice::EVENT ${d.id} type=${e.type} a=${e.aId}@${e.aDate} b=${e.bId}@${e.bDate} shift=${e.shift} date=${e.date||''} member=${e.memberId||''} cover=${e.coverId||''} created=${t(e.createdAt)} canceled=${!!e.canceled}`); }
  const rq = await T.collection('requests').get();
  console.log(`::notice::requests total=${rq.size}`);
  for (const d of rq.docs) { const r = d.data(); const e = r.ev || {}; if (!hit(e)) continue;
    console.log(`::notice::REQ ${d.id} kind=${r.kind} from=${r.from} to=${r.to} status=${r.status} eventId=${r.eventId||''} a=${e.aId}@${e.aDate} b=${e.bId}@${e.bDate} shift=${e.shift} created=${t(r.createdAt)} decided=${t(r.decidedAt)}`); }
  if (process.argv[4] === 'color') {
    const cc = await T.collection('calcache').get();
    for (const d of cc.docs) { const x = d.data(); console.log(`::notice::CAL ${d.id} ver=${x.ver||''} at=${t(x.at)}`); const L = x.dbg || []; for (let i = 0; i < L.length; i += 6) console.log(`::notice::COLOR ${L.slice(i, i + 6).join(' | ')}`); }
  }
  if (process.argv[4] === 'memo') {   // 내용 없이 개수·날짜형식만
    const ms = await T.collection('memos').get();
    for (const d of ms.docs) { const n = d.data().notes || {}; const ks = Object.keys(n);
      console.log(`::notice::MEMO ${d.id} count=${ks.length} has1123=${'2026-11-23' in n} keyShapes=${[...new Set(ks.map(k => k.replace(/[0-9]/g, '9')))].join(',')} topKeys=${Object.keys(d.data()).join(',')}`); }
    const wl = await T.collection('widgetLinks').get();
    for (const d of wl.docs) { const x = d.data();
      let got = '';
      try { const r = await fetch('https://asia-northeast3-baejeon-shift.cloudfunctions.net/widgetData?t=' + d.id); const j = await r.json(); const days = j.days || {}; got = `days=${Object.keys(days).length} withMemo=${Object.values(days).filter(v => v.n).length} n1123=${!!(days['2026-11-23'] || {}).n}`; } catch (e) { got = 'fetch ' + e.message; }
      console.log(`::notice::LINK member=${x.memberId} ${got}`); }
  }
  process.exit(0);
})().catch(e => { console.log('::error::' + e.message); process.exit(1); });
