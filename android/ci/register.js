// Firebase 프로젝트에 안드로이드 앱(kr.baejeon.shift)과 서명 지문을 등록해요.
// Firebase Hosting이 /.well-known/assetlinks.json 을 이 정보로 만들어 줘서, 앱 화면에 주소창이 안 보이게 돼요.
const fs = require('fs');
const admin = require('firebase-admin');
const [, , saPath, fp, fp1] = process.argv;
admin.initializeApp({ credential: admin.credential.cert(JSON.parse(fs.readFileSync(saPath, 'utf8'))) });
(async () => {
  const pm = admin.projectManagement();
  const apps = await pm.listAndroidApps();
  let app = null;
  for (const a of apps) { const m = await a.getMetadata(); if (m.packageName === 'kr.baejeon.shift') app = a; }
  if (!app) { app = await pm.createAndroidApp('kr.baejeon.shift', '근무표 (갤럭시)'); console.log('안드로이드 앱 등록'); }
  const hex = fp.replace(/:/g, '').toLowerCase();
  const certs = await app.getShaCertificates();
  if (!certs.some(c => c.shaHash.toLowerCase() === hex)) { await app.addShaCertificate(pm.shaCertificate(hex)); console.log('서명 지문 등록'); }
  else console.log('서명 지문 이미 등록됨');
  // SHA-1도 등록 → 구글 로그인(캘린더 권한)에 쓰는 안드로이드 OAuth 클라이언트가 만들어져요
  if (fp1) {
    const h1 = fp1.replace(/:/g, '').toLowerCase();
    if (!certs.some(c => c.shaHash.toLowerCase() === h1)) { await app.addShaCertificate(pm.shaCertificate(h1)); console.log('SHA-1 등록'); }
  }
  process.exit(0);
})().catch(e => { console.log('::warning::앱 등록 실패: ' + (e && e.message || e)); process.exit(0); });
