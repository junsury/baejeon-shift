import sys, os, json
sys.argv=[sys.argv[0]]
sys.path.insert(0, os.path.dirname(__file__))
import run as R
from playwright.sync_api import sync_playwright
with sync_playwright() as p:
    b=p.chromium.launch()
    ctx,pg,errs=R.page(b,R.ADMIN); pg.goto(R.BASE); pg.wait_for_timeout(500)
    pg.click('[data-g=google]'); pg.wait_for_timeout(500)
    import tempfile
    f=tempfile.NamedTemporaryFile('w',suffix='.json',delete=False); json.dump(R.TEST_DATA,f,ensure_ascii=False); f.close()
    pg.set_input_files('#gImport',f.name); pg.click('[data-g=setup]'); pg.wait_for_timeout(1500)
    pg.click('[data-g=pick][data-id=B2]'); pg.wait_for_timeout(600)
    store=R.store_of(pg); ctx.close()
    for go in ['my-day','my-week','my-month','team-day','team-month']:
        ctx,pg,errs=R.page(b,R.ADMIN); R.with_store(pg,store,R.ADMIN); pg.goto(R.BASE+'?src=twa&go='+go); pg.wait_for_timeout(1200)
        st=pg.evaluate("[document.querySelector('[data-tab].on,[data-tab].active,[data-tab][aria-selected=true]')?.dataset.tab, [...document.querySelectorAll('[data-mv][aria-pressed=true],[data-tv][aria-pressed=true]')].map(x=>x.dataset.mv||x.dataset.tv).join(','), location.search]")
        print(go, st, errs[:2]); ctx.close()
