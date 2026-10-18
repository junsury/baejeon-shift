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
  console.log(`::notice::events total=${evs.size}`);
  for (const d of evs.docs) { const e = d.data(); if (!hit(e)) continue;
    console.log(`::notice::EVENT ${d.id} type=${e.type} a=${e.aId}@${e.aDate} b=${e.bId}@${e.bDate} shift=${e.shift} date=${e.date||''} member=${e.memberId||''} cover=${e.coverId||''} created=${t(e.createdAt)} canceled=${!!e.canceled}`); }
  const rq = await T.collection('requests').get();
  console.log(`::notice::requests total=${rq.size}`);
  for (const d of rq.docs) { const r = d.data(); const e = r.ev || {}; if (!hit(e)) continue;
    console.log(`::notice::REQ ${d.id} kind=${r.kind} from=${r.from} to=${r.to} status=${r.status} eventId=${r.eventId||''} a=${e.aId}@${e.aDate} b=${e.bId}@${e.bDate} shift=${e.shift} created=${t(r.createdAt)} decided=${t(r.decidedAt)}`); }
  process.exit(0);
})().catch(e => { console.log('::error::' + e.message); process.exit(1); });
