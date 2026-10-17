/* =====================================================================
   앱 연결층: Firebase(구글 로그인 · Firestore)
   - 데이터는 teams/<TEAM_ID>/... 아래에 저장
   - 역할: main(메인관리자) · sub(서브관리자) · member(팀원) · family(가족)
   ===================================================================== */
const FBV='10.12.2',FBB='https://www.gstatic.com/firebasejs/'+FBV+'/';
const TEAM=(window.TEAM_ID||'gyeonggi');
let FB=null,fsdb=null,auth=null,appRole=null,appUser=null,appUsers=[],claims={},invite=null,famCodes=[],ownMemos={},unsubs=[],dataStarted=false;
const ROLE_NAME={main:'메인관리자',sub:'서브관리자',member:'팀원',family:'가족'};
const isFamily=()=>appRole==='family';
const ownMid=()=>appUser&&(appRole==='family'?appUser.familyOf:appUser.memberId)||null;
const tref=(...p)=>FB.F.doc(fsdb,'teams',TEAM,...p);
const tcol=(...p)=>FB.F.collection(fsdb,'teams',TEAM,...p);
const rndCode=n=>{const A='ABCDEFGHJKLMNPQRSTUVWXYZ23456789';let s='';const r=crypto.getRandomValues(new Uint32Array(n));for(const x of r)s+=A[x%A.length];return s};

/* 기존 코드가 쓰던 db API(collection/doc/onSnapshot)를 Firestore로 연결 */
function makeDb(){
  const F=FB.F,base=['teams',TEAM];
  const col=name=>F.collection(fsdb,...base,...name.split('/'));
  const dref=path=>F.doc(fsdb,...base,...path.split('/'));
  const wrapCol=s=>({empty:s.empty,docs:s.docs.map(d=>({id:d.id,data:()=>d.data()}))});
  return {
    collection:name=>({
      onSnapshot:(cb,err)=>{const u=F.onSnapshot(col(name),s=>cb(wrapCol(s)),e=>{console.warn(name,e);err&&err(e)});unsubs.push(u);return u},
      add:async doc=>{const r=await F.addDoc(col(name),doc);return {id:r.id}}
    }),
    doc:path=>({
      set:d=>F.setDoc(dref(path),d),
      update:d=>F.updateDoc(dref(path),d),
      delete:()=>F.deleteDoc(dref(path)),
      onSnapshot:(cb,err)=>{const u=F.onSnapshot(dref(path),s=>cb({exists:s.exists(),id:s.id,data:()=>s.data()}),e=>{console.warn(path,e);err&&err(e)});unsubs.push(u);return u}
    })
  };
}
function stopListeners(){for(const u of unsubs)try{u()}catch(e){}unsubs=[];dataStarted=false}

/* 파일 저장 · 공유 (휴대폰에서는 공유창 → 카카오톡 선택 가능) */
async function appDownloads(){
  return {save:async({filename,data})=>{
    const blob=data instanceof Blob?data:new Blob([data]);
    const type=blob.type||(/\.png$/.test(filename)?'image/png':/\.pdf$/.test(filename)?'application/pdf':/\.json$/.test(filename)?'application/json':'application/octet-stream');
    const mobile=/Android|iPhone|iPad|iPod/i.test(navigator.userAgent);
    if(mobile&&window.File&&navigator.canShare){
      const file=new File([blob],filename,{type});
      if(navigator.canShare({files:[file]})){try{await navigator.share({files:[file],title:filename});return {status:'saved'}}catch(e){if(e&&e.name==='AbortError')throw {code:'declined'}}}
    }
    const a=document.createElement('a');a.href=URL.createObjectURL(blob);a.download=filename;document.body.appendChild(a);a.click();
    setTimeout(()=>{URL.revokeObjectURL(a.href);a.remove()},4000);return {status:'saved'};
  }};
}

/* ---------- 시작 화면 (로그인 · 초대 코드 · 본인 선택 · 팀 만들기) ---------- */
function gate(html){const g=$('#gate');if(html==null){g.hidden=true;document.body.classList.remove('gated');return}g.hidden=false;document.body.classList.add('gated');$('#gateBody').innerHTML=html}
const gLoading=t=>'<div class="g-spin"></div><p class="note">'+esc(t||'불러오는 중…')+'</p>';
function gErr(e){console.error(e);gate('<h2>문제가 생겼어요</h2><p class="note">'+esc(errText(e))+'</p><div class="btns"><button class="btn" data-g="reload">다시 시도</button><button class="btn ghost" data-g="logout">로그아웃</button></div>')}
function errText(e){const c=e&&e.code||'';
  if(/permission-denied/.test(c))return '권한이 없어요. 코드가 맞는지, 관리자가 권한을 바꾸지 않았는지 확인하세요.';
  if(/unavailable|network/.test(c))return '인터넷 연결을 확인하고 다시 시도하세요.';
  if(/popup-closed|cancelled-popup/.test(c))return '로그인 창이 닫혔어요. 다시 눌러 주세요.';
  if(/unauthorized-domain/.test(c))return '이 주소가 Firebase 로그인 허용 도메인에 없어요. Firebase 콘솔 → Authentication → 설정 → 승인된 도메인에 추가하세요.';
  return (e&&e.message)||String(e)}
const loginHtml=()=>'<div class="g-logo">근</div><h2>배전센터 근무표</h2><p class="note">팀원과 가족만 쓸 수 있어요. 구글 계정으로 로그인하세요.</p><button class="btn g-google" data-g="google"><span class="g-g">G</span>구글로 로그인</button><p class="note g-small">처음이면 로그인한 뒤 팀 초대 코드나 가족 초대 코드를 입력해요.</p>';
function joinHtml(msg){
  const q=new URLSearchParams(location.search);const pre=(q.get('join')||q.get('family')||'').toUpperCase();
  return '<h2>초대 코드 입력</h2><p class="note">팀원은 관리자에게 받은 <b>팀 초대 코드</b>, 가족은 가족에게 받은 <b>가족 초대 코드</b>를 입력하세요.</p>'+(msg?'<div class="msg bad">'+esc(msg)+'</div>':'')+
    '<input id="gCode" class="g-code" maxlength="12" autocomplete="off" autocapitalize="characters" placeholder="예: K7P2QX" value="'+esc(pre)+'">'+
    '<button class="btn" data-g="join">확인</button><p class="note g-small">'+esc(auth&&auth.currentUser?auth.currentUser.email:'')+' 계정으로 로그인했어요 · <button class="link" data-g="logout">다른 계정</button></p>';
}
function pickHtml(){
  const taken=new Set(Object.keys(claims));
  const list=byName().filter(m=>!taken.has(m.id));
  return '<h2>누구인가요?</h2><p class="note">본인 이름을 고르면 이 계정이 그 근무자로 연결돼요. 한 번만 고르면 되고, 잘못 골랐으면 관리자가 풀어줄 수 있어요.</p>'+
    (list.length?'<div class="pick">'+list.map(m=>'<button data-g="pick" data-id="'+m.id+'">'+esc(m.name)+'<small>'+teamLabel(m,todayN)+'</small></button>').join('')+'</div>':'<div class="msg warn">고를 수 있는 이름이 없어요. 관리자에게 문의하세요.</div>')+
    (appRole==='main'||appRole==='sub'?'<button class="link" data-g="nopick" style="align-self:center">근무자가 아니에요 (관리만 해요)</button>':'');
}
function setupHtml(){
  return '<h2>팀 만들기</h2><p class="note">아직 팀이 없어요. 지금 로그인한 계정이 <b>메인관리자</b>가 돼요.</p>'+
    '<label class="g-lab">팀 이름<input id="gTeamName" value="경기배전센터"></label>'+
    '<label class="g-lab">기존 근무표 데이터 (선택)<input id="gImport" type="file" accept=".json,application/json"><small>근무표 아티팩트의 관리 → 설정 → "앱으로 옮기기"에서 받은 파일이에요. 없으면 비워 두세요.</small></label>'+
    '<button class="btn" data-g="setup">팀 만들기</button><p class="note g-small">'+esc(auth.currentUser.email||'')+' · <button class="link" data-g="logout">다른 계정</button></p>';
}

$('#gate').addEventListener('click',async e=>{
  const b=e.target.closest('[data-g]');if(!b||b.disabled)return;const act=b.dataset.g;
  try{
    if(act==='reload')location.reload();
    else if(act==='logout'){stopListeners();await FB.Au.signOut(auth)}
    else if(act==='google'){b.disabled=true;await googleLogin()}
    else if(act==='join'){b.disabled=true;await joinWithCode(($('#gCode').value||'').trim().toUpperCase());b.disabled=false}
    else if(act==='pick'){b.disabled=true;await claimMember(b.dataset.id)}
    else if(act==='nopick'){sessionSkipPick=true;gate(null);me='';render()}
    else if(act==='setup'){b.disabled=true;await createTeam()}
  }catch(err){b.disabled=false;if(act==='join'){gate(joinHtml(/permission|not-found/.test(err&&err.code||'')?'코드가 맞지 않아요. 다시 확인해 주세요.':errText(err)))}else if(act==='pick'){gate(pickHtml());alertMsg(/permission|already/.test(err&&err.code||'')?'방금 다른 계정이 그 이름을 골랐어요. 관리자에게 문의하세요.':errText(err))}else gErr(err)}
});
function alertMsg(t){const g=$('#gateBody');const d=document.createElement('div');d.className='msg bad';d.textContent=t;g.prepend(d)}

async function googleLogin(){
  const p=new FB.Au.GoogleAuthProvider();p.setCustomParameters({prompt:'select_account'});
  try{await FB.Au.signInWithPopup(auth,p)}
  catch(e){if(/popup-blocked|operation-not-supported/.test(e.code||''))await FB.Au.signInWithRedirect(auth,p);else throw e}
}

/* 팀 초대 코드 → 팀원, 가족 초대 코드 → 가족 */
async function joinWithCode(code){
  if(!code){gate(joinHtml('코드를 입력하세요.'));return}
  const F=FB.F,u=auth.currentUser;const base={name:u.displayName||'',email:u.email||'',photo:u.photoURL||'',createdAt:Date.now(),joinCode:code};
  let fam=null;try{const s=await F.getDoc(tref('familyCodes',code));if(s.exists())fam=s.data()}catch(e){}
  if(fam&&fam.memberId){await F.setDoc(tref('users',u.uid),{...base,role:'family',familyOf:fam.memberId,memberId:null})}
  else await F.setDoc(tref('users',u.uid),{...base,role:'member',memberId:null});
  /* 이후는 내 사용자 문서 구독이 이어받음 */
}
async function claimMember(mid){
  const F=FB.F,b=F.writeBatch(fsdb);
  b.set(tref('claims',mid),{uid:auth.currentUser.uid,at:Date.now()});
  b.update(tref('users',auth.currentUser.uid),{memberId:mid});
  await b.commit();
}
async function createTeam(){
  const F=FB.F,u=auth.currentUser;const name=($('#gTeamName').value||'').trim()||'경기배전센터';
  const file=$('#gImport').files&&$('#gImport').files[0];let data=null;
  if(file){try{data=JSON.parse(await file.text())}catch(e){alertMsg('파일을 읽지 못했어요. 근무표에서 받은 .json 파일인지 확인하세요.');$('[data-g=setup]').disabled=false;return}}
  gate(gLoading('팀을 만드는 중…'));
  const b=F.writeBatch(fsdb);
  b.set(tref(),{name,mainUid:u.uid,createdAt:Date.now()});
  b.set(tref('users',u.uid),{role:'main',name:u.displayName||'',email:u.email||'',photo:u.photoURL||'',memberId:null,createdAt:Date.now()});
  b.set(tref('private','invite'),{code:rndCode(6),at:Date.now()});
  await b.commit();
  if(data)await importData(data);
  location.reload();
}
/* 아티팩트에서 내보낸 데이터 가져오기 */
async function importData(d){
  const F=FB.F;const ops=[];
  const put=(path,doc)=>ops.push([path,doc]);
  for(const m of d.members||[]){const{id,...r}=m;if(id)put(['members',id],r)}
  for(const a of d.assignments||[])if(a&&a.date)put(['assignments',a.date],a);
  for(const x of d.budgets||[]){const{id,...r}=x;if(id)put(['budgets',id],r)}
  for(const x of d.events||[]){const{_id,...r}=x;put(['events',_id||rndCode(20)],r)}
  for(const x of d.requests||[]){const{_id,...r}=x;put(['requests',_id||rndCode(20)],r)}
  if(Array.isArray(d.holidays))put(['config','holidays'],{dates:d.holidays});
  for(let i=0;i<ops.length;i+=400){const b=F.writeBatch(fsdb);for(const[p,doc]of ops.slice(i,i+400))b.set(tref(...p),doc);await b.commit()}
  if(d.memos&&d.memoOwner)pendingMemoImport={owner:d.memoOwner,notes:d.memos};
  try{localStorage.setItem('bj-memo-import',JSON.stringify(pendingMemoImport))}catch(e){}
}
let pendingMemoImport=null;

/* ---------- 로그인 후 ---------- */
async function afterLogin(u){
  gate(gLoading());
  const F=FB.F;
  const ts=await F.getDoc(tref());
  if(!ts.exists()){gate(setupHtml());return}
  let first=true;
  unsubs.push(F.onSnapshot(tref('users',u.uid),s=>{
    if(!s.exists()){appUser=null;appRole=null;stopListeners();gate(joinHtml(first?'':'팀에서 나가졌거나 연결이 해제됐어요. 초대 코드를 다시 입력하세요.'));first=false;watchMe(u);return}
    first=false;const d=s.data();const prevRole=appRole,prevMid=appUser&&appUser.memberId;appUser=d;appRole=d.role;
    isAdmin=appRole==='main'||appRole==='sub';canWrite=isAdmin;live=true;
    if(isFamily()){me=d.familyOf}else if(d.memberId){me=d.memberId}
    if(!dataStarted)startData();
    else if(prevRole!==appRole||prevMid!==d.memberId){restartRoleData()}
    checkPick();dataChanged();
  },e=>gErr(e)));
}
function watchMe(u){ /* 가입 전: 내 문서가 생기면 다시 시작 */
  const F=FB.F;const un=F.onSnapshot(tref('users',u.uid),s=>{if(s.exists()){un();afterLogin(u)}},()=>{});
}
function checkPick(){
  if(!appUser)return;
  if(isFamily()||appUser.memberId||sessionSkipPick){gate(null);return}
  if(!membersLoaded){gate(gLoading());return}
  gate(pickHtml());
}
let sessionSkipPick=false,membersLoaded=false,roleUnsubs=[];

function startData(){
  dataStarted=true;
  db.collection('members').onSnapshot(s=>{membersLoaded=true;if(!s.empty)members=s.docs.map(x=>({id:x.id,...x.data()}));checkPick();dataChanged()},()=>{membersLoaded=true;checkPick()});
  db.collection('events').onSnapshot(s=>{events=s.docs.map(x=>({_id:x.id,...x.data()}));dataChanged()});
  db.collection('assignments').onSnapshot(s=>{assignments=s.docs.map(x=>x.data()).filter(x=>x&&x.date&&x.teams).sort((x,y)=>x.date<y.date?-1:1);orgDraft=null;dataChanged()});
  db.collection('budgets').onSnapshot(s=>{budgets=s.docs.map(x=>({id:x.id,...x.data()}));dataChanged()});
  db.doc('config/holidays').onSnapshot(s=>{if(s.exists&&Array.isArray(s.data().dates)){holidays=new Set(s.data().dates);dataChanged()}});
  restartRoleData();
}
function restartRoleData(){
  for(const u of roleUnsubs)try{u()}catch(e){}roleUnsubs=[];
  const keep=u=>{roleUnsubs.push(u);return u};
  requests=[];appUsers=[];claims={};invite=null;famCodes=[];ownMemos={};memos={};
  const F=FB.F;
  if(!isFamily()){
    keep(db.collection('requests').onSnapshot(s=>{requests=s.docs.map(x=>({_id:x.id,...x.data()}));dataChanged()}));
    keep(db.collection('users').onSnapshot(s=>{appUsers=s.docs.map(x=>({uid:x.id,...x.data()}));dataChanged()}));
    keep(db.collection('claims').onSnapshot(s=>{claims={};s.docs.forEach(x=>claims[x.id]=x.data());checkPick();dataChanged()}));
    if(isAdmin)keep(db.doc('private/invite').onSnapshot(s=>{invite=s.exists?s.data():null;dataChanged()}));
    if(appUser.memberId)keep(F.onSnapshot(F.query(tcol('familyCodes'),F.where('by','==',uid)),s=>{famCodes=s.docs.map(x=>({code:x.id,...x.data()}));renderAcct()},()=>{}));
  }
  const mm=ownMid();
  if(mm)keep(db.doc('memos/'+mm).onSnapshot(s=>{ownMemos=(s.exists&&s.data().notes)||{};syncMemos();dataChanged()}));
  /* 아티팩트에서 옮겨온 메모 한 번 넣기 */
  try{const p=JSON.parse(localStorage.getItem('bj-memo-import')||'null');if(p&&mm&&p.owner===mm&&!isFamily()){localStorage.removeItem('bj-memo-import');F.setDoc(tref('memos',mm),{notes:p.notes},{merge:true}).catch(()=>{})}}catch(e){}
}
function syncMemos(){memos=me&&me===ownMid()?ownMemos:{}}

/* ---------- 부팅 ---------- */
(async()=>{
  /* 카카오톡 안에서 열면 구글 로그인이 막혀서 바깥 브라우저로 */
  if(/KAKAOTALK/i.test(navigator.userAgent)){location.href='kakaotalk://web/openExternal?url='+encodeURIComponent(location.href);gate('<h2>브라우저에서 열어 주세요</h2><p class="note">카카오톡 안에서는 로그인이 안 돼요. 오른쪽 아래 ⋮ → "다른 브라우저로 열기"를 눌러 주세요.</p>');return}
  if(/NAVER|Instagram|FBAN|FBAV|Line\//i.test(navigator.userAgent)){gate('<h2>브라우저에서 열어 주세요</h2><p class="note">이 앱 안의 화면에서는 구글 로그인이 막혀 있어요. 메뉴에서 "다른 브라우저로 열기"(크롬·사파리)를 눌러 주세요.</p>');return}
  const cfg=window.FIREBASE_CONFIG;
  if(!cfg||!cfg.apiKey||/YOUR|여기/.test(cfg.apiKey)){gate('<h2>설정이 필요해요</h2><p class="note">config.js에 Firebase 설정값이 아직 없어요.</p>');return}
  gate(gLoading());
  try{const [A,Au,F]=await Promise.all([import(FBB+'firebase-app.js'),import(FBB+'firebase-auth.js'),import(FBB+'firebase-firestore.js')]);FB={A,Au,F}}
  catch(e){gate('<h2>연결할 수 없어요</h2><p class="note">인터넷 연결을 확인하고 다시 열어 주세요.</p><button class="btn" data-g="reload">다시 시도</button>');return}
  const app=FB.A.initializeApp(cfg);
  auth=FB.Au.getAuth(app);auth.languageCode='ko';
  try{fsdb=FB.F.initializeFirestore(app,{ignoreUndefinedProperties:true,localCache:FB.F.persistentLocalCache({tabManager:FB.F.persistentMultipleTabManager()})})}
  catch(e){fsdb=FB.F.initializeFirestore(app,{ignoreUndefinedProperties:true})}
  db=makeDb();
  try{await FB.Au.getRedirectResult(auth)}catch(e){}
  FB.Au.onAuthStateChanged(auth,u=>{
    stopListeners();for(const x of roleUnsubs)try{x()}catch(e){}roleUnsubs=[];
    appUser=null;appRole=null;
    if(!u){uid=null;gate(loginHtml());return}
    uid=u.uid;afterLogin(u).catch(gErr);
  });
})();

/* ---------- 내 정보 (가족 초대 · 로그아웃) ---------- */
$('#acctBtn').onclick=()=>{$('#acct').hidden=false;renderAcct()};
$('#acct').addEventListener('click',async e=>{
  if(e.target.id==='acct'||e.target.closest('[data-ac=close]')){$('#acct').hidden=true;return}
  const b=e.target.closest('[data-ac]');if(!b)return;const F=FB.F;
  try{
    if(b.dataset.ac==='logout'){$('#acct').hidden=true;stopListeners();await FB.Au.signOut(auth)}
    else if(b.dataset.ac==='famNew'){b.disabled=true;const code=rndCode(8);await F.setDoc(tref('familyCodes',code),{memberId:appUser.memberId,by:uid,at:Date.now()})}
    else if(b.dataset.ac==='famDel'){if(confirm('이 초대 코드를 지울까요? 이미 가입한 가족은 그대로예요.'))await F.deleteDoc(tref('familyCodes',b.dataset.code))}
    else if(b.dataset.ac==='famShare'){shareInvite('family',b.dataset.code)}
    else if(b.dataset.ac==='famOut'){if(confirm('이 가족의 연결을 끊을까요? 더 이상 근무표를 볼 수 없어요.'))await F.deleteDoc(tref('users',b.dataset.uid))}
  }catch(err){alert(errText(err))}
});
function shareInvite(kind,code){
  const url=location.origin+location.pathname+'?'+(kind==='family'?'family':'join')+'='+code;
  const text=kind==='family'?'배전센터 근무표 가족 초대예요. 구글로 로그인하고 코드 '+code+' 를 입력하세요.':'배전센터 근무표 팀 초대예요. 구글로 로그인하고 코드 '+code+' 를 입력하세요.';
  if(navigator.share)navigator.share({title:'배전센터 근무표',text,url}).catch(()=>{});
  else{navigator.clipboard&&navigator.clipboard.writeText(text+'\n'+url);alert('초대 문구를 복사했어요. 카톡에 붙여 넣으세요.\n\n'+text+'\n'+url)}
}
function renderAcct(){
  const box=$('#acctBody');if(!box||$('#acct').hidden)return;
  if(!appUser){box.innerHTML='';return}
  const u=auth&&auth.currentUser;
  let h='<div class="acct-me">'+(u&&u.photoURL?'<img src="'+esc(u.photoURL)+'" alt="">':'')+'<div><b>'+esc((u&&u.displayName)||'')+'</b><small>'+esc((u&&u.email)||'')+'</small></div><span class="tag 변경">'+ROLE_NAME[appRole]+'</span></div>';
  if(isFamily())h+='<p class="note">'+esc(nm(appUser.familyOf))+' 님의 근무를 보고 있어요.</p>';
  else h+='<p class="note">연결된 근무자: <b style="color:var(--fg)">'+(appUser.memberId?esc(nm(appUser.memberId)):'없음')+'</b></p>';
  if(!isFamily()&&appUser.memberId){
    const fams=appUsers.filter(x=>x.role==='family'&&x.familyOf===appUser.memberId);
    h+='<h3>가족과 공유</h3><p class="note" style="margin:0">가족은 내 근무(일·주·월)와 내 메모만 볼 수 있어요. 코드는 한 가족에 하나씩 만들어 보내세요.</p>';
    h+=famCodes.length?'<div class="list">'+famCodes.map(c=>'<div class="item"><div class="t"><b class="mono">'+c.code+'</b><small>'+new Date(c.at).toLocaleDateString('ko-KR')+' 만듦</small></div><span class="btns"><button class="btn" data-ac="famShare" data-code="'+c.code+'">보내기</button><button class="btn ghost" data-ac="famDel" data-code="'+c.code+'">삭제</button></span></div>').join('')+'</div>':'';
    h+='<button class="btn ghost" data-ac="famNew">가족 초대 코드 만들기</button>';
    if(fams.length)h+='<div class="list">'+fams.map(f=>'<div class="item"><div class="t"><b>'+esc(f.name||'가족')+'</b><small>'+esc(f.email||'')+'</small></div><button class="btn ghost" data-ac="famOut" data-uid="'+f.uid+'">연결 끊기</button></div>').join('')+'</div>';
  }
  h+='<h3>홈 화면에 추가</h3><p class="note" style="margin:0">아이폰: 사파리 아래 공유 버튼 → "홈 화면에 추가". 안드로이드: 크롬 ⋮ → "홈 화면에 추가" 또는 "앱 설치".</p>';
  h+='<div class="btns" style="margin-top:6px"><button class="btn ghost" data-ac="logout">로그아웃</button><button class="btn ghost" data-ac="close">닫기</button></div>';
  box.innerHTML=h;
}

/* ---------- 관리: 팀 초대 코드 · 사용자와 권한 ---------- */
async function renderLinks(){
  const box=$('#linkCard');if(!box)return;
  if(!appUser){box.innerHTML='';return}
  const order={main:0,sub:1,member:2,family:3};
  const rows=appUsers.slice().sort((a,b)=>(order[a.role]-order[b.role])||String(a.memberId?nm(a.memberId):a.name).localeCompare(String(b.memberId?nm(b.memberId):b.name),'ko'));
  let h='<h2>팀 초대</h2>';
  if(invite)h+='<div class="invite"><span class="note">팀 초대 코드</span><b class="mono">'+esc(invite.code)+'</b><span class="btns"><button class="btn" data-ad="inviteShare">보내기</button><button class="btn ghost" data-ad="inviteNew">새 코드</button></span></div><p class="note" style="margin:0">팀원은 구글로 로그인한 뒤 이 코드를 넣고 본인 이름을 골라요. 새 코드를 만들면 예전 코드는 더 이상 안 돼요(이미 가입한 사람은 그대로).</p>';
  h+='<h2 style="margin-top:6px">사용자 · 권한</h2><p class="note" style="margin:0">메인관리자는 서브관리자를 지정·해제하고, 메인관리자를 다른 사람에게 넘길 수 있어요. 관리자는 연결 해제와 내보내기를 할 수 있어요.</p>';
  h+='<div class="list">'+rows.map(x=>{
    const self=x.uid===uid;const who=x.role==='family'?esc(x.name||'가족')+' <small style="display:inline">('+esc(nm(x.familyOf))+' 가족)</small>':(x.memberId?esc(nm(x.memberId)):'<span class="note">근무자 미선택</span>');
    const acts=[];
    if(!self&&appRole==='main'&&x.role!=='family'){
      if(x.role==='member')acts.push('<button class="btn ghost" data-ad="toSub" data-uid="'+x.uid+'">서브관리자로</button>');
      if(x.role==='sub')acts.push('<button class="btn ghost" data-ad="toMember" data-uid="'+x.uid+'">서브 해제</button>');
      acts.push('<button class="btn ghost" data-ad="toMain" data-uid="'+x.uid+'">메인 넘기기</button>');
    }
    const canManage=!self&&(appRole==='main'||(appRole==='sub'&&(x.role==='member'||x.role==='family')));
    if(canManage&&x.memberId)acts.push('<button class="btn ghost" data-ad="unlink" data-uid="'+x.uid+'">연결 해제</button>');
    if(canManage)acts.push('<button class="btn ghost" data-ad="remove" data-uid="'+x.uid+'">내보내기</button>');
    return '<div class="item" style="flex-wrap:wrap"><div class="t"><b>'+who+'</b> <span class="tag '+(x.role==='main'?'대근':x.role==='sub'?'변경':'지원')+'">'+ROLE_NAME[x.role]+'</span>'+(self?' <span class="tag 미정">나</span>':'')+'<small>'+esc(x.email||'')+'</small></div>'+(acts.length?'<span class="btns">'+acts.join('')+'</span>':'')+'</div>'}).join('')+'</div>';
  box.innerHTML=h;
}
$('#linkCard').addEventListener('click',async e=>{
  const b=e.target.closest('[data-ad]');if(!b||b.disabled)return;const F=FB.F;const x=appUsers.find(u=>u.uid===b.dataset.uid);const who=x?(x.memberId?nm(x.memberId):(x.name||x.email)):'';
  try{
    switch(b.dataset.ad){
      case 'inviteShare':shareInvite('team',invite.code);break;
      case 'inviteNew':if(confirm('새 초대 코드를 만들까요? 예전 코드는 더 이상 쓸 수 없어요.'))await F.setDoc(tref('private','invite'),{code:rndCode(6),at:Date.now()});break;
      case 'toSub':if(confirm(who+' 님을 서브관리자로 지정할까요?'))await F.updateDoc(tref('users',x.uid),{role:'sub'});break;
      case 'toMember':if(confirm(who+' 님의 서브관리자 권한을 회수할까요?'))await F.updateDoc(tref('users',x.uid),{role:'member'});break;
      case 'toMain':if(confirm(who+' 님에게 메인관리자를 넘길까요?\n넘기면 나는 서브관리자가 돼요.')){const w=F.writeBatch(fsdb);w.update(tref('users',x.uid),{role:'main'});w.update(tref(),{mainUid:x.uid});w.update(tref('users',uid),{role:'sub'});await w.commit()}break;
      case 'unlink':if(confirm(who+' 님 계정과 근무자 연결을 풀까요? 다음에 들어오면 이름을 다시 골라요.')){const w=F.writeBatch(fsdb);w.update(tref('users',x.uid),{memberId:null});if(claims[x.memberId]&&claims[x.memberId].uid===x.uid)w.delete(tref('claims',x.memberId));await w.commit()}break;
      case 'remove':if(confirm(who+' 님을 팀에서 내보낼까요? 다시 들어오려면 초대 코드가 필요해요.')){const w=F.writeBatch(fsdb);if(x.memberId&&claims[x.memberId]&&claims[x.memberId].uid===x.uid)w.delete(tref('claims',x.memberId));w.delete(tref('users',x.uid));await w.commit()}break;
    }
  }catch(err){alert('처리하지 못했어요: '+errText(err))}
});

/* 앱에서 "나" 고르기는 화면 전환만 (관리자) — 계정 연결은 가입할 때 한 번 */
async function linkMe(id){
  if(appUser&&!isAdmin&&!isFamily()&&appUser.memberId&&id!==appUser.memberId)return;
  me=id;syncMemos();render();
}
async function saveMemo(day,text){
  const mm=ownMid();if(!mm||isFamily()||me!==mm)return;
  const notes={...ownMemos};if(text)notes[day]=text;else delete notes[day];ownMemos=notes;syncMemos();
  try{await FB.F.setDoc(tref('memos',mm),{notes})}catch(e){reqFail(e)}
  render();
}
function applyRoleUI(){
  const fam=isFamily();
  document.body.classList.toggle('family',fam);
  document.querySelectorAll('nav.tabs button').forEach(b=>{if(b.dataset.tab!=='my')b.hidden=fam||(b.id==='tabAdmin'&&!(isAdmin&&!viewerPreview))});
  const sel=$('#meSel');sel.disabled=!!appUser&&!isAdmin;
  $('#meLab').hidden=fam;
  $('#famLab').hidden=!fam;if(fam)$('#famLab').textContent=nm(appUser.familyOf)+' 님 근무';
}
