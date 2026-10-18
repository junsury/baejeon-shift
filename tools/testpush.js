// 시험 알림: 메인관리자 기기로 알림 하나 보내기 (토큰·이름은 출력하지 않음)
const fs = require('fs');
const admin = require('firebase-admin');
admin.initializeApp({ credential: admin.credential.cert(JSON.parse(fs.readFileSync(process.argv[2], 'utf8'))) });
(async () => {
  const T = admin.firestore().collection('teams').doc('gyeonggi');
  const us = await T.collection('users').where('role', '==', 'main').get();
  const refs = [];
  for (const u of us.docs) { const s = await u.ref.collection('tokens').get(); s.forEach(d => refs.push(d)); }
  console.log('::notice::devices ' + refs.length + ' [' + refs.map(r => /SamsungBrowser/.test(r.data().ua || '') ? 'Samsung' : /Android/.test(r.data().ua || '') ? 'Chrome' : 'other').join(',') + ']');
  if (!refs.length) process.exit(0);
  const res = await admin.messaging().sendEachForMulticast({
    tokens: refs.map(r => r.id),
    data: { title: '🔔 시험 알림', body: '근무표 알림이 잘 오는지 확인하는 알림이에요. 눌러서 앱이 열리는지 봐 주세요.', url: './?go=inbox', tag: 'test-' + Date.now() },
    webpush: { headers: { Urgency: 'high', TTL: '3600' } },
  });
  console.log('::notice::sent ok ' + res.successCount + ' fail ' + res.failureCount + ' ' + res.responses.filter(r => r.error).map(r => r.error.code).join(','));
  process.exit(0);
})().catch(e => { console.log('::error::' + e.message); process.exit(1); });
