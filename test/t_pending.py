# 수락 대기 중인 요청: 근무표·내 근무에 '요청중' 표시, 근태에 보낸/받은 구분, 취소하면 사라짐
import sys, os, json, tempfile
SHOT=sys.argv[1]; sys.argv=[sys.argv[0]]
src=open(os.path.join(os.path.dirname(__file__),'run.py')).read().replace('\nrun()','\n')
R={'__file__':os.path.join(os.path.dirname(os.path.abspath(__file__)),'run.py')}; exec(compile(src,'run.py','exec'),R)
D=json.loads(json.dumps(R['TEST_DATA']))
D['requests']=[{"_id":"q1","kind":"swap","from":"B2","to":"C2","status":"pending","createdAt":5,"note":"",
  "ev":{"type":"swap","aId":"B2","aDate":"2026-10-16","bId":"C2","bDate":"2026-10-18","shift":"주","createdAt":5}},
  {"_id":"q2","kind":"leave","from":"A1","to":"B2","status":"pending","createdAt":6,"note":"",
  "ev":{"type":"leave","memberId":"A1","date":"2026-10-21","shift":"주","leaveType":"연차","coverType":"cover","coverId":"B2"}},
  {"_id":"q3","kind":"swap","from":"B2","to":"D2","status":"rejected","createdAt":7,"decidedAt":1792400000000,"note":"",
  "ev":{"type":"swap","aId":"B2","aDate":"2026-10-23","bId":"D2","bDate":"2026-10-25","shift":"주","createdAt":7}}]
from playwright.sync_api import sync_playwright
with sync_playwright() as p:
    b=getattr(p,os.environ.get('BROWSER','chromium')).launch()
    ctx,pg,errs=R['page'](b,R['ADMIN']); pg.goto(R['BASE']); pg.wait_for_timeout(500)
    pg.click('[data-g=google]'); pg.wait_for_timeout(500)
    f=tempfile.NamedTemporaryFile('w',suffix='.json',delete=False); json.dump(D,f,ensure_ascii=False); f.close()
    pg.set_input_files('#gImport',f.name); pg.click('[data-g=setup]'); pg.wait_for_timeout(1500)
    pg.click('[data-g=pick][data-id=B2]'); pg.wait_for_timeout(800)
    print('reqs', pg.evaluate("JSON.parse(sessionStorage.getItem('mockstore')).filter(([k])=>/requests\\//.test(k)).map(([k,v])=>v.status)"))
    pg.click('[data-tab=attend]'); pg.wait_for_timeout(300); print('sum:', pg.inner_text('#rqSum').replace('\n',' '))
    print('badge:', pg.inner_text('nav.tabs [data-tab=attend] .badge'))
    pg.screenshot(path=SHOT+'/pend_att.png')
    pg.click('#rqSum .rqbox.res'); pg.wait_for_timeout(300)
    print('detail result:', 'outShown', pg.is_visible('#ibOut'), 'outRes', pg.locator('#outList .item.res.no [data-act=rseen]').count(), 'inRes', pg.locator('#inList .item.res').count(), 'btn', pg.inner_text('#ibOutBtn'))
    pg.screenshot(path=SHOT+'/pend_out.png')
    pg.click('[data-tab=my]'); pg.wait_for_timeout(200); pg.click('[data-mv=month]'); pg.wait_for_timeout(300)
    print('month rq:', pg.eval_on_selector_all('.ml.rq','e=>e.map(x=>x.textContent)'))
    pg.click('[data-tab=team]'); pg.wait_for_timeout(200); pg.click('[data-tv=print]'); pg.wait_for_timeout(500)
    print('sheet rq:', pg.eval_on_selector_all('#sheet .rq','e=>e.map(x=>x.textContent)'))
    pg.click('#toSimple'); pg.wait_for_timeout(300); pg.click('[data-stab=sheet]'); pg.wait_for_timeout(300)
    print('simple sheet rq:', pg.eval_on_selector_all('#simple .rq','e=>e.map(x=>x.textContent)'))
    pg.click('[data-stab=attend]'); pg.wait_for_timeout(300); pg.screenshot(path=SHOT+'/pend_sm.png',full_page=True)
    print('simple attend:', pg.inner_text('#simple .sm-body')[:200].replace('\n',' | '))
    print('result card:', pg.locator('.sm-req.res.no').count())
    pg.click('.sm-req.res [data-act=rseen]'); pg.wait_for_timeout(600)
    print('after rseen:', pg.locator('.sm-req.res').count(), pg.evaluate("JSON.parse(sessionStorage.getItem('mockstore')).filter(([k])=>/requests\\/q3$/.test(k)).map(([k,v])=>v.fromSeen)"))
    pg.click('.sm-req.mine [data-act=cancel]'); pg.wait_for_timeout(600)
    pg.click('[data-stab=sheet]'); pg.wait_for_timeout(300)
    print('after cancel rq:', pg.eval_on_selector_all('#simple .rq','e=>e.map(x=>x.textContent)'))
    print('errs', [e for e in errs if 'ERR_FAILED' not in e])
