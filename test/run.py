import os, sys, json
from playwright.sync_api import sync_playwright
ROOT=os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MOCK=open(os.path.join(ROOT,'test','mock-firebase.js')).read()
CFG="window.FIREBASE_CONFIG={apiKey:'test-key',authDomain:'x',projectId:'x'};window.TEAM_ID='gyeonggi';"
SHOT=sys.argv[1] if len(sys.argv)>1 else '/tmp'
import http.server, threading, functools
H=functools.partial(http.server.SimpleHTTPRequestHandler,directory=os.path.join(ROOT,'app'))
class Q(H.func):
    def log_message(self,*a):pass
srv=http.server.ThreadingHTTPServer(('127.0.0.1',8765),functools.partial(Q,directory=os.path.join(ROOT,'app')))
threading.Thread(target=srv.serve_forever,daemon=True).start()
BASE='http://127.0.0.1:8765/'
SIMPLE=False
def page(b,user,w=390):
    ctx=b.new_context(viewport={'width':w,'height':844},service_workers='block')
    pg=ctx.new_page(); errs=[]
    pg.on('pageerror',lambda e:errs.append(str(e)))
    pg.on('console',lambda m:errs.append('console:'+m.text) if m.type=='error' else None)
    pg.route('**/fonts.googleapis.com/**',lambda r:r.abort())
    pg.route('**/www.gstatic.com/firebasejs/**',lambda r:r.fulfill(body=MOCK,content_type='application/javascript'))
    pg.route('**/config.js',lambda r:r.fulfill(body=CFG,content_type='application/javascript'))
    pg.add_init_script("window.__fbNext=%s;"%json.dumps(user))
    if not SIMPLE: pg.add_init_script("try{localStorage.getItem('shift-simple')==null&&localStorage.setItem('shift-simple','0')}catch(e){}")
    return ctx,pg,errs

TEST_DATA={
 "version":1,
 "members":[{"id":"A1","name":"강하늘","team":"A","role":"조장","order":1},{"id":"A2","name":"이바다","team":"A","role":"조원","order":2},
  {"id":"B1","name":"박산들","team":"B","role":"조장","order":3},{"id":"B2","name":"최구름","team":"B","role":"조원","order":4},
  {"id":"C1","name":"정이슬","team":"C","role":"조장","order":5},{"id":"C2","name":"한별님","team":"C","role":"조원","order":6},
  {"id":"D1","name":"윤솔길","team":"D","role":"조장","order":7},{"id":"D2","name":"오가람","team":"D","role":"조원","order":8},
  {"id":"T1","name":"임새벽","team":None,"role":"TSO","order":9}],
 "assignments":[{"date":"2026-10-19","teams":{"A2":{"team":"D","role":"조원"},"B2":{"team":"A","role":"조원"},"C2":{"team":"B","role":"조원"},"D2":{"team":"C","role":"조원"}}}],
 "budgets":[],"events":[{"_id":"e1","type":"swap","aId":"A2","aDate":"2026-10-18","bId":"B2","bDate":"2026-12-20","shift":"야","createdAt":1}],
 "requests":[],"holidays":None,"memos":{"2026-10-18":"아이 학교 행사"},"memoOwner":"B2"}

ADMIN={"uid":"uA","email":"admin@test","displayName":"관리자","photoURL":""}
MEM={"uid":"uM","email":"mem@test","displayName":"강하늘","photoURL":""}
FAM={"uid":"uF","email":"fam@test","displayName":"구름 가족","photoURL":""}
import tempfile
def store_of(pg): return pg.evaluate("sessionStorage.getItem('mockstore')")
def with_store(pg,store,user=None):
    js="sessionStorage.setItem('mockstore',%s);"%json.dumps(store)
    if user: js+="sessionStorage.setItem('mockuser',%s);"%json.dumps(json.dumps(user))
    pg.add_init_script(js)
def roles(pg): return pg.evaluate("JSON.parse(sessionStorage.getItem('mockstore')).filter(([k])=>/\\/users\\/[^/]+$/.test(k)).map(([k,v])=>k.split('/').pop()+':'+v.role+(v.memberId?'/'+v.memberId:''))")
def run():
  with sync_playwright() as p:
    b=p.chromium.launch()
    # --- A: 관리자 팀 만들기 + 본인 연결 ---
    ctx,pg,errs=page(b,ADMIN); pg.goto(BASE); pg.wait_for_timeout(500)
    pg.click('[data-g=google]'); pg.wait_for_timeout(500)
    f=tempfile.NamedTemporaryFile('w',suffix='.json',delete=False); json.dump(TEST_DATA,f,ensure_ascii=False); f.close()
    pg.set_input_files('#gImport',f.name); pg.click('[data-g=setup]'); pg.wait_for_timeout(1500)
    print('A1 pick:', pg.inner_text('#gateBody')[:30].replace('\n',' '))
    pg.click('[data-g=pick][data-id=B2]'); pg.wait_for_timeout(600)
    print('A2 in app:', pg.evaluate("document.getElementById('gate').hidden"), roles(pg))
    store=store_of(pg); ctx.close()
    # --- B: 팀원 승인 요청 ---
    ctx,pg,errs=page(b,MEM); with_store(pg,store); pg.goto(BASE); pg.wait_for_timeout(400); pg.click('[data-g=google]'); pg.wait_for_timeout(700)
    print('B1 pick (B2 hidden):', 'data-id="B2"' not in pg.inner_html('#gateBody'))
    pg.click('[data-g=pick][data-id=A1]'); pg.wait_for_timeout(600)
    print('B2 wait:', pg.inner_text('#gateBody')[:40].replace('\n',' '))
    store=store_of(pg); ctx.close()
    # --- A: 관리자 승인 ---
    ctx,pg,errs=page(b,ADMIN); with_store(pg,store,ADMIN); pg.on('dialog',lambda d:d.accept())
    pg.goto(BASE); pg.wait_for_timeout(900)
    print('A3 pend bar:', pg.inner_text('#pendBar') if pg.is_visible('#pendBar') else None, '| tab:', pg.inner_text('#tabAdmin'))
    pg.click('#goPend'); pg.wait_for_timeout(300); pg.screenshot(path=SHOT+'/appApprove.png',full_page=True)
    pg.click('[data-ad=approve][data-uid=uM]'); pg.wait_for_timeout(500); print('A4 roles:', roles(pg))
    store=store_of(pg); print('A errs', [e for e in errs if 'ERR_FAILED' not in e]); ctx.close()
    # --- B: 승인 후 사용 + 가족 링크 ---
    ctx,pg,errs=page(b,MEM); with_store(pg,store,MEM); pg.goto(BASE); pg.wait_for_timeout(900)
    print('B3 in app:', pg.evaluate("document.getElementById('gate').hidden"), '| admin tab:', pg.is_visible('#tabAdmin'), '| meSel disabled:', pg.evaluate("document.getElementById('meSel').disabled"))
    pg.click('#acctBtn'); pg.wait_for_timeout(200); pg.click('[data-ac=famNew]'); pg.wait_for_timeout(500)
    tok=pg.evaluate("JSON.parse(sessionStorage.getItem('mockstore')).find(([k])=>k.includes('/familyLinks/'))[0].split('/').pop()")
    print('B4 family token:', tok); pg.screenshot(path=SHOT+'/appB_acct.png')
    store=store_of(pg); print('B errs', [e for e in errs if 'ERR_FAILED' not in e]); ctx.close()
    # --- C: 가족 (로그인 없음) ---
    ctx,pg,errs=page(b,None); with_store(pg,store); pg.goto(BASE+'?f='+tok); pg.wait_for_timeout(1200)
    tabs=pg.evaluate("[...document.querySelectorAll('nav.tabs button')].filter(b=>!b.hidden&&b.offsetParent).map(b=>b.textContent)")
    print('C1 family:', pg.evaluate("document.getElementById('gate').hidden"), tabs, pg.inner_text('#famLab'))
    pg.screenshot(path=SHOT+'/appC_family.png')
    pg.goto(BASE+'?f=WRONGTOKEN'); pg.wait_for_timeout(800)
    print('C2 after re-open w/ bad token keeps doc?:', pg.inner_text('#gateBody')[:20] if not pg.evaluate("document.getElementById('gate').hidden") else 'still family view')
    print('C errs', [e for e in errs if 'ERR_FAILED' not in e]); ctx.close()
    # --- D: 서브 지정 + 메인 넘기기 ---
    ctx,pg,errs=page(b,ADMIN); with_store(pg,store,ADMIN); pg.on('dialog',lambda d:d.accept())
    pg.goto(BASE); pg.wait_for_timeout(800); pg.click('#tabAdmin'); pg.click('[data-at=set]'); pg.wait_for_timeout(300)
    pg.click('[data-ad=toSub][data-uid=uM]'); pg.wait_for_timeout(400); print('D1', roles(pg))
    pg.click('[data-ad=toMain][data-uid=uM]'); pg.wait_for_timeout(500); print('D2', roles(pg))
    print('D errs', [e for e in errs if 'ERR_FAILED' not in e]); ctx.close()
    b.close()
run()
