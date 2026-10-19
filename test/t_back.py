# 뒤로 가기: 이전 화면으로 → 맨 처음에선 종료 확인 창
import sys, os, json, tempfile
SHOT=sys.argv[1]; sys.argv=[sys.argv[0]]
src=open(os.path.join(os.path.dirname(__file__),'run.py')).read().replace('\nrun()','\n')
R={'__file__':os.path.join(os.path.dirname(os.path.abspath(__file__)),'run.py')}; exec(compile(src,'run.py','exec'),R)
from playwright.sync_api import sync_playwright
with sync_playwright() as p:
    b=getattr(p,os.environ.get('BROWSER','chromium')).launch()
    ctx,pg,errs=R['page'](b,R['ADMIN']); pg.goto(R['BASE']); pg.wait_for_timeout(500)
    pg.click('[data-g=google]'); pg.wait_for_timeout(500)
    f=tempfile.NamedTemporaryFile('w',suffix='.json',delete=False); json.dump(R['TEST_DATA'],f,ensure_ascii=False); f.close()
    pg.set_input_files('#gImport',f.name); pg.click('[data-g=setup]'); pg.wait_for_timeout(1500)
    pg.click('[data-g=pick][data-id=B2]'); pg.wait_for_timeout(800)
    tab=lambda: pg.evaluate("document.querySelector('nav.tabs [aria-selected=true]').dataset.tab")
    pg.click('[data-tab=team]'); pg.wait_for_timeout(200); pg.click('[data-tv=month]'); pg.wait_for_timeout(200)
    pg.click('[data-tab=attend]'); pg.wait_for_timeout(200)
    print('now', tab())
    pg.go_back(); pg.wait_for_timeout(300); print('back1', tab(), pg.evaluate("document.querySelector('#teamSeg [aria-pressed=true]').dataset.tv"))
    pg.go_back(); pg.wait_for_timeout(300); print('back2', tab(), pg.evaluate("document.querySelector('#teamSeg [aria-pressed=true]').dataset.tv"))
    pg.click('#acctBtn'); pg.wait_for_timeout(200); pg.go_back(); pg.wait_for_timeout(300)
    print('acct closed', pg.evaluate("document.getElementById('acct').hidden"), tab())
    pg.click('#toSimple'); pg.wait_for_timeout(300); pg.click('[data-stab=attend]'); pg.wait_for_timeout(200); pg.click('[data-flow=leave]'); pg.wait_for_timeout(300)
    pg.go_back(); pg.wait_for_timeout(300); print('flow back', pg.evaluate("!!document.querySelector('#simple .sm-act')"))
    for i in range(12):
        pg.go_back(); pg.wait_for_timeout(250)
        if pg.locator('.exitask').count(): break
    print('exit dialog', pg.locator('.exitask').count(), 'after', i+1)
    pg.click('.exitask [data-x=no]'); pg.wait_for_timeout(200)
    print('dialog gone', pg.locator('.exitask').count()==0)
    pg.go_back(); pg.wait_for_timeout(300); print('exit again', pg.locator('.exitask').count())
    print('errs', [e for e in errs if 'ERR_FAILED' not in e])
