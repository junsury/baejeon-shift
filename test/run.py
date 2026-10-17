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
def page(b,user,w=390):
    ctx=b.new_context(viewport={'width':w,'height':844},service_workers='block')
    pg=ctx.new_page(); errs=[]
    pg.on('pageerror',lambda e:errs.append(str(e)))
    pg.on('console',lambda m:errs.append('console:'+m.text) if m.type=='error' else None)
    pg.route('**/fonts.googleapis.com/**',lambda r:r.abort())
    pg.route('**/www.gstatic.com/firebasejs/**',lambda r:r.fulfill(body=MOCK,content_type='application/javascript'))
    pg.route('**/config.js',lambda r:r.fulfill(body=CFG,content_type='application/javascript'))
    pg.add_init_script("window.__fbNext=%s;"%json.dumps(user))
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
def run():
  with sync_playwright() as p:
    b=p.chromium.launch()
    # --- A: 관리자 팀 만들기 ---
    ctx,pg,errs=page(b,ADMIN); pg.goto(BASE); pg.wait_for_timeout(500)
    print('A1 login screen:', pg.inner_text('#gateBody')[:40].replace('\n',' '))
    pg.click('[data-g=google]'); pg.wait_for_timeout(500)
    print('A2 setup:', pg.inner_text('#gateBody')[:30].replace('\n',' '))
    f=tempfile.NamedTemporaryFile('w',suffix='.json',delete=False); json.dump(TEST_DATA,f,ensure_ascii=False); f.close()
    pg.set_input_files('#gImport',f.name); pg.click('[data-g=setup]'); pg.wait_for_timeout(1500)
    print('A3 pick:', pg.inner_text('#gateBody')[:60].replace('\n',' '))
    pg.click('[data-g=pick][data-id=B2]'); pg.wait_for_timeout(600)
    print('A4 gate hidden:', pg.evaluate("document.getElementById('gate').hidden"), '| me label:', pg.inner_text('#myLbl')[:30].replace('\n',' '))
    pg.screenshot(path=SHOT+'/appA.png')
    print('A5 admin tab visible:', pg.is_visible('#tabAdmin'))
    pg.click('#tabAdmin'); pg.click('[data-at=set]'); pg.wait_for_timeout(300)
    code=pg.inner_text('.invite b.mono'); print('A6 invite code:', code)
    store=pg.evaluate("sessionStorage.getItem('mockstore')")
    print('A7 memo imported:', 'teams/gyeonggi/memos/B2' in store)
    pg.screenshot(path=SHOT+'/appA_admin.png',full_page=True)
    print('A errs', errs); ctx.close()
    # --- B: 팀원 가입 ---
    ctx,pg,errs=page(b,MEM); pg.add_init_script("sessionStorage.setItem('mockstore',%s)"%json.dumps(store))
    pg.goto(BASE); pg.wait_for_timeout(400); pg.click('[data-g=google]'); pg.wait_for_timeout(500)
    print('B1 join:', pg.inner_text('#gateBody')[:20].replace('\n',' '))
    pg.fill('#gCode','WRONG1'); pg.evaluate("()=>{window.__fb.deny=(op,path,d)=>String(path).indexOf('/users/')>=0&&!!d&&d.role==='member'&&d.joinCode!==%s}"%json.dumps(code))
    pg.click('[data-g=join]'); pg.wait_for_timeout(400); print('B2 wrong code msg:', pg.inner_text('#gateBody .msg') if pg.query_selector('#gateBody .msg') else None)
    pg.fill('#gCode',code); pg.click('[data-g=join]'); pg.wait_for_timeout(800)
    print('B3 pick (B2 hidden?):', 'data-id="B2"' in pg.inner_html('#gateBody'), pg.inner_text('#gateBody')[:20].replace('\n',' '))
    pg.click('[data-g=pick][data-id=A1]'); pg.wait_for_timeout(600)
    print('B4 gate hidden:', pg.evaluate("document.getElementById('gate').hidden"),'admin tab:',pg.is_visible('#tabAdmin'),'meSel disabled:',pg.evaluate("document.getElementById('meSel').disabled"))
    pg.click('#acctBtn'); pg.wait_for_timeout(200); pg.click('[data-ac=famNew]'); pg.wait_for_timeout(500)
    fcode=pg.inner_text('#acctBody b.mono'); print('B5 family code:', fcode)
    pg.screenshot(path=SHOT+'/appB_acct.png')
    store=pg.evaluate("sessionStorage.getItem('mockstore')"); print('B errs', errs); ctx.close()
    # --- C: 가족 ---
    ctx,pg,errs=page(b,FAM); pg.add_init_script("sessionStorage.setItem('mockstore',%s)"%json.dumps(store))
    pg.goto(BASE+'?family='+fcode); pg.wait_for_timeout(400); pg.click('[data-g=google]'); pg.wait_for_timeout(500)
    print('C1 prefilled:', pg.input_value('#gCode')); pg.click('[data-g=join]'); pg.wait_for_timeout(800)
    tabs=pg.evaluate("[...document.querySelectorAll('nav.tabs button')].filter(b=>!b.hidden&&b.offsetParent).map(b=>b.textContent)")
    print('C2 family tabs:', tabs, '| famLab:', pg.inner_text('#famLab'), '| meLab hidden:', pg.evaluate("document.getElementById('meLab').hidden"))
    pg.click('[data-mv=week]'); pg.wait_for_timeout(200); pg.screenshot(path=SHOT+'/appC_family.png',full_page=True)
    print('C errs', errs); ctx.close()
    # --- D: 메인이 팀원을 서브로, 메인 넘기기 ---
    ctx,pg,errs=page(b,ADMIN); pg.add_init_script("sessionStorage.setItem('mockstore',%s);sessionStorage.setItem('mockuser',%s)"%(json.dumps(store),json.dumps(json.dumps(ADMIN))))
    pg.on('dialog',lambda d:d.accept())
    pg.goto(BASE); pg.wait_for_timeout(800); pg.click('#tabAdmin'); pg.click('[data-at=set]'); pg.wait_for_timeout(300)
    pg.click('[data-ad=toSub][data-uid=uM]'); pg.wait_for_timeout(400)
    roles=pg.evaluate("JSON.parse(sessionStorage.getItem('mockstore')).filter(([k])=>k.includes('/users/')).map(([k,v])=>k.split('/').pop()+':'+v.role)")
    print('D1 roles:', roles)
    pg.click('[data-ad=toMain][data-uid=uM]'); pg.wait_for_timeout(500)
    roles=pg.evaluate("JSON.parse(sessionStorage.getItem('mockstore')).filter(([k])=>k.includes('/users/')).map(([k,v])=>k.split('/').pop()+':'+v.role)")
    print('D2 after transfer:', roles, '| admin tab still (sub):', pg.is_visible('#tabAdmin'))
    pg.screenshot(path=SHOT+'/appD.png',full_page=True)
    print('D errs', errs); ctx.close()
    b.close()
run()
