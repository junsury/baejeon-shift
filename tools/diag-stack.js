// 근태 겹침 점검: 옛 계산(engine_prev) ↔ 새 계산(engine) 차이, 겹친 날, 서로 상쇄되는 맞교환 쌍. 이름 없이 ID·날짜만 (공개 로그)
const fs = require('fs');
const admin = require('firebase-admin');
admin.initializeApp({ credential: admin.credential.cert(JSON.parse(fs.readFileSync(process.argv[2], 'utf8'))) });
const OLD = require('./engine_prev.js'), NEW = require('../functions/engine.js');
(async () => {
  const T = admin.firestore().collection('teams').doc('gyeonggi');
  const [ms, as, es, hol] = await Promise.all([T.collection('members').get(), T.collection('assignments').get(), T.collection('events').get(), T.collection('config').doc('holidays').get()]);
  const members = ms.docs.map(d => ({ id: d.id, ...d.data() })), assignments = as.docs.map(d => d.data()), events = es.docs.map(d => ({ _id: d.id, ...d.data() }));
  const arg = { members, assignments, events, holidayDates: hol.exists ? hol.data().dates : null };
  const o = OLD(arg), n = NEW(arg);
  const sh = c => c.code + (c.cover ? '+' + c.cover : '') + (c.lv ? '(' + c.lv.s + '휴가)' : '');
  const dates = new Set(); for (const e of events) for (const d of [e.date, e.aDate, e.bDate]) if (d) dates.add(d);
  let diff = 0;
  for (const d of [...dates].sort()) { const k = o.dn(d); for (const kk of [k, k + 1]) { const a = o.dayCells(kk), b = n.dayCells(kk);
    for (const m of members) { if (sh(a[m.id]) !== sh(b[m.id])) { diff++; console.log(`::notice::DIFF ${o.ds(kk)} ${m.id} ${sh(a[m.id])} -> ${sh(b[m.id])}`); } } } }
  console.log(`::notice::events=${events.length} dates=${dates.size} diff=${diff}`);
  // 같은 사람·같은 날에 근태가 2개 이상 쌓인 곳
  const by = {}; for (const e of events) { const ps = e.type === 'swap' ? [[e.aId, e.aDate], [e.bId, e.aDate], [e.aId, e.bDate], [e.bId, e.bDate]] : [[e.memberId, e.date], [e.coverId, e.date]];
    for (const [p, d] of ps) if (p && d) (by[p + '@' + d] = by[p + '@' + d] || new Set()).add(e._id); }
  for (const [k, v] of Object.entries(by)) if (v.size > 1) console.log(`::notice::STACK ${k} n=${v.size} ${[...v].join(',')}`);
  // 정확히 반대인 맞교환 쌍
  const sw = events.filter(e => e.type === 'swap');
  for (let i = 0; i < sw.length; i++) for (let j = i + 1; j < sw.length; j++) { const x = sw[i], y = sw[j];
    const same = new Set([x.aId, x.bId]).size === 2 && [x.aId, x.bId].sort().join() === [y.aId, y.bId].sort().join() && [x.aDate, x.bDate].sort().join() === [y.aDate, y.bDate].sort().join() && x.shift === y.shift;
    if (same) console.log(`::notice::REVERSE ${x._id} ${y._id} ${x.aDate} ${x.bDate}`); }
  process.exit(0);
})().catch(e => { console.log('::error::' + e.message); process.exit(1); });
