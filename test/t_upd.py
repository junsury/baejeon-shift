# 새로 바뀐 점(웹): 처음 기기는 조용히 기억, 지난번 이후 바뀐 게 있으면 창 / 👤 업데이트 기록
import sys, os, json, tempfile
SHOT=sys.argv[1]; sys.argv=[sys.argv[0]]
src=open(os.path.join(os.path.dirname(__file__),'run.py')).read().replace('\nrun()','\n')
R={'__file__':os.path.join(os.path.dirname(os.path.abspath(__file__)),'run.py')}; exec(compile(src,'run.py','exec'),R)
from playwright.sync_api import sync_playwright
def go(b,seen,label):
    ctx,pg,errs=R['page'](b,R['ADMIN'])
    if seen: pg.add_init_script("try{localStorage.getItem('bj-upd-seen')||localStorage.setItem('bj-upd-seen','%s')}catch(e){}"%seen)
    pg.goto(R['BASE']); pg.wait_for_timeout(500)
    pg.click('[data-g=google]'); pg.wait_for_timeout(500)
    f=tempfile.NamedTemporaryFile('w',suffix='.json',delete=False); json.dump(R['TEST_DATA'],f,ensure_ascii=False); f.close()
    pg.set_input_files('#gImport',f.name); pg.click('[data-g=setup]'); pg.wait_for_timeout(1500)
    pg.click('[data-g=pick][data-id=B2]'); pg.wait_for_timeout(3500)
    print(label,'modal',pg.locator('.updm').count())
    if pg.locator('.updm').count(): pg.screenshot(path=SHOT+'/upd_new.png'); pg.click('.updm [data-x]'); pg.wait_for_timeout(200)
    return ctx,pg,errs
with sync_playwright() as p:
    b=getattr(p,os.environ.get('BROWSER','chromium')).launch()
    ctx,pg,errs=go(b,None,'first'); ctx.close()
    ctx,pg,errs=go(b,'2026-10-21-simple','after-old')
    pg.click('#acctBtn'); pg.wait_for_timeout(300); pg.click('[data-ac=updLog]'); pg.wait_for_timeout(800)
    print('log modal',pg.locator('.updm').count(),'items',pg.locator('.updm li').count()>5)
    pg.screenshot(path=SHOT+'/upd_log.png')
    print('errs',[e for e in errs if 'ERR_FAILED' not in e])
