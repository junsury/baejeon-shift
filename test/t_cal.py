import sys, os, json, tempfile
SHOT=sys.argv[1]; sys.argv=[sys.argv[0]]
src=open(os.path.join(os.path.dirname(__file__),'run.py')).read().replace('\nrun()','\n')
R={'__file__':os.path.join(os.path.dirname(os.path.abspath(__file__)),'run.py')}; exec(compile(src,'run.py','exec'),R)
from playwright.sync_api import sync_playwright
with sync_playwright() as p:
    b=p.chromium.launch()
    ctx,pg,errs=R['page'](b,R['ADMIN']); pg.goto(R['BASE']); pg.wait_for_timeout(500)
    pg.click('[data-g=google]'); pg.wait_for_timeout(500)
    f=tempfile.NamedTemporaryFile('w',suffix='.json',delete=False); json.dump(R['TEST_DATA'],f,ensure_ascii=False); f.close()
    pg.set_input_files('#gImport',f.name); pg.click('[data-g=setup]'); pg.wait_for_timeout(1500)
    pg.click('[data-g=pick][data-id=B2]'); pg.wait_for_timeout(600)
    store=json.loads(R['store_of'](pg)); ctx.close()
    store.append(['teams/gyeonggi/calcache/B2',{'at':1,'items':[{'d':'2026-10-20','t':'치과','h':'15:00'},{'d':'2026-10-22','t':'테스트','h':''}]}])
    ctx,pg,errs=R['page'](b,R['ADMIN']); R['with_store'](pg,json.dumps(store),R['ADMIN']); pg.goto(R['BASE']+'?go=my-day&d=2026-10-20'); pg.wait_for_timeout(1500)
    print(pg.inner_text('body')[:900], errs[:3])
    pg.screenshot(path=SHOT+'/cal_month.png',full_page=True)
