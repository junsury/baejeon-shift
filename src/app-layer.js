/* =====================================================================
   앱 연결층: Firebase(구글 로그인 · 익명 로그인 · Firestore · 푸시)
   - 데이터는 teams/<TEAM_ID>/... 아래에 저장
   - 역할: main(메인관리자) · sub(서브관리자) · member(팀원) · family(가족, 로그인 없는 링크)
           pending(승인 대기) · rejected(거절됨)
   ===================================================================== */
const FBV='10.12.2',FBB='https://www.gstatic.com/firebasejs/'+FBV+'/';
const TEAM=(window.TEAM_ID||'gyeonggi');
let FB=null,fsdb=null,auth=null,appRole=null,appUser=null,appUsers=[],claims={},famLinks=[],ownMemos={},unsubs=[],dataStarted=false;
const ROLE_NAME={main:'메인관리자',sub:'서브관리자',member:'팀원',family:'가족',pending:'승인 대기',rejected:'거절됨'};
const isFamily=()=>appRole==='family';
window.__fam=isFamily;
let modeSet=false;
/* 간단 버전(과장 버전) 저장: 계정에 남겨서 다른 기기에서도 같게 */
window.__saveMode=v=>{modeSet=true;if(!auth||!auth.currentUser||!appUser||isFamily())return;try{FB.F.updateDoc(tref('users',auth.currentUser.uid),{mode:v?'simple':'full'}).catch(()=>{})}catch(e){}};
window.__afterSimple=()=>{try{applyRoleUI();renderAcct()}catch(e){}};
const ownMid=()=>appUser&&(appRole==='family'?appUser.familyOf:appUser.memberId)||null;
const tref=(...p)=>FB.F.doc(fsdb,'teams',TEAM,...p);
const tcol=(...p)=>FB.F.collection(fsdb,'teams',TEAM,...p);
const rndCode=n=>{const A='ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789';let s='';const r=crypto.getRandomValues(new Uint32Array(n));for(const x of r)s+=A[x%A.length];return s};
const famParam=()=>{const q=new URLSearchParams(location.search).get('f');if(q){try{localStorage.setItem('bj-fam',q)}catch(e){}return q}
  /* 홈 화면 아이콘으로 열면 주소에 토큰이 없어서, 한 번 연 링크를 기억해 둠 */
  try{return auth&&auth.currentUser&&!auth.currentUser.isAnonymous?null:localStorage.getItem('bj-fam')}catch(e){return null}};
const pendingUsers=()=>appUsers.filter(x=>x.role==='pending');

/* 기존 코드가 쓰던 db API(collection/doc/onSnapshot)를 Firestore로 연결 */
function makeDb(){
  const F=FB.F,base=['teams',TEAM];
  const col=name=>F.collection(fsdb,...base,...name.split('/'));
  const dref=path=>F.doc(fsdb,...base,...path.split('/'));
  const wrapCol=s=>({empty:s.empty,docs:s.docs.map(d=>({id:d.id,data:()=>d.data()}))});
  return {
    collection:name=>({
      onSnapshot:(cb,err)=>{const u=F.onSnapshot(col(name),s=>cb(wrapCol(s)),e=>{console.warn(name,e);listenErr(e);err&&err(e)});unsubs.push(u);return u},
      add:async doc=>{const r=await F.addDoc(col(name),doc);return {id:r.id}}
    }),
    doc:path=>({
      set:d=>F.setDoc(dref(path),d),
      update:d=>F.updateDoc(dref(path),d),
      delete:()=>F.deleteDoc(dref(path)),
      onSnapshot:(cb,err)=>{const u=F.onSnapshot(dref(path),s=>cb({exists:s.exists(),id:s.id,data:()=>s.data()}),e=>{console.warn(path,e);listenErr(e);err&&err(e)});unsubs.push(u);return u}
    })
  };
}
function stopListeners(){for(const u of unsubs)try{u()}catch(e){}unsubs=[];for(const u of roleUnsubs)try{u()}catch(e){}roleUnsubs=[];dataStarted=false}
/* 가족 링크가 끊기면 읽기 권한이 사라짐 */
function listenErr(e){if(isFamily()&&e&&/permission/.test(e.code||'')){stopListeners();gate(famGoneHtml())}}
const famGoneHtml=()=>'<div class="g-logo">근</div><h2>링크가 끊겼어요</h2><p class="note">이 근무표 링크는 더 이상 쓸 수 없어요. 가족에게 새 링크를 받아 주세요.</p>';

/* 파일 저장 · 공유 (휴대폰에서는 공유창 → 카카오톡 선택 가능) */
function shareByTap(file,filename){return new Promise((ok,no)=>{
  const d=document.createElement('div');d.className='pushask';d.style.zIndex='60';
  d.innerHTML='<b>'+(/\.png$/.test(filename)?'이미지':'파일')+'가 준비됐어요</b><span>아래 버튼을 누르면 공유창이 떠요. 카카오톡을 고르세요.</span><div class="btns"><button class="btn" data-x="go">공유하기</button><button class="btn ghost" data-x="no">닫기</button></div>';
  document.body.appendChild(d);
  d.onclick=async ev=>{const x=ev.target.closest('[data-x]');if(!x)return;
    if(x.dataset.x==='no'){d.remove();no({code:'declined'});return}
    try{await navigator.share({files:[file],title:filename});d.remove();ok({status:'saved'})}catch(e){d.remove();if(e&&e.name==='AbortError')no({code:'declined'});else no(e)}};
})}
async function appDownloads(){
  return {save:async({filename,data})=>{
    const blob=data instanceof Blob?data:new Blob([data]);
    const type=blob.type||(/\.png$/.test(filename)?'image/png':/\.pdf$/.test(filename)?'application/pdf':/\.json$/.test(filename)?'application/json':'application/octet-stream');
    const mobile=/Android|iPhone|iPad|iPod/i.test(navigator.userAgent);
    if(mobile&&window.File&&navigator.canShare){
      const file=new File([blob],filename,{type});
      if(navigator.canShare({files:[file]})){try{await navigator.share({files:[file],title:filename});return {status:'saved'}}catch(e){if(e&&e.name==='AbortError')throw {code:'declined'};
        // 그림 만드는 데 시간이 걸려 '누른 동작'이 만료되면 공유창이 막혀요 → 한 번 더 누르는 버튼
        if(e&&e.name==='NotAllowedError')return await shareByTap(file,filename)}}
    }
    const a=document.createElement('a');a.href=URL.createObjectURL(blob);a.download=filename;document.body.appendChild(a);a.click();
    setTimeout(()=>{URL.revokeObjectURL(a.href);a.remove()},4000);return {status:'saved'};
  }};
}

/* ---------- 시작 화면 ---------- */
function gate(html){const g=$('#gate');if(html==null){g.hidden=true;document.body.classList.remove('gated');return}g.hidden=false;document.body.classList.add('gated');$('#gateBody').innerHTML=html}
const gLoading=t=>'<div class="g-spin"></div><p class="note">'+esc(t||'불러오는 중…')+'</p>';
function gErr(e){console.error(e);gate('<h2>문제가 생겼어요</h2><p class="note">'+esc(errText(e))+'</p><div class="btns"><button class="btn" data-g="reload">다시 시도</button><button class="btn ghost" data-g="logout">로그아웃</button></div>')}
function errText(e){const c=e&&e.code||'';
  if(/permission-denied/.test(c))return '권한이 없어요. 관리자 승인이나 권한이 바뀌었는지 확인하세요.';
  if(/unavailable|network/.test(c))return '인터넷 연결을 확인하고 다시 시도하세요.';
  if(/popup-closed|cancelled-popup/.test(c))return '로그인 창이 닫혔어요. 다시 눌러 주세요.';
  if(/unauthorized-domain/.test(c))return '이 주소가 Firebase 로그인 허용 도메인에 없어요. Firebase 콘솔 → Authentication → 설정 → 승인된 도메인에 추가하세요.';
  if(/admin-restricted-operation|operation-not-allowed/.test(c))return 'Firebase에서 이 로그인 방식이 꺼져 있어요. (관리자: Authentication → 로그인 방법에서 켜 주세요)';
  return (e&&e.message)||String(e)}
const loginHtml=()=>'<div class="g-logo">근</div><h2>배전센터 근무표</h2><p class="note">팀원 전용이에요. 구글 계정으로 로그인한 뒤 본인 이름을 고르면, 관리자가 승인하면 쓸 수 있어요.</p><button class="btn g-google" data-g="google"><span class="g-g">G</span>구글로 로그인</button><p class="note g-small"><a href="guide.html" target="_blank" rel="noopener">처음이세요? 사용 설명서 보기</a></p>';
function pickHtml(msg){
  const taken=new Set(Object.keys(claims));
  const list=byName().filter(m=>!taken.has(m.id));
  const admin=appRole==='main'||appRole==='sub';
  return '<h2>누구인가요?</h2><p class="note">'+(admin?'관리자 계정과 연결할 근무자를 고르세요.':'본인 이름을 고르면 관리자에게 승인 요청이 가요. 승인되면 바로 쓸 수 있어요.')+'</p>'+(msg?'<div class="msg bad">'+esc(msg)+'</div>':'')+
    (list.length?'<div class="pick">'+list.map(m=>'<button data-g="pick" data-id="'+m.id+'">'+esc(m.name)+'<small>'+teamLabel(m,todayN)+'</small></button>').join('')+'</div>':'<div class="msg warn">고를 수 있는 이름이 없어요. 관리자에게 문의하세요.</div>')+
    (admin?'<button class="link" data-g="nopick" style="align-self:center">근무자가 아니에요 (관리만 해요)</button>':'')+
    '<p class="note g-small">'+esc(auth.currentUser.email||'')+' · <button class="link" data-g="logout">다른 계정</button></p>';
}
function waitHtml(){
  const rej=appRole==='rejected';
  return '<div class="g-logo">근</div><h2>'+(rej?'승인이 거절됐어요':'승인을 기다리는 중이에요')+'</h2>'+
    '<p class="note"><b style="color:var(--fg)">'+esc(nm(appUser.requestedMemberId)||'')+'</b> 님으로 '+(rej?'보낸 요청을 관리자가 거절했어요. 이름을 잘못 골랐다면 다시 요청하세요.':'승인 요청을 보냈어요. 관리자(메인·서브)가 승인하면 이 화면이 자동으로 바뀌어요.')+'</p>'+
    '<div class="btns"><button class="btn ghost" data-g="repick">'+(rej?'다시 요청하기':'다른 이름으로 다시 요청')+'</button></div>'+
    '<p class="note g-small">'+esc(auth.currentUser.email||'')+' · <button class="link" data-g="logout">로그아웃</button></p>';
}
function setupHtml(){
  return '<h2>팀 만들기</h2><p class="note">아직 팀이 없어요. 지금 로그인한 계정이 <b>메인관리자</b>가 돼요.</p>'+
    '<label class="g-lab">팀 이름<input id="gTeamName" value="경기배전센터"></label>'+
    '<label class="g-lab">기존 근무표 데이터 (선택)<input id="gImport" type="file" accept=".json,application/json"><small>근무표 아티팩트의 관리 → 설정 → "데이터 내보내기"에서 받은 파일이에요. 없으면 비워 두세요.</small></label>'+
    '<button class="btn" data-g="setup">팀 만들기</button><p class="note g-small">'+esc(auth.currentUser.email||'')+' · <button class="link" data-g="logout">다른 계정</button></p>';
}

$('#gate').addEventListener('click',async e=>{
  const b=e.target.closest('[data-g]');if(!b||b.disabled)return;const act=b.dataset.g;
  try{
    if(act==='reload')location.reload();
    else if(act==='logout'){stopListeners();await FB.Au.signOut(auth)}
    else if(act==='google'){b.disabled=true;await googleLogin()}
    else if(act==='pick'){b.disabled=true;await pickMember(b.dataset.id)}
    else if(act==='repick'){b.disabled=true;await FB.F.deleteDoc(tref('users',auth.currentUser.uid))}
    else if(act==='nopick'){sessionSkipPick=true;gate(null);me='';render()}
    else if(act==='setup'){b.disabled=true;await createTeam()}
  }catch(err){b.disabled=false;if(act==='pick')gate(pickHtml(/permission/.test(err&&err.code||'')?'방금 다른 사람이 그 이름으로 연결됐어요. 관리자에게 문의하세요.':errText(err)));else gErr(err)}
});
function alertMsg(t){const g=$('#gateBody');const d=document.createElement('div');d.className='msg bad';d.textContent=t;g.prepend(d)}

async function googleLogin(){
  const p=new FB.Au.GoogleAuthProvider();p.setCustomParameters({prompt:'select_account'});
  try{await FB.Au.signInWithPopup(auth,p)}
  catch(e){if(/popup-blocked|operation-not-supported/.test(e.code||''))await FB.Au.signInWithRedirect(auth,p);else throw e}
}

/* 본인 선택 → 승인 요청 (관리자는 바로 연결) */
async function pickMember(mid){
  const F=FB.F,u=auth.currentUser;
  if(appUser&&(appRole==='main'||appRole==='sub')){
    const w=F.writeBatch(fsdb);w.set(tref('claims',mid),{uid:u.uid,at:Date.now()});w.update(tref('users',u.uid),{memberId:mid});await w.commit();return}
  await F.setDoc(tref('users',u.uid),{role:'pending',requestedMemberId:mid,memberId:null,name:u.displayName||'',email:u.email||'',photo:u.photoURL||'',createdAt:Date.now()});
}
async function createTeam(){
  const F=FB.F,u=auth.currentUser;const name=($('#gTeamName').value||'').trim()||'경기배전센터';
  const file=$('#gImport').files&&$('#gImport').files[0];let data=null;
  if(file){try{data=JSON.parse(await file.text())}catch(e){alertMsg('파일을 읽지 못했어요. 근무표에서 받은 .json 파일인지 확인하세요.');$('[data-g=setup]').disabled=false;return}}
  gate(gLoading('팀을 만드는 중…'));
  const b=F.writeBatch(fsdb);
  b.set(tref(),{name,mainUid:u.uid,createdAt:Date.now()});
  b.set(tref('users',u.uid),{role:'main',name:u.displayName||'',email:u.email||'',photo:u.photoURL||'',memberId:null,createdAt:Date.now()});
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
  if(d.memos&&d.memoOwner){try{localStorage.setItem('bj-memo-import',JSON.stringify({owner:d.memoOwner,notes:d.memos}))}catch(e){}}
}

/* ---------- 로그인 후 ---------- */
let sessionSkipPick=false,membersLoaded=false,roleUnsubs=[];
async function afterLogin(u){
  gate(gLoading());
  const F=FB.F;
  if(u.isAnonymous)return familyLogin(u);
  const ts=await F.getDoc(tref());
  if(!ts.exists()){gate(setupHtml());return}
  unsubs.push(F.onSnapshot(tref('users',u.uid),s=>{
    const prevRole=appRole,prevMid=appUser&&appUser.memberId;
    appUser=s.exists()?s.data():null;appRole=appUser?appUser.role:null;
    isAdmin=appRole==='main'||appRole==='sub';canWrite=isAdmin;live=true;
    if(!appUser||appRole==='pending'||appRole==='rejected'){
      stopRoleData();
      if(!membersLoaded)watchPickData();
      gate(appUser?waitHtml():(membersLoaded?pickHtml():gLoading()));
      return}
    if(appUser.memberId)me=appUser.memberId;
    if(appUser.mode)ui.simple=appUser.mode==='simple';
    else if(!modeSet){let l=null;try{l=localStorage.getItem('shift-simple')}catch(e){}ui.simple=l!=null?l==='1':!isAdmin}
    modeSet=true;
    if(!dataStarted)startData();
    else if(prevRole!==appRole||prevMid!==appUser.memberId)restartRoleData();
    checkPick();dataChanged();
    if(prevRole==='pending'&&appRole==='member')setTimeout(()=>offerPush(true),600);
  },e=>gErr(e)));
}
/* 승인 전: 이름 고르기에 필요한 근무자·연결 목록만 */
let pickUnsubs=[];
function watchPickData(){
  if(pickUnsubs.length)return;const F=FB.F;
  pickUnsubs.push(F.onSnapshot(tcol('members'),s=>{membersLoaded=true;if(!s.empty)members=s.docs.map(x=>({id:x.id,...x.data()}));refreshPick()},()=>{membersLoaded=true;refreshPick()}));
  pickUnsubs.push(F.onSnapshot(tcol('claims'),s=>{claims={};s.docs.forEach(x=>claims[x.id]=x.data());refreshPick()},()=>{}));
  unsubs.push(()=>{for(const u of pickUnsubs)try{u()}catch(e){}pickUnsubs=[]});
}
function refreshPick(){if(!appUser)gate(pickHtml());else if(appRole==='pending'||appRole==='rejected')gate(waitHtml())}
function checkPick(){
  if(!appUser||isFamily())return gate(null);
  if(appUser.memberId||sessionSkipPick)return gate(null);
  if(!membersLoaded)return gate(gLoading());
  gate(pickHtml()); /* 관리자인데 아직 근무자 연결 전 */
}

/* 가족: 로그인 없는 보기 전용 링크 (?f=토큰) */
async function familyLogin(u){
  const F=FB.F;
  let us=await F.getDoc(tref('users',u.uid));
  const tok=famParam();
  if(tok&&(!us.exists()||us.data().linkToken!==tok)){
    let link=null;try{const s=await F.getDoc(tref('familyLinks',tok));if(s.exists())link=s.data()}catch(e){}
    if(!link){gate(famGoneHtml());return}
    await F.setDoc(tref('users',u.uid),{role:'family',familyOf:link.memberId,linkToken:tok,createdAt:Date.now()});
    us=await F.getDoc(tref('users',u.uid));
  }
  if(!us.exists()){gate(famGoneHtml());return}
  appUser=us.data();appRole='family';isAdmin=false;canWrite=false;live=true;me=appUser.familyOf;
  startData();gate(null);dataChanged();
}

function startData(){
  dataStarted=true;
  db.collection('members').onSnapshot(s=>{membersLoaded=true;if(!s.empty)members=s.docs.map(x=>({id:x.id,...x.data()}));checkPick();dataChanged()},()=>{membersLoaded=true;checkPick()});
  db.collection('events').onSnapshot(s=>{events=s.docs.map(x=>({_id:x.id,...x.data()}));dataChanged()});
  db.collection('assignments').onSnapshot(s=>{assignments=s.docs.map(x=>x.data()).filter(x=>x&&x.date&&x.teams).sort((x,y)=>x.date<y.date?-1:1);orgDraft=null;dataChanged()});
  db.collection('budgets').onSnapshot(s=>{budgets=s.docs.map(x=>({id:x.id,...x.data()}));dataChanged()});
  db.doc('config/holidays').onSnapshot(s=>{if(s.exists&&Array.isArray(s.data().dates)){holidays=new Set(s.data().dates);dataChanged()}});
  db.doc('config/sheetNotes').onSnapshot(s=>{sheetNotes=(s.exists&&s.data().notes)||{};dataChanged()},()=>{});
  restartRoleData();
}
function stopRoleData(){for(const u of roleUnsubs)try{u()}catch(e){}roleUnsubs=[]}
function restartRoleData(){
  stopRoleData();
  const keep=u=>{roleUnsubs.push(u);return u};
  requests=[];appUsers=[];claims={};famLinks=[];ownMemos={};memos={};
  const F=FB.F;
  if(!isFamily()){
    keep(db.collection('requests').onSnapshot(s=>{requests=s.docs.map(x=>({_id:x.id,...x.data()}));dataChanged()}));
    keep(db.collection('users').onSnapshot(s=>{const before=pendingUsers().length;appUsers=s.docs.map(x=>({uid:x.id,...x.data()}));dataChanged()}));
    keep(db.collection('claims').onSnapshot(s=>{claims={};s.docs.forEach(x=>claims[x.id]=x.data());checkPick();dataChanged()}));
    if(appUser.memberId)keep(F.onSnapshot(F.query(tcol('familyLinks'),F.where('by','==',uid)),s=>{famLinks=s.docs.map(x=>({token:x.id,...x.data()})).sort((a,b)=>b.at-a.at);renderAcct()},()=>{}));
  }
  const mm=ownMid();
  if(mm)keep(db.doc('memos/'+mm).onSnapshot(s=>{ownMemos=(s.exists&&s.data().notes)||{};syncMemos();dataChanged()}));
  // 갤럭시 앱이 올린 내 휴대폰 캘린더 일정 → '내 근무 · 월'에 보이기 (본인 것만)
  if(mm&&!isFamily())keep(db.doc('calcache/'+mm).onSnapshot(s=>{const m={};const hx=n=>n?'#'+((n>>>0)&0xffffff).toString(16).padStart(6,'0'):'';if(s.exists)for(const x of (s.data().items||[])){(m[x.d]=m[x.d]||[]).push({t:(x.h?x.h+' ':'')+x.t,c:hx(x.c),w:x.w||'',id:x.id||''})}
    window.__calLines=k=>me===mm?m[ds(k)]:null;
    window.__calItems=m;dataChanged()},()=>{}));
  // '내 근무 · 일': 그날 캘린더 일정 + 일정 추가 (갤럭시 앱 → 가족 공유 캘린더에 저장)
  window.__dayCal=k=>{
    if(!mm||me!==mm||isFamily())return '';
    const list=(window.__calItems||{})[ds(k)]||[];
    let h='<div class="period"><h3>일정</h3></div>';
    h+=list.length?'<div class="list">'+list.map(x=>(isTWA&&x.id?'<a class="item evlink" href="intent://day?d='+ds(k)+'&e='+encodeURIComponent(x.id)+'#Intent;scheme=baejeonshift;package=kr.baejeon.shift;end">':'<div class="item">')+'<span class="dot-ev"'+(x.c?' style="background:'+x.c+'"':'')+'></span><div class="t"><b style="font-weight:500">'+(x.w&&x.w!=='나'?'<span class="note" style="font-weight:600">'+esc(x.w)+' · </span>':'')+esc(x.t)+'</b></div>'+(isTWA&&x.id?'</a>':'</div>')).join('')+'</div>':'<p class="note" style="margin:0;font-size:13px">없음</p>';
    if(isTWA)h+='<div class="btns" style="margin-top:auto"><a class="btn sm" href="intent://day?d='+ds(k)+'#Intent;scheme=baejeonshift;package=kr.baejeon.shift;end">＋ 일정</a></div>';
    else h+='<p class="note" style="margin:auto 0 0;font-size:12px">추가는 갤럭시 앱에서</p>';
    return h;
  };

  try{const p=JSON.parse(localStorage.getItem('bj-memo-import')||'null');if(p&&mm&&p.owner===mm&&!isFamily()){localStorage.removeItem('bj-memo-import');F.setDoc(tref('memos',mm),{notes:p.notes},{merge:true}).catch(()=>{})}}catch(e){}
  if(!isFamily())setTimeout(()=>offerPush(false),1500);
  startExtras();
}
function syncMemos(){memos=me&&me===ownMid()?ownMemos:{}}

/* ---------- 부팅 ---------- */
(async()=>{
  const fam=!!famParam();
  /* 카카오톡 안에서 열면 구글 로그인이 막혀서 바깥 브라우저로 (가족 링크는 그대로 열림) */
  if(!fam&&/KAKAOTALK/i.test(navigator.userAgent)){location.href='kakaotalk://web/openExternal?url='+encodeURIComponent(location.href);gate('<h2>브라우저에서 열어 주세요</h2><p class="note">카카오톡 안에서는 로그인이 안 돼요. 오른쪽 아래 ⋮ → "다른 브라우저로 열기"를 눌러 주세요.</p>');return}
  if(!fam&&/NAVER|Instagram|FBAN|FBAV|Line\//i.test(navigator.userAgent)){gate('<h2>브라우저에서 열어 주세요</h2><p class="note">이 앱 안의 화면에서는 구글 로그인이 막혀 있어요. 메뉴에서 "다른 브라우저로 열기"(크롬·사파리)를 눌러 주세요.</p>');return}
  const cfg=window.FIREBASE_CONFIG;
  if(!cfg||!cfg.apiKey||/YOUR|여기/.test(cfg.apiKey)){gate('<h2>설정이 필요해요</h2><p class="note">config.js에 Firebase 설정값이 아직 없어요.</p>');return}
  gate(gLoading());
  try{const [A,Au,F]=await Promise.all([import(FBB+'firebase-app.js'),import(FBB+'firebase-auth.js'),import(FBB+'firebase-firestore.js')]);FB={A,Au,F}}
  catch(e){gate('<h2>연결할 수 없어요</h2><p class="note">인터넷 연결을 확인하고 다시 열어 주세요.</p><button class="btn" data-g="reload">다시 시도</button>');return}
  FB.app=FB.A.initializeApp(cfg);
  auth=FB.Au.getAuth(FB.app);auth.languageCode='ko';
  try{fsdb=FB.F.initializeFirestore(FB.app,{ignoreUndefinedProperties:true,localCache:FB.F.persistentLocalCache({tabManager:FB.F.persistentMultipleTabManager()})})}
  catch(e){fsdb=FB.F.initializeFirestore(FB.app,{ignoreUndefinedProperties:true})}
  db=makeDb();
  try{await FB.Au.getRedirectResult(auth)}catch(e){}
  let first=true;
  FB.Au.onAuthStateChanged(auth,u=>{
    stopListeners();appUser=null;appRole=null;membersLoaded=false;
    if(!u){uid=null;
      if(fam&&first){first=false;FB.Au.signInAnonymously(auth).catch(gErr);gate(gLoading());return}
      gate(loginHtml());return}
    first=false;
    /* 팀원이 가족 링크를 열었으면 링크 쪽을 보여줌 */
    if(fam&&!u.isAnonymous&&!new URLSearchParams(location.search).get('f')){try{localStorage.removeItem('bj-fam')}catch(e){}}
    else if(fam&&!u.isAnonymous){gate('<h2>가족 보기 링크예요</h2><p class="note">이 링크는 로그인 없이 보는 가족용이에요. 지금 이 기기는 팀원 계정으로 로그인돼 있어서 내 근무표가 열려요.</p><button class="btn" data-g="reload" onclick="history.replaceState(null,\'\',location.pathname)">내 근무표 열기</button>');return}
    uid=u.uid;afterLogin(u).catch(gErr);
  });
})();

/* ---------- 푸시 알림 ---------- */
const pushSupported=()=>'Notification' in window&&'serviceWorker' in navigator&&'PushManager' in window&&!!window.VAPID_KEY;
const isIOS=/iPhone|iPad|iPod/i.test(navigator.userAgent);
const isAndroid=/Android/i.test(navigator.userAgent);
const APK_URL='https://github.com/junsury/baejeon-shift/releases/download/android/baejeon-shift.apk';
// 갤럭시 앱(TWA) 안에서 열렸는지: 앱은 ?src=twa 로 열어요
const isTWA=(()=>{try{const q=new URLSearchParams(location.search);if(q.get('src')==='twa'){sessionStorage.setItem('bj-twa','1');q.delete('src');history.replaceState(null,'',location.pathname+(q.toString()?'?'+q:'')+location.hash)}
  return sessionStorage.getItem('bj-twa')==='1'||document.referrer.startsWith('android-app://kr.baejeon.shift')}catch(e){return false}})();
const standalone=()=>matchMedia('(display-mode: standalone)').matches||navigator.standalone===true;
let pushState='';
function fixTwaNotify(){
  pushState='denied';renderAcct();
  alert('갤럭시 앱의 알림 권한이 아직 꺼져 있어요.\n[확인]을 누르면 알림 허용 화면이 열려요. 허용하면 근무표가 다시 열리고, 그때 👤 → [알림 켜기]를 한 번 더 눌러 주세요.');
  location.href='intent://notify#Intent;scheme=baejeonshift;package=kr.baejeon.shift;end';
}
async function enablePush(){
  if(!pushSupported()){alert(isIOS&&!standalone()?'아이폰은 사파리에서 공유 → "홈 화면에 추가"로 설치한 앱에서만 알림을 받을 수 있어요. 설치한 앱으로 열어서 다시 눌러 주세요.':'이 브라우저에서는 알림을 쓸 수 없어요.');return}
  const perm=await Notification.requestPermission();
  // 갤럭시 앱에서는 허용 결과가 조금 늦게 반영될 때가 있어요
  if(perm==='granted')for(let i=0;i<20&&Notification.permission!=='granted';i++)await new Promise(r=>setTimeout(r,150));
  if(isTWA&&(perm!=='granted'||Notification.permission!=='granted')){fixTwaNotify();return}
  if(perm!=='granted'){pushState='denied';renderAcct();alert('알림이 꺼져 있어요. 휴대폰 설정 → 알림에서 이 앱(또는 브라우저)의 알림을 허용해 주세요.');return}
  try{
    if(!FB.M)FB.M=await import(FBB+'firebase-messaging.js');
    const reg=await navigator.serviceWorker.ready;
    const messaging=FB.M.getMessaging(FB.app);
    const token=await FB.M.getToken(messaging,{vapidKey:window.VAPID_KEY,serviceWorkerRegistration:reg});
    if(!token)throw new Error('토큰을 받지 못했어요');
    await FB.F.setDoc(tref('users',uid,'tokens',token),{at:Date.now(),ua:navigator.userAgent.slice(0,120)});
    try{localStorage.setItem('bj-push',token)}catch(e){}
    pushState='on';
    FB.M.onMessage(messaging,p=>{const d=(p&&p.data)||{};toast((d.title||'알림')+(d.body?' · '+d.body:''))});
    renderAcct();toast('알림을 켰어요');
  }catch(e){console.error(e);if(isTWA&&/permission-blocked|permission-default/.test((e&&e.code)||String(e)))fixTwaNotify();else alert('알림을 켜지 못했어요: '+errText(e))}
}
async function disablePush(){
  try{const t=localStorage.getItem('bj-push');if(t){await FB.F.deleteDoc(tref('users',uid,'tokens',t));localStorage.removeItem('bj-push')}
    if(FB.M){await FB.M.deleteToken(FB.M.getMessaging(FB.app)).catch(()=>{})}}catch(e){}
  pushState='off';renderAcct();toast('이 기기 알림을 껐어요');
}
function pushOn(){try{return 'Notification' in window&&Notification.permission==='granted'&&!!localStorage.getItem('bj-push')}catch(e){return false}}
/* 처음 한 번: 알림 켜기 권하기 (켜져 있으면 토큰만 새로고침) */
function offerPush(force){
  if(!appUser||isFamily()||!pushSupported())return;
  if(pushOn()){enablePushSilently();return}
  let asked=false;try{asked=localStorage.getItem('bj-push-asked')==='1'}catch(e){}
  if(asked&&!force)return;
  try{localStorage.setItem('bj-push-asked','1')}catch(e){}
  const b=$('#pushAsk');if(b)b.hidden=false;
}
async function enablePushSilently(){try{if(Notification.permission==='granted'){await enablePushQuiet()}}catch(e){}}
async function enablePushQuiet(){
  if(!FB.M)FB.M=await import(FBB+'firebase-messaging.js');
  const reg=await navigator.serviceWorker.ready;const messaging=FB.M.getMessaging(FB.app);
  const token=await FB.M.getToken(messaging,{vapidKey:window.VAPID_KEY,serviceWorkerRegistration:reg});
  if(token){await FB.F.setDoc(tref('users',uid,'tokens',token),{at:Date.now(),ua:navigator.userAgent.slice(0,120)});try{localStorage.setItem('bj-push',token)}catch(e){}}
  FB.M.onMessage(messaging,p=>{const d=(p&&p.data)||{};toast((d.title||'알림')+(d.body?' · '+d.body:''))});
}
$('#pushAsk').addEventListener('click',async e=>{const b=e.target.closest('[data-pa]');if(!b)return;$('#pushAsk').hidden=true;if(b.dataset.pa==='on')await enablePush()});
function toast(t){const d=document.createElement('div');d.className='toast';d.textContent=t;document.body.appendChild(d);setTimeout(()=>d.classList.add('on'),10);setTimeout(()=>{d.classList.remove('on');setTimeout(()=>d.remove(),300)},3500)}

/* ---------- 내 정보 (가족 링크 · 알림 · 로그아웃) ---------- */
$('#acct').addEventListener('change',async e=>{if(e.target.id==='remindChk'){try{await FB.F.updateDoc(tref('users',uid),{remind:e.target.checked});toast(e.target.checked?'내일 근무 알림을 켰어요':'내일 근무 알림을 껐어요')}catch(err){alert(errText(err))}}});
$('#acctBtn').onclick=()=>{$('#acct').hidden=false;renderAcct()};
$('#acct').addEventListener('click',async e=>{
  if(e.target.id==='acct'||e.target.closest('[data-ac=close]')){$('#acct').hidden=true;return}
  const b=e.target.closest('[data-ac]');if(!b)return;const F=FB.F;
  try{
    if(b.dataset.ac==='logout'){$('#acct').hidden=true;stopListeners();await FB.Au.signOut(auth)}
    else if(b.dataset.ac==='famNew'){b.disabled=true;const tok=rndCode(16);await F.setDoc(tref('familyLinks',tok),{memberId:appUser.memberId,by:uid,at:Date.now()});shareFamily(tok)}
    else if(b.dataset.ac==='famDel'){if(confirm('이 링크를 끊을까요? 이 링크로 보던 가족은 바로 못 보게 돼요.'))await F.deleteDoc(tref('familyLinks',b.dataset.tok))}
    else if(b.dataset.ac==='famShare'){shareFamily(b.dataset.tok)}
    else if(b.dataset.ac==='pushOn'){await enablePush()}
    else if(b.dataset.ac==='widgetLink'){b.disabled=true;const old=await F.getDocs(F.query(tcol('widgetLinks'),F.where('by','==',uid)));const tok=rndCode(24);
      await F.setDoc(tref('widgetLinks',tok),{memberId:appUser.memberId,by:uid,at:Date.now()});
      for(const d of old.docs)await F.deleteDoc(d.ref).catch(()=>{});
      location.href='intent://link?t='+tok+'#Intent;scheme=baejeonshift;package=kr.baejeon.shift;end';b.disabled=false}
    else if(b.dataset.ac==='widgetOff'){if(confirm('이 계정과 연결된 갤럭시 위젯을 모두 끊을까요? 위젯에 "연결이 끊겼어요"가 떠요.')){const old=await F.getDocs(F.query(tcol('widgetLinks'),F.where('by','==',uid)));for(const d of old.docs)await F.deleteDoc(d.ref);toast('위젯 연결을 끊었어요')}}
    else if(b.dataset.ac==='shareGuide'){shareGuide()}
    else if(b.dataset.ac==='pushOff'){await disablePush()}
    else if(b.dataset.ac==='famLeave'){if(confirm('이 기기에서 근무표 보기를 그만둘까요? 다시 보려면 링크를 다시 눌러야 해요.')){try{localStorage.removeItem('bj-fam')}catch(e){}await F.deleteDoc(tref('users',uid)).catch(()=>{});await FB.Au.signOut(auth);location.replace(location.pathname)}}
  }catch(err){alert(errText(err))}
});
function shareGuide(){
  const base=location.origin+location.pathname.replace(/[^/]*$/,'');const url=base+'guide.html';
  const text='배전센터 근무표 사용 설명서예요. 안드로이드는 크롬, 아이폰은 사파리에서 열어 주세요.';
  if(navigator.share)navigator.share({title:'배전센터 근무표 사용 설명서',text,url}).catch(()=>{});
  else{navigator.clipboard&&navigator.clipboard.writeText(text+'\n'+url).catch(()=>{});alert('설명서 링크를 복사했어요. 단톡방에 붙여 넣으세요.\n\n'+url)}
}
function famUrl(tok){return location.origin+location.pathname+'?f='+tok}
function shareFamily(tok){
  const url=famUrl(tok);const text=nm(appUser.memberId)+' 근무표예요. 링크를 누르면 로그인 없이 볼 수 있어요.';
  if(navigator.share)navigator.share({title:'배전센터 근무표',text,url}).catch(()=>{});
  else{navigator.clipboard&&navigator.clipboard.writeText(text+'\n'+url).catch(()=>{});alert('링크를 복사했어요. 카톡에 붙여 넣으세요.\n\n'+url)}
}
function renderAcct(){
  const box=$('#acctBody');if(!box||$('#acct').hidden)return;
  if(!appUser){box.innerHTML='';return}
  const u=auth&&auth.currentUser;
  if(isFamily()){
    box.innerHTML='<h3 style="margin:0">'+esc(nm(appUser.familyOf))+' 님 근무표</h3><p class="note" style="margin:0">가족이 보내 준 보기 전용 링크로 보고 있어요. 근무와 메모만 볼 수 있고 바꿀 수는 없어요.</p>'+
      '<h3>홈 화면에 추가</h3><p class="note" style="margin:0">아이폰: 사파리 공유 버튼 → "홈 화면에 추가". 안드로이드: 크롬 ⋮ → "홈 화면에 추가".</p>'+
      '<div class="btns" style="margin-top:6px"><button class="btn ghost" data-ac="famLeave">이 기기에서 그만 보기</button><button class="btn ghost" data-ac="close">닫기</button></div>';return}
  let h='<div class="acct-me">'+(u&&u.photoURL?'<img src="'+esc(u.photoURL)+'" alt="">':'')+'<div><b>'+esc((u&&u.displayName)||'')+'</b><small>'+esc((u&&u.email)||'')+'</small></div><span class="tag 변경">'+ROLE_NAME[appRole]+'</span></div>';
  h+='<p class="note">연결된 근무자: <b style="color:var(--fg)">'+(appUser.memberId?esc(nm(appUser.memberId)):'없음')+'</b></p>';
  h+='<h3>알림</h3>';
  if(!window.VAPID_KEY)h+='<p class="note" style="margin:0">알림 기능을 준비하고 있어요.</p>';
  else if(pushOn())h+='<p class="note" style="margin:0">✓ 이 기기에서 알림을 받고 있어요. 요청·승인·근태 변경이 있으면 알려 드려요.</p><div class="btns"><button class="btn ghost" data-ac="pushOff">이 기기 알림 끄기</button><button class="btn ghost" data-ac="pushOn">다시 연결</button></div>';
  else h+='<p class="note" style="margin:0">'+(isIOS&&!standalone()?'아이폰은 "홈 화면에 추가"한 앱으로 열어야 알림을 받을 수 있어요.':'이 기기는 아직 알림이 꺼져 있어요. 켜면 근무변경·대근 요청, 승인·거절, 근태 변경, 가입 승인 요청을 휴대폰 알림으로 받아요.')+'</p><button class="btn" data-ac="pushOn">이 기기 알림 켜기</button>';
  if(window.VAPID_KEY&&appUser.memberId)h+='<label class="chk" for="remindChk"><input type="checkbox" id="remindChk"'+(appUser.remind===false?'':' checked')+'><span>내일 근무 알림<small>매일 저녁 8시에 내일 근무와 같이 근무하는 사람을 알려 드려요. 근무가 바뀐 날은 "변경됨"으로 표시돼요.'+(pushOn()?'':' (위에서 이 기기 알림을 켜야 받아요)')+'</small></span></label>';
  if(appUser.memberId){
    h+='<h3>가족과 공유</h3><p class="note" style="margin:0">링크를 받은 가족은 로그인 없이 내 근무(일·주·월)와 내 메모를 볼 수 있어요. 링크를 끊으면 바로 못 봐요.</p>';
    h+=famLinks.length?'<div class="list">'+famLinks.map(l=>'<div class="item"><div class="t"><b>가족 링크</b><small>'+new Date(l.at).toLocaleDateString('ko-KR')+' 만듦</small></div><span class="btns"><button class="btn" data-ac="famShare" data-tok="'+l.token+'">보내기</button><button class="btn ghost" data-ac="famDel" data-tok="'+l.token+'">끊기</button></span></div>').join('')+'</div>':'';
    h+='<button class="btn ghost" data-ac="famNew">'+(famLinks.length?'새 링크 만들기':'가족 링크 만들기')+'</button>';
  }
  if(appUser.memberId&&isTWA)h+='<h3>갤럭시 위젯</h3><p class="note" style="margin:0">버튼을 누르면 홈 화면 위젯에 내 근무가 표시돼요. 연결한 뒤 홈 화면을 길게 눌러 [위젯] → "근무표"에서 원하는 위젯을 추가하세요.</p><div class="btns"><button class="btn" data-ac="widgetLink">갤럭시 위젯 연결</button><button class="btn ghost" data-ac="widgetOff">연결 끊기</button></div>'
    +'<h3>가족 공유 캘린더</h3><p class="note" style="margin:0">내 구글 계정에 "근무표" 캘린더를 만들어 가족(아이폰도 가능)과 함께 써요. 내 근무가 자동으로 들어가고, 가족이 넣은 일정은 위젯에 보여요.</p><div class="btns"><a class="btn ghost" href="intent://family#Intent;scheme=baejeonshift;package=kr.baejeon.shift;end">가족 공유 캘린더 설정</a></div>';
  else if(appUser.memberId&&isAndroid)h+='<h3>갤럭시 앱 (위젯)</h3><p class="note" style="margin:0">갤럭시는 앱을 설치하면 홈 화면 위젯으로 내 근무와 캘린더 일정을 볼 수 있어요. 받은 파일을 열어 설치하고, 앱에서 로그인한 뒤 이 메뉴의 [갤럭시 위젯 연결]을 누르세요.</p><div class="btns"><a class="btn" href="'+APK_URL+'">갤럭시 앱 받기</a><a class="btn ghost" href="guide.html#android-app" target="_blank" rel="noopener">설치 방법</a></div>';
  h+='<h3>도움말</h3><div class="btns"><a class="btn ghost" href="guide.html" target="_blank" rel="noopener">사용 설명서</a><button class="btn ghost" data-ac="shareGuide">설명서 보내기</button></div>';
  h+='<h3>홈 화면에 추가</h3><p class="note" style="margin:0">아이폰: 사파리 아래 공유 버튼 → "홈 화면에 추가". 안드로이드: 크롬 ⋮ → "홈 화면에 추가" 또는 "앱 설치".</p>';
  h+='<div class="btns" style="margin-top:6px"><button class="btn ghost" data-ac="logout">로그아웃</button><button class="btn ghost" data-ac="close">닫기</button></div>';
  box.innerHTML=h;
}

/* ---------- 관리: 승인 대기 · 사용자와 권한 ---------- */
async function renderLinks(){
  const box=$('#linkCard');if(!box)return;
  if(!appUser){box.innerHTML='';return}
  const order={pending:0,main:1,sub:2,member:3,rejected:4};
  const pend=pendingUsers();
  const rows=appUsers.filter(x=>x.role!=='family'&&x.role!=='pending').sort((a,b)=>(order[a.role]-order[b.role])||String(a.memberId?nm(a.memberId):a.name).localeCompare(String(b.memberId?nm(b.memberId):b.name),'ko'));
  let h='<h2>가입 승인 '+(pend.length?'<span class="badge">'+pend.length+'</span>':'')+'</h2>';
  h+=pend.length?'<div class="list">'+pend.map(x=>{const taken=claims[x.requestedMemberId];
    return '<div class="item" style="flex-wrap:wrap"><div class="t"><b>'+esc(nm(x.requestedMemberId))+'</b> 님으로 요청<small>'+esc(x.name||'')+' · '+esc(x.email||'')+' · '+new Date(x.createdAt).toLocaleDateString('ko-KR')+'</small>'+(taken?'<span class="msg bad" style="margin-top:4px">이미 다른 계정이 이 이름으로 연결돼 있어요</span>':'')+'</div><span class="btns"><button class="btn" data-ad="approve" data-uid="'+x.uid+'"'+(taken?' disabled':'')+'>승인</button><button class="btn ghost" data-ad="reject" data-uid="'+x.uid+'">거절</button></span></div>'}).join('')+'</div>':'<p class="empty" style="margin:0">기다리는 가입 요청이 없어요.</p>';
  h+='<p class="note" style="margin:0">팀원에게 앱 주소만 알려 주세요. 구글로 로그인해서 본인 이름을 고르면 여기 요청이 와요.</p>';
  h+='<div class="btns"><button class="btn ghost" data-ad="shareApp">앱 주소 보내기</button></div>';
  h+='<h2 style="margin-top:6px">사용자 · 권한</h2><p class="note" style="margin:0">메인관리자는 서브관리자를 지정·해제하고, 메인관리자를 다른 사람에게 넘길 수 있어요. 관리자는 팀원을 내보낼 수 있어요(다시 쓰려면 승인 요청부터).</p>';
  h+='<div class="list">'+rows.map(x=>{
    const self=x.uid===uid;const who=x.memberId?esc(nm(x.memberId)):(x.role==='rejected'?esc(nm(x.requestedMemberId)||x.name||''):'<span class="note">근무자 미연결</span>');
    const acts=[];
    if(!self&&appRole==='main'&&x.role!=='rejected'){
      if(x.role==='member')acts.push('<button class="btn ghost" data-ad="toSub" data-uid="'+x.uid+'">서브관리자로</button>');
      if(x.role==='sub')acts.push('<button class="btn ghost" data-ad="toMember" data-uid="'+x.uid+'">서브 해제</button>');
      acts.push('<button class="btn ghost" data-ad="toMain" data-uid="'+x.uid+'">메인 넘기기</button>');
    }
    const canManage=!self&&(appRole==='main'||(appRole==='sub'&&(x.role==='member'||x.role==='rejected')));
    if(canManage)acts.push('<button class="btn ghost" data-ad="remove" data-uid="'+x.uid+'">'+(x.role==='rejected'?'지우기':'내보내기')+'</button>');
    return '<div class="item" style="flex-wrap:wrap;row-gap:8px"><div class="t" style="flex:1 1 100%;min-width:0"><b style="white-space:nowrap">'+who+'</b> <span class="tag '+(x.role==='main'?'대근':x.role==='sub'?'변경':x.role==='rejected'?'미정':'지원')+'">'+ROLE_NAME[x.role]+'</span>'+(self?' <span class="tag 미정">나</span>':'')+'<small>'+esc(x.email||'')+'</small></div>'+(acts.length?'<span class="btns" style="flex-wrap:wrap">'+acts.join('')+'</span>':'')+'</div>'}).join('')+'</div>';
  const fams=appUsers.filter(x=>x.role==='family');
  if(fams.length)h+='<p class="note" style="margin:0">가족 링크로 보는 기기: '+[...new Set(fams.map(f=>f.familyOf))].map(mid=>esc(nm(mid))+' '+fams.filter(f=>f.familyOf===mid).length+'대').join(' · ')+'</p>';
  box.innerHTML=h;
}
$('#linkCard').addEventListener('click',async e=>{
  const b=e.target.closest('[data-ad]');if(!b||b.disabled)return;const F=FB.F;const x=appUsers.find(u=>u.uid===b.dataset.uid);
  const who=x?(x.memberId?nm(x.memberId):(nm(x.requestedMemberId)||x.name||x.email)):'';
  try{
    switch(b.dataset.ad){
      case 'shareApp':{const url=location.origin+location.pathname;const text='배전센터 근무표 앱이에요. 안드로이드는 크롬, 아이폰은 사파리에서 열어 주세요. 구글로 로그인하고 본인 이름을 고르면 관리자가 승인해 드려요.\n사용 설명서: '+location.origin+location.pathname.replace(/[^/]*$/,'')+'guide.html';
        if(navigator.share)navigator.share({title:'배전센터 근무표',text,url}).catch(()=>{});else{navigator.clipboard&&navigator.clipboard.writeText(text+'\n'+url).catch(()=>{});alert('주소를 복사했어요. 단톡방에 붙여 넣으세요.\n\n'+url)}break}
      case 'approve':{b.disabled=true;const mid=x.requestedMemberId;const w=F.writeBatch(fsdb);w.set(tref('claims',mid),{uid:x.uid,at:Date.now(),by:uid});w.update(tref('users',x.uid),{role:'member',memberId:mid,approvedAt:Date.now(),approvedBy:uid});await w.commit();toast(who+' 님을 승인했어요');break}
      case 'reject':if(confirm(who+' 님으로 온 요청을 거절할까요?'))await F.updateDoc(tref('users',x.uid),{role:'rejected'});break;
      case 'toSub':if(confirm(who+' 님을 서브관리자로 지정할까요?'))await F.updateDoc(tref('users',x.uid),{role:'sub'});break;
      case 'toMember':if(confirm(who+' 님의 서브관리자 권한을 회수할까요?'))await F.updateDoc(tref('users',x.uid),{role:'member'});break;
      case 'toMain':if(confirm(who+' 님에게 메인관리자를 넘길까요?\n넘기면 나는 서브관리자가 돼요.')){const w=F.writeBatch(fsdb);w.update(tref('users',x.uid),{role:'main'});w.update(tref(),{mainUid:x.uid});w.update(tref('users',uid),{role:'sub'});await w.commit()}break;
      case 'remove':if(confirm(who+' 님을 '+(x.role==='rejected'?'목록에서 지울까요?':'팀에서 내보낼까요? 다시 쓰려면 승인 요청부터 해야 해요.'))){const w=F.writeBatch(fsdb);if(x.memberId&&claims[x.memberId]&&claims[x.memberId].uid===x.uid)w.delete(tref('claims',x.memberId));w.delete(tref('users',x.uid));await w.commit()}break;
    }
  }catch(err){b.disabled=false;alert('처리하지 못했어요: '+errText(err))}
});

/* 앱에서 "나" 고르기: 관리자만, 화면 전환만 (메모 등 개인 정보는 본인 것만) */
async function linkMe(id){
  if(appUser&&!isAdmin)return;
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
  const ib=$('#importBox');if(ib)ib.hidden=appRole!=='main';
  renderLog();renderBackups();
  /* 관리자: 승인 대기 표시 */
  const pend=isAdmin?pendingUsers().length:0;const ta=$('#tabAdmin');
  if(ta)ta.innerHTML='관리'+(pend?'<span class="badge">'+pend+'</span>':'');
  const pb=$('#pendBar');if(pb){pb.hidden=!pend||viewerPreview;if(pend)pb.innerHTML='<span>가입 승인을 기다리는 사람이 <b>'+pend+'명</b> 있어요.</span><button class="link" id="goPend">승인하러 가기</button>'}
}
$('#pendBar').addEventListener('click',e=>{if(e.target.id==='goPend'){ui.tab='admin';ui.adminTab='set';render();setTimeout(()=>{const c=$('#linkCard');c&&c.scrollIntoView({behavior:'smooth',block:'start'})},50)}});
/* 알림을 눌러 들어오면 해당 화면으로 (?go=inbox|admin), 앱을 열면 아이콘 숫자 지우기 */
(()=>{
  const go=new URLSearchParams(location.search).get('go');
  if(go==='inbox'){ui.tab='attend';ui.attTab='inbox';ui.stab='attend'}
  else if(go==='admin'){ui.tab='admin';ui.adminTab='set'}
  else if(/^my-(day|week|month)$/.test(go||'')){const q=new URLSearchParams(location.search).get('d');const n=/^\d{4}-\d\d-\d\d$/.test(q||'')?dn(q):todayN;ui.tab='my';ui.myView=go.slice(3);ui.myDay=n;ui.ym=monthOf(n);ui.stab='my';ui.sDay=n===todayN?null:n;ui.sYm=monthOf(n)}
  else if(go==='team-day'||go==='team-month'){const q=new URLSearchParams(location.search).get('d');const n=/^\d{4}-\d\d-\d\d$/.test(q||'')?dn(q):todayN;ui.tab='team';ui.teamView=go.slice(5);ui.day=n;ui.ym=monthOf(n);ui.stab='sheet';ui.sScrollToday=true;try{boardSel=null}catch(e){}}
  if(go)history.replaceState(null,'',location.pathname);
  const clear=()=>{try{navigator.clearAppBadge&&navigator.clearAppBadge()}catch(e){}};
  clear();document.addEventListener('visibilitychange',()=>{if(!document.hidden)clear()});
})();
/* 메인관리자: 아티팩트 데이터 가져오기 (팀을 이미 만든 뒤에도) */
$('#importRun').onclick=async()=>{
  const f=$('#importFile').files&&$('#importFile').files[0];const msg=$('#importMsg');
  if(!f){msg.innerHTML='<div class="msg warn">파일을 먼저 고르세요.</div>';return}
  let d;try{d=JSON.parse(await f.text())}catch(e){msg.innerHTML='<div class="msg bad">파일을 읽지 못했어요. 근무표에서 받은 .json 파일인지 확인하세요.</div>';return}
  if(!confirm('가져올까요? 지금 앱의 근무자 이름·조 편성·대근총량·근태 기록이 파일 내용으로 바뀌어요.'))return;
  $('#importRun').disabled=true;msg.innerHTML='<div class="msg warn">가져오는 중…</div>';
  try{await importData(d);msg.innerHTML='<div class="msg ok">✓ 가져왔어요. 근무자 '+(d.members||[]).length+'명, 근태 기록 '+(d.events||[]).length+'건.</div>'}
  catch(e){msg.innerHTML='<div class="msg bad">가져오지 못했어요: '+esc(errText(e))+'</div>'}
  $('#importRun').disabled=false;
};

/* ---------- 변경 이력 (누가 언제 등록·취소했는지) ---------- */
let historyLog=[],calLinks=[];
function evDesc(e){
  if(!e)return '';
  if(e.type==='swap'){const [a,b]=dn(e.aDate)<=dn(e.bDate)?[e.aDate,e.bDate]:[e.bDate,e.aDate];return fmt(dn(e.aDate))+' '+nm(e.aId)+' ↔ '+fmt(dn(e.bDate))+' '+nm(e.bId)+' '+(e.shift==='주'?'주간':'야간')+' 맞교환'}
  const sh=e.shift==='T'?'TSO':e.shift==='주'?'주간':'야간';
  return fmt(dn(e.date))+' '+nm(e.memberId)+' '+sh+' '+(LT_NAME[e.leaveType||'연차']||'')+(e.coverId?(e.coverType==='tso'?' → 근무지원 ':' → 대근 ')+nm(e.coverId):'');
}
async function logHistory(action,ev,label){
  if(!FB||!appUser||isFamily())return;
  try{await FB.F.addDoc(tcol('history'),{at:Date.now(),by:uid,byName:(appUser.memberId&&nm(appUser.memberId))||appUser.name||'',action,label,ev})}catch(e){console.warn('history',e)}
}
async function addEvent(ev){
  ev.createdAt=Date.now();ev.byMember=me||null;
  const ctx=ui.tab==='attend'?ui.attTab:'';if(ctx==='add')ev.src='admin';
  try{const r=await db.collection('events').add(ev);logHistory('create',{...ev},ctx==='add'?'관리자 등록':ctx==='inbox'?'요청 승인':ctx==='req'?'구두 합의':'등록');return r.id}
  catch(e){reqFail(e);return null}
}
async function delEventOk(id){
  const ev=events.find(e=>e._id===id);
  try{await db.doc('events/'+id).delete();if(ev){const{_id,...p}=ev;logHistory('delete',p,'취소')}return true}
  catch(e){reqFail(e);return false}
}
async function delEvent(id){await delEventOk(id)}
function renderLog(){
  const box=$('#logCard');if(!box)return;
  const show=ui.tab==='attend'&&ui.attTab==='hist'&&!isFamily();box.hidden=!show;if(!show)return;
  const tm=t=>{const d=new Date(t);return (d.getMonth()+1)+'/'+d.getDate()+' '+String(d.getHours()).padStart(2,'0')+':'+String(d.getMinutes()).padStart(2,'0')};
  box.innerHTML='<h3 style="margin-bottom:0">변경 이력</h3><p class="note" style="margin:0 0 4px">누가 언제 등록·취소했는지 최근 100건이에요. 지울 수 없어요.</p>'+
    (historyLog.length?'<div class="list">'+historyLog.map(h=>'<div class="item"><span class="kind '+(h.action==='delete'?'swap':'leave')+'">'+esc(h.label||(h.action==='delete'?'취소':'등록'))+'</span><div class="t">'+esc(evDesc(h.ev))+'<small>'+tm(h.at)+' · '+esc(h.byName||'')+'</small></div></div>').join('')+'</div>':'<p class="empty" style="margin:6px 0 0">아직 기록이 없어요. 이제부터 등록·취소가 여기에 남아요.</p>');
}

/* ---------- 백업 (매일 새벽 자동, 관리자: 받기 · 되돌리기) ---------- */
let backups=[];
async function readBackup(id){
  const F=FB.F;const s=await F.getDocs(tcol('backups',id,'parts'));
  const parts=s.docs.map(d=>[+d.id,d.data().data]).sort((a,b)=>a[0]-b[0]).map(x=>x[1]);
  return JSON.parse(parts.join(''));
}
function renderBackups(){
  const box=$('#backupCard');if(!box)return;
  const show=isAdmin&&!viewerPreview;box.hidden=!show;if(!show)return;
  let h='<h2>백업</h2><p class="note" style="margin:0">매일 새벽 3시에 근무자·조 편성·대근총량·근태·요청 기록을 자동으로 저장해요 (30일 보관, 개인 메모는 제외).</p>';
  h+=backups.length?'<div class="list">'+backups.slice(0,10).map(b=>'<div class="item"><div class="t"><b>'+esc(b.id)+'</b><small>근태 '+((b.counts&&b.counts.events)||0)+'건 · 요청 '+((b.counts&&b.counts.requests)||0)+'건</small></div><span class="btns"><button class="btn ghost" data-bk="get" data-id="'+b.id+'">받기</button>'+(appRole==='main'?'<button class="btn ghost" data-bk="restore" data-id="'+b.id+'">이 시점으로</button>':'')+'</span></div>').join('')+'</div>':'<p class="empty" style="margin:0">아직 백업이 없어요. 오늘 새벽부터 생겨요.</p>';
  h+='<div class="btns"><button class="btn ghost" data-bk="now">지금 백업하기</button></div><div id="bkMsg"></div>';
  box.innerHTML=h;
}
$('#backupCard').addEventListener('click',async e=>{
  const b=e.target.closest('[data-bk]');if(!b||b.disabled)return;const msg=()=>$('#bkMsg');
  try{
    if(b.dataset.bk==='now'){b.disabled=true;msg().innerHTML='<div class="msg warn">백업하는 중…</div>';
      if(!FB.Fn)FB.Fn=await import(FBB+'firebase-functions.js');
      const call=FB.Fn.httpsCallable(FB.Fn.getFunctions(FB.app,'asia-northeast3'),'backupNow');const r=await call({team:TEAM});
      msg().innerHTML='<div class="msg ok">✓ '+esc(r.data.id)+' 백업을 저장했어요.</div>'}
    else if(b.dataset.bk==='get'){const d=await readBackup(b.dataset.id);await (await appDownloads()).save({filename:'근무표_백업_'+b.dataset.id+'.json',data:JSON.stringify(d,null,1)})}
    else if(b.dataset.bk==='restore'){
      if(!confirm(b.dataset.id+' 백업 시점으로 되돌릴까요?\n그 뒤에 등록·변경된 근태와 요청은 사라져요.'))return;
      if(!confirm('정말 되돌릴까요? 되돌리기 전에 지금 상태도 자동으로 백업해 둘게요.'))return;
      b.disabled=true;msg().innerHTML='<div class="msg warn">되돌리는 중…</div>';
      try{if(!FB.Fn)FB.Fn=await import(FBB+'firebase-functions.js');await FB.Fn.httpsCallable(FB.Fn.getFunctions(FB.app,'asia-northeast3'),'backupNow')({team:TEAM})}catch(x){}
      const d=await readBackup(b.dataset.id);await restoreBackup(d);
      msg().innerHTML='<div class="msg ok">✓ '+esc(b.dataset.id)+' 시점으로 되돌렸어요.</div>'}
  }catch(err){b.disabled=false;msg().innerHTML='<div class="msg bad">하지 못했어요: '+esc(errText(err))+'</div>'}
});
async function restoreBackup(d){
  const F=FB.F;const ops=[];
  for(const c of ['members','assignments','budgets','events','requests','config']){
    const keep=new Set((d[c]||[]).map(x=>x._id));
    const cur=await F.getDocs(tcol(c));cur.docs.forEach(x=>{if(!keep.has(x.id))ops.push(['del',c,x.id])});
    for(const x of d[c]||[]){const{_id,...r}=x;ops.push(['set',c,_id,r])}
  }
  for(let i=0;i<ops.length;i+=400){const w=F.writeBatch(fsdb);for(const o of ops.slice(i,i+400)){if(o[0]==='del')w.delete(tref(o[1],o[2]));else w.set(tref(o[1],o[2]),o[3])}await w.commit()}
  logHistory('delete',{type:'leave',date:ds(todayN),memberId:'',shift:'주'},'백업 되돌리기 '+(d.at||'').slice(0,10));
}

/* 역할별 추가 구독: 변경 이력 · 백업 목록 */
function startExtras(){
  const F=FB.F;if(isFamily())return;
  roleUnsubs.push(F.onSnapshot(F.query(tcol('history'),F.orderBy('at','desc'),F.limit(100)),s=>{historyLog=s.docs.map(x=>x.data());renderLog()},()=>{}));
  /* 캘린더 구독은 없앰: 예전에 만든 링크 정리 */
  if(appUser.memberId)F.getDocs(F.query(tcol('calLinks'),F.where('by','==',uid))).then(r=>r.docs.forEach(d=>F.deleteDoc(d.ref).catch(()=>{}))).catch(()=>{});
  if(isAdmin)roleUnsubs.push(F.onSnapshot(F.query(tcol('backups'),F.orderBy('at','desc'),F.limit(30)),s=>{backups=s.docs.map(x=>({id:x.id,...x.data()}));renderBackups()},()=>{}));
}

/* ---------- 새 버전 바로 반영: 앱으로 돌아올 때·5분마다 확인 ---------- */
(()=>{
  if(!window.APP_BUILD)return;let busy=false,shown=false;
  async function check(auto){
    if(busy)return;busy=true;
    try{const r=await fetch('version.json?t='+Date.now(),{cache:'no-store'});const v=await r.json();
      if(v&&v.b&&v.b!==window.APP_BUILD){
        const typing=document.activeElement&&/INPUT|TEXTAREA|SELECT/.test(document.activeElement.tagName);
        if(document.hidden||(auto&&!typing)){location.reload();return}
        if(!shown){shown=true;const d=document.createElement('div');d.className='toast on';d.style.cursor='pointer';d.textContent='새 버전이 있어요 · 눌러서 새로고침';d.onclick=()=>location.reload();document.body.appendChild(d)}
      }}catch(e){}finally{busy=false}
  }
  document.addEventListener('visibilitychange',()=>{if(!document.hidden)check(true)});
  setInterval(()=>check(false),5*60*1000);setTimeout(()=>check(true),1500);
})();
