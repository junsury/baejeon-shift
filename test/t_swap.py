import sys, os, json, tempfile
SHOT=sys.argv[1]; sys.argv=[sys.argv[0]]
sys.path.insert(0, os.path.dirname(__file__))
import importlib.util
spec=importlib.util.spec_from_file_location('R',os.path.join(os.path.dirname(__file__),'run.py'))
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
    pg.click('[data-tab=attend]'); pg.wait_for_timeout(300)
    pg.click('#fSwap'); pg.wait_for_timeout(300)
    pg.select_option('#sA','B2'); pg.wait_for_timeout(200)
    for d in ['2026-10-23','2026-10-25','2026-10-26']:
        pg.evaluate("d=>{const i=document.getElementById('sADate');i.value=d;i.dispatchEvent(new Event('change'))}", d); pg.wait_for_timeout(300)
        print(d, pg.eval_on_selector_all('#sB option','os=>os.map(o=>o.textContent)'), pg.inner_text('#sBHint'))
    opts=pg.eval_on_selector_all('#sB option','os=>os.map(o=>o.value).filter(Boolean)')
    if opts:
        pg.select_option('#sB',opts[0]); pg.wait_for_timeout(300)
        print('B date', pg.input_value('#sBDate'), pg.inner_text('#sResult')[:120].replace('\n',' | '))
        pg.click('#sBDateBtn'); pg.wait_for_timeout(300)
        print('ok dates', pg.eval_on_selector_all('#pop-sBDate button.ok','bs=>bs.length'), 'no', pg.eval_on_selector_all('#pop-sBDate button.no','bs=>bs.length'))
        pg.screenshot(path=SHOT+'/swap.png',full_page=False)
    pg.click('#acctBtn'); pg.wait_for_timeout(300); print('acct:', pg.inner_text('#acctBody')[:200].replace('\n',' | '))
    print('errs', [e for e in errs if 'ERR_FAILED' not in e])
