// 앱 서명 열쇠: 처음 한 번 만들어 Firestore(ci/android, 관리자 서버만 접근 가능)에 보관하고 다음부터는 같은 열쇠를 써요.
// 같은 열쇠로 서명해야 휴대폰에서 '업데이트'로 설치돼요.
const fs = require('fs');
const crypto = require('crypto');
const { execFileSync } = require('child_process');
const admin = require('firebase-admin');
const [, , saPath, ksPath] = process.argv;
admin.initializeApp({ credential: admin.credential.cert(JSON.parse(fs.readFileSync(saPath, 'utf8'))) });
(async () => {
  const ref = admin.firestore().collection('ci').doc('android');
  const snap = await ref.get();
  let pw;
  if (snap.exists && snap.data().ks) {
    pw = snap.data().pw;
    fs.writeFileSync(ksPath, Buffer.from(snap.data().ks, 'base64'));
    console.log('기존 서명 열쇠 사용');
  } else {
    pw = crypto.randomBytes(18).toString('base64').replace(/[^A-Za-z0-9]/g, '') + 'Kx9';
    execFileSync('keytool', ['-genkeypair', '-alias', 'baejeon', '-keyalg', 'RSA', '-keysize', '2048', '-validity', '10000',
      '-keystore', ksPath, '-storetype', 'PKCS12', '-storepass', pw, '-keypass', pw,
      '-dname', 'CN=Baejeon Shift, O=Gyeonggi Baejeon Center, C=KR'], { stdio: 'inherit' });
    await ref.set({ ks: fs.readFileSync(ksPath).toString('base64'), pw, at: Date.now() });
    console.log('새 서명 열쇠를 만들어 보관했어요');
  }
  console.log('::add-mask::' + pw);
  fs.appendFileSync(process.env.GITHUB_ENV, 'KEYSTORE_PASSWORD=' + pw + '\nKEYSTORE_FILE=' + ksPath + '\n');
  process.exit(0);
})().catch(e => { console.error('::error::' + (e && e.message || e)); process.exit(1); });
