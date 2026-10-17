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
    pg.click('[data-g=pick][data-id=B2]'); pg.wait_for_timeout(800)
    pg.click('[data-tab=team]'); pg.wait_for_timeout(300); pg.click('[data-tv=print]'); pg.wait_for_timeout(800)
    pg.once('dialog', lambda d: d.accept('팀 회식'))
    pg.evaluate("document.querySelector('#sheet td[data-rm]').click()"); pg.wait_for_timeout(800)
    print('after js click', pg.inner_text('#sheet td[data-rm]'))
    pg.once('dialog', lambda d: d.accept('두번째'))
    box=pg.query_selector('#sheet tr:nth-child(3) td[data-rm]').bounding_box(); print(box)
    pg.mouse.click(box['x']+box['width']/2, box['y']+box['height']/2); pg.wait_for_timeout(800)
    print('mouse', pg.inner_text('#sheet tr:nth-child(3) td[data-rm]'))
    print(pg.inner_text('#sheet td[data-rm]'), errs[:3])
