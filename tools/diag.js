// 알림 진단: 관리자별 등록된 기기 수와 최근 함수 기록 (이름·이메일·토큰은 출력하지 않음 — 공개 저장소 로그)
const fs = require('fs');
const admin = require('firebase-admin');
admin.initializeApp({ credential: admin.credential.cert(JSON.parse(fs.readFileSync(process.argv[2], 'utf8'))) });
(async () => {
  const db = admin.firestore();
  const us = await db.collection('teams').doc('gyeonggi').collection('users').get();
  let i = 0;
  for (const d of us.docs) {
    const u = d.data();
    if (!['main', 'sub', 'pending'].includes(u.role)) continue;
    const toks = await d.ref.collection('tokens').get();
    const list = toks.docs.map(t => { const x = t.data(); const ua = x.ua || ''; return (/Android/.test(ua) ? 'Android' : /iPhone|iPad/.test(ua) ? 'iOS' : /Windows/.test(ua) ? 'Windows' : 'other') + '@' + new Date(x.at || 0).toISOString().slice(0, 16); });
    console.log(`::notice::user#${++i} role=${u.role} createdAt=${u.at ? new Date(u.at).toISOString().slice(0,16) : '-'} tokens=${toks.size} [${list.join(', ')}]`);
  }
  process.exit(0);
})().catch(e => { console.log('::error::' + e.message); process.exit(1); });
