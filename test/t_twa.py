# 갤럭시 앱으로 알아보기: ?src=twa(아이콘) / ?appv(알림·링크로 열어도) / 한 번 앱으로 열린 폰은 기억
import sys, os, json, tempfile
SHOT=sys.argv[1]; sys.argv=[sys.argv[0]]
src=open(os.path.join(os.path.dirname(__file__),'run.py')).read().replace('\nrun()','\n')
R={'__file__':os.path.join(os.path.dirname(os.path.abspath(__file__)),'run.py')}; exec(compile(src,'run.py','exec'),R)
from playwright.sync_api import sync_playwright
UA='Mozilla/5.0 (Linux; Android 14; SM-F956N) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0 Mobile Safari/537.36'
def check(pg,label):
    pg.click('[data-g=google]'); pg.wait_for_timeout(500)
    f=tempfile.NamedTemporaryFile('w',suffix='.json',delete=False); json.dump(R['TEST_DATA'],f,ensure_ascii=False); f.close()
    pg.set_input_files('#gImport',f.name); pg.click('[data-g=setup]'); pg.wait_for_timeout(1500)
    pg.click('[data-g=pick][data-id=B2]'); pg.wait_for_timeout(800)
    pg.click('[data-tab=my]'); pg.wait_for_timeout(200); pg.click('[data-mv=day]'); pg.wait_for_timeout(300)
    print(label, 'add btn', pg.locator('a.btn[href^="intent://day"]').count()>0)
with sync_playwright() as p:
    b=getattr(p,os.environ.get('BROWSER','chromium')).launch()
    ctx,pg,errs=R['page'](b,R['ADMIN']); pg.goto(R['BASE']+'?appv=41'); pg.wait_for_timeout(500); check(pg,'appv')
    print('remembered', pg.evaluate("localStorage.getItem('bj-twa-dev')"))
    ctx.close()
    ctx,pg,errs=R['page'](b,R['ADMIN']); pg.add_init_script("Object.defineProperty(navigator,'userAgent',{get:()=>'%s'});try{localStorage.setItem('bj-twa-dev','1')}catch(e){}"%UA)
    pg.goto(R['BASE']); pg.wait_for_timeout(500); check(pg,'remembered-android')
    ctx.close()
    ctx,pg,errs=R['page'](b,R['ADMIN']); pg.goto(R['BASE']); pg.wait_for_timeout(500); check(pg,'plain-pc')
    ctx.close()
