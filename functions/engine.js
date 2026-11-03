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
  function rawCells(n,evs){
    const key='r'+n;
    if(!evs&&cache.has(key))return cache.get(key);
    const list=(evs||events).filter(e=>touches(e,n)).map((e,i)=>[e,i]).sort((x,y)=>((x[0].createdAt||0)-(y[0].createdAt||0))||(x[1]-y[1])).map(x=>x[0]);
    const base={},toks=[],res={};
    for(const m of members){
      if(!present(m,n)){base[m.id]='없';continue}
      if(m.role==='TSO'){base[m.id]=isWorkday(n)?'T':'휴';continue}
      const t=teamAt(m,n).team;if(!t){base[m.id]='없';continue}
      let code=PATTERN[mod(REF_POS[t]+n-dn(REF),8)];
      if(code==='비'&&teamAt(m,n-1).team!==t)code='휴';   // 조가 바뀐 날엔 전날 야간을 안 했으니 비번이 아님
      base[m.id]=code;
      if(code==='주'||code==='야')toks.push({s:code,orig:m.id,holder:m.id,via:null,hist:[]});
    }
    const tsoSt={};   // TSO: 휴가·시간휴가·근무지원
    const take=(who,sh)=>toks.find(k=>k.holder===who&&k.s===sh&&k.via!=='cover')||toks.find(k=>k.holder===who&&k.s===sh);
    for(const e of list){
      if(e.type==='swap'){
        for(const[from,to,other]of[[e.aId,e.bId,e.bDate],[e.bId,e.aId,e.aDate]]){
          if(dn(from===e.aId?e.aDate:e.bDate)!==n)continue;
          let k=take(from,e.shift);
          if(!k){k={s:e.shift,orig:from,holder:from,via:null,hist:[],ghost:true};toks.push(k)}   // 넘겨줄 근무가 없던 기록(옛 데이터)도 그대로 보여주기
          k.hist.push({t:'swap',from,to,od:dn(other),ev:e._id});k.holder=to;k.via='swap';k.with=from;k.withDate=dn(other);
        }
      }else if(e.type==='leave'){
        const mm=members.find(x=>x.id===e.memberId),isTso=!!mm&&mm.role==='TSO';
        if(e.leaveType==='시간'||isTso){(tsoSt[e.memberId]=tsoSt[e.memberId]||[]).push(e);continue}
        let k=take(e.memberId,e.shift);
        if(!k){k={s:e.shift,orig:e.memberId,holder:e.memberId,via:null,hist:[],ghost:true};toks.push(k)}
        const to=e.coverType==='cover'||e.coverType==='tso'?(e.coverId||null):null;
        k.hist.push({t:'leave',from:e.memberId,to,ct:e.coverType,lt:e.leaveType,ev:e._id});k.holder=to;k.via=to?(e.coverType==='tso'?'tso':'cover'):null;k.for=e.memberId;
      }
    }
    for(const m of members){
      const id=m.id,b=base[id];
      if(b==='없'){res[id]={code:'없',cover:null,tags:[]};continue}
      if(m.role==='TSO'){
        let c={code:b,cover:null,tags:[]};
        const sup=toks.find(k=>k.holder===id&&k.via==='tso');
        if(sup)c={code:'지원',cover:null,tags:['지원'],for:sup.for};
        for(const e of tsoSt[id]||[]){
          if(e.leaveType==='시간'){if(c.code==='T'){const dur=tmin(e.to)-tmin(e.from);c={...c,tags:[...c.tags,'시간휴가'],part:{from:e.from,to:e.to},minus:Math.max(0,dur)/60}}}
          else c={code:'연',cover:null,tags:[],was:e.shift,by:e.coverId||null,byType:e.coverType,leaveType:e.leaveType};
        }
        res[id]=c;continue}
      const held=toks.filter(k=>k.holder===id&&k.via!=='cover'),cov=toks.filter(k=>k.holder===id&&k.via==='cover');
      const steps=[];for(const k of toks)k.hist.forEach((h,i)=>{if(h.from===id)steps.push({...h,s:k.s,k,i})});
      steps.sort((x,y)=>list.findIndex(e=>e._id===x.ev)-list.findIndex(e=>e._id===y.ev));
      const gave=steps.find(x=>x.t==='swap'),lv=steps.filter(x=>x.t==='leave').pop();
      let c;
      if(held.length){const k=held.find(x=>x.s==='주')||held[0];
        c={code:k.s,cover:null,tags:[]};
        if(k.via==='swap'){c.tags.push('변경');c.with=k.with;c.withDate=k.withDate}
        if(gave){if(!c.tags.includes('변경'))c.tags.push('변경');c.gave=gave.s;c.gaveTo=gave.to;c.gaveDate=gave.od;if(c.with==null){c.with=gave.to;c.withDate=gave.od}}
        if(held.length>1){const k2=held.find(x=>x!==k);c.extra=k2.s}
        if(lv)c.lv={s:lv.s,by:lv.to,byType:lv.ct,leaveType:lv.lt};
      }else if(lv){c={code:'연',cover:null,tags:[],was:lv.s,by:lv.to||null,byType:lv.ct,leaveType:lv.lt}}
      else if(gave){c={code:'휴',cover:null,tags:['변경'],gave:gave.s,gaveTo:gave.to,gaveDate:gave.od,with:gave.to,withDate:gave.od}}
      else c={code:(b==='주'||b==='야')?'휴':b,cover:null,tags:[]};
      if(cov.length){c.cover=cov[0].s;c.tags=[...c.tags,'대근'];c.for=cov[0].for}
      if(steps.length+held.filter(k=>k.via).length+cov.length>1)c.stack=true;
      res[id]=c;
    }
    Object.defineProperty(res,'__toks',{value:toks});
    if(!evs)cache.set(key,res);
    return res;
  }
  function dayCells(n,evs){
    if(!evs&&cache.has(n))return cache.get(n);
    const raw=rawCells(n,evs),prev=rawCells(n-1,evs),res={};
    Object.defineProperty(res,'__toks',{value:raw.__toks});
    for(const id in raw){const c=raw[id],p=prev[id];let x=c;
      if(p){const night=hasShift(p,'야');
        if(c.code==='비'&&!night)x={...c,code:'휴'};
        else if(c.code==='휴'&&night&&!c.tags.length&&!c.gave)x={...c,code:'비'}}
      res[id]=x}
    if(!evs)cache.set(n,res);
    return res;
  }
  function touches(e,n){return e.type==='swap'?(dn(e.aDate)===n||dn(e.bDate)===n):dn(e.date)===n}
  const tmin=t=>{const[h,m]=String(t||'0:0').split(':').map(Number);return h*60+(m||0)};
  function hasShift(c,s){return c.code===s||c.cover===s}
  let holidays = new Set(holidayDates && holidayDates.length ? holidayDates : DEFAULT_HOLIDAYS);
  assignments = (assignments || []).filter(x => x && x.date && x.teams).sort((x, y) => x.date < y.date ? -1 : 1);
  return { dayCells, dn, ds, dow, teamAt, hasShift, HOURS, WD, PATTERN };
};
