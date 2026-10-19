# 갤럭시 앱(새 버전, ?appv)에서 메모 저장 → 위젯 바로 새로 고침(intent://refresh) 부르기. 옛 앱(appv 없음)은 안 부름
import sys, os, json, tempfile
SHOT=sys.argv[1]; sys.argv=[sys.argv[0]]
src=open(os.path.join(os.path.dirname(__file__),'run.py')).read().replace('\nrun()','\n')
R={'__file__':os.path.join(os.path.dirname(os.path.abspath(__file__)),'run.py')}; exec(compile(src,'run.py','exec'),R)
from playwright.sync_api import sync_playwright
def go(b, q):
    ctx,pg,errs=R['page'](b,R['ADMIN']); pg.goto(R['BASE']+q); pg.wait_for_timeout(500)
    hit=[]
    pg.on('request', lambda r: hit.append(r.url) if r.url.startswith('intent:') else None)
    pg.on('requestfailed', lambda r: hit.append(r.url) if r.url.startswith('intent:') else None)
    pg.click('[data-g=google]'); pg.wait_for_timeout(500)
    f=tempfile.NamedTemporaryFile('w',suffix='.json',delete=False); json.dump(R['TEST_DATA'],f,ensure_ascii=False); f.close()
    pg.set_input_files('#gImport',f.name); pg.click('[data-g=setup]'); pg.wait_for_timeout(1500)
    pg.click('[data-g=pick][data-id=B2]'); pg.wait_for_timeout(800)
    if pg.locator('#toDetail').count() and pg.locator('#toDetail').is_visible(): pg.click('#toDetail'); pg.wait_for_timeout(300)
    pg.click('[data-tab=my]'); pg.wait_for_timeout(300); pg.click('[data-mv=day]'); pg.wait_for_timeout(300)
    pg.fill('#pmemo','시험 메모'); pg.click('#pmSave'); pg.wait_for_timeout(1500)
    alive=pg.evaluate("!!document.querySelector('#pmemo')")
    print(q, 'refresh calls', pg.evaluate("window.__pinged||0"), 'page alive', alive, 'errs', [e for e in errs if 'ERR_' not in e and 'intent' not in e])
    ctx.close()
with sync_playwright() as p:
    b=getattr(p,os.environ.get('BROWSER','chromium')).launch()
    go(b,'?src=twa&appv=40')
    go(b,'?src=twa')
    go(b,'')
