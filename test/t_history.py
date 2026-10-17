import sys,json,os
sys.argv=[sys.argv[0],'/tmp']
exec(open(os.path.join(os.path.dirname(__file__),'run.py')).read().split('def run():')[0])
with sync_playwright() as p:
    b=p.chromium.launch()
    ctx,pg,errs=page(b,ADMIN); pg.goto(BASE); pg.wait_for_timeout(400); pg.click('[data-g=google]'); pg.wait_for_timeout(400)
    f=tempfile.NamedTemporaryFile('w',suffix='.json',delete=False); json.dump(TEST_DATA,f,ensure_ascii=False); f.close()
    pg.set_input_files('#gImport',f.name); pg.click('[data-g=setup]'); pg.wait_for_timeout(1500); pg.click('[data-g=pick][data-id=B2]'); pg.wait_for_timeout(600)
    pg.click('nav.tabs [data-tab=attend]'); pg.click('[data-att=hist]'); pg.wait_for_timeout(300)
    pg.click('#histUp [data-del=e1]'); pg.click('#histUp [data-del=e1][data-confirm]'); pg.wait_for_timeout(600)
    print(pg.inner_text('#logCard')[:200])
    pg.click('#acctBtn'); pg.wait_for_timeout(200); print('remind chk present:', pg.query_selector('#remindChk') is not None)
    pg.click('[data-ac=close]'); pg.click('#tabAdmin'); pg.click('[data-at=set]'); pg.wait_for_timeout(300); print(pg.inner_text('#backupCard')[:80])
    print('errs',[e for e in errs if 'ERR_FAILED' not in e])
