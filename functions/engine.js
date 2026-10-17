// 자동 생성 파일 — 고치지 마세요. build.py가 src/shift-board.html의 근무 계산 코드를 복사해 만들어요.
/* eslint-disable */
module.exports = function makeEngine({ members, assignments, events, holidayDates }) {
  let cache = new Map();
const PATTERN=['주','주','휴','야','야','비','휴','휴'];
  const REF='2026-10-21';                    // A휴 B비 C야 D주
  const REF_POS={A:7,B:5,C:3,D:1};
  const HOURS={'주':9,'야':12,'T':8,'지원':9};
  const DEFAULT_HOLIDAYS=["2026-01-01","2026-02-16","2026-02-17","2026-02-18","2026-03-01","2026-03-02","2026-05-05","2026-05-24","2026-05-25","2026-05-01","2026-06-03","2026-06-06","2026-07-17","2026-08-15","2026-08-17","2026-09-24","2026-09-25","2026-09-26","2026-09-28","2026-10-03","2026-10-05","2026-10-09","2026-12-25","2027-01-01","2027-02-06","2027-02-07","2027-02-08","2027-02-09","2027-03-01","2027-05-01","2027-05-03","2027-05-05","2027-05-13","2027-06-06","2027-07-17","2027-07-19","2027-08-15","2027-08-16","2027-09-14","2027-09-15","2027-09-16","2027-10-03","2027-10-04","2027-10-09","2027-10-11","2027-12-25","2027-12-27"];
  const WD=['일','월','화','수','목','금','토'];
  const dn=s=>{const[y,m,d]=s.split('-').map(Number);return Math.round(Date.UTC(y,m-1,d)/864e5)};
  const ds=n=>new Date(n*864e5).toISOString().slice(0,10);
  const dow=n=>new Date(n*864e5).getUTCDay();
  const mod=(a,b)=>((a%b)+b)%b;
  function teamAt(m,n){let t={team:m.team,role:m.role};for(const a of assignments){if(dn(a.date)>n)break;if(a.teams&&a.teams[m.id])t=a.teams[m.id]}return t}
  const present=(m,n)=>(!m.joinDate||n>=dn(m.joinDate))&&(!m.leaveDate||n<dn(m.leaveDate));
  const isWorkday=n=>{const w=dow(n);return w>0&&w<6&&!holidays.has(ds(n))};
  function dayCells(n,evs){
    const key=n;
    if(!evs&&cache.has(key))return cache.get(key);
    const list=(evs||events).filter(e=>touches(e,n)).sort((a,b)=>(a.createdAt||0)-(b.createdAt||0));
    const res={};
    for(const m of members){
      if(!present(m,n)){res[m.id]={code:'없',cover:null,tags:[]};continue}
      if(m.role==='TSO')res[m.id]={code:isWorkday(n)?'T':'휴',cover:null,tags:[]};
      else{const t=teamAt(m,n).team;if(!t){res[m.id]={code:'없',cover:null,tags:[]};continue}let code=PATTERN[mod(REF_POS[t]+n-dn(REF),8)];
        if(code==='비'&&teamAt(m,n-1).team!==t)code='휴';   // 조가 바뀐 날엔 전날 야간을 안 했으니 비번이 아님
        res[m.id]={code,cover:null,tags:[]}}
    }
    for(const e of list)if(e.type==='swap'){
      if(dn(e.aDate)===n)give(e.aId,e.bId,e.shift,dn(e.bDate));
      if(dn(e.bDate)===n)give(e.bId,e.aId,e.shift,dn(e.aDate));
    }
    function give(from,to,shift,other){
      if(res[from])res[from]={code:'휴',cover:null,tags:['변경'],gave:shift,with:to,withDate:other};
      if(res[to])res[to]={code:shift,cover:null,tags:['변경'],with:from,withDate:other};
    }
    for(const e of list)if(e.type==='leave'){
      if(e.leaveType==='시간'){const r=res[e.memberId];if(r&&r.code==='T'){const dur=tmin(e.to)-tmin(e.from);res[e.memberId]={...r,tags:[...r.tags,'시간휴가'],part:{from:e.from,to:e.to},minus:Math.max(0,dur)/60}}continue}
      if(res[e.memberId])res[e.memberId]={code:'연',cover:null,tags:[],was:e.shift,by:e.coverId||null,byType:e.coverType,leaveType:e.leaveType};
      if(e.coverType==='tso'&&res[e.coverId])res[e.coverId]={code:'지원',cover:null,tags:['지원'],for:e.memberId};
      if(e.coverType==='cover'&&res[e.coverId]){const c=res[e.coverId];res[e.coverId]={...c,cover:e.shift,tags:[...c.tags,'대근'],for:e.memberId}}
    }
    if(!evs)cache.set(key,res);
    return res;
  }
  function touches(e,n){return e.type==='swap'?(dn(e.aDate)===n||dn(e.bDate)===n):dn(e.date)===n}
  const tmin=t=>{const[h,m]=String(t||'0:0').split(':').map(Number);return h*60+(m||0)};
  function hasShift(c,s){return c.code===s||c.cover===s}
  let holidays = new Set(holidayDates && holidayDates.length ? holidayDates : DEFAULT_HOLIDAYS);
  assignments = (assignments || []).filter(x => x && x.date && x.teams).sort((x, y) => x.date < y.date ? -1 : 1);
  return { dayCells, dn, ds, dow, teamAt, hasShift, HOURS, WD, PATTERN };
};
