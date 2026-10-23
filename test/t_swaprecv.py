# 근무변경 '이 날 근무 받기': 받고 싶은 날 → 근무 → 사람 → 대신 줄 내 날짜 → 요청
import sys, os, json, tempfile
SHOT=sys.argv[1]; sys.argv=[sys.argv[0]]
src=open(os.path.join(os.path.dirname(__file__),'run.py')).read().replace('\nrun()','\n')
R={'__file__':os.path.join(os.path.dirname(os.path.abspath(__file__)),'run.py')}; exec(compile(src,'run.py','exec'),R)
from playwright.sync_api import sync_playwright
with sync_playwright() as p:
    b=getattr(p,os.environ.get('BROWSER','chromium')).launch()
    ctx,pg,errs=R['page'](b,R['ADMIN'],420); pg.goto(R['BASE']); pg.wait_for_timeout(500)
    pg.click('[data-g=google]'); pg.wait_for_timeout(500)
    f=tempfile.NamedTemporaryFile('w',suffix='.json',delete=False); json.dump(R['TEST_DATA'],f,ensure_ascii=False); f.close()
    pg.set_input_files('#gImport',f.name); pg.click('[data-g=setup]'); pg.wait_for_timeout(1500)
    pg.click('[data-g=pick][data-id=B2]'); pg.wait_for_timeout(800)
    pg.click('[data-tab=attend]'); pg.wait_for_timeout(300); pg.click('[data-att=req]'); pg.wait_for_timeout(300); pg.click('#fSwap'); pg.wait_for_timeout(300)
    pg.click('[data-swm=recv]'); pg.wait_for_timeout(300)
    print('give rows hidden', pg.evaluate("document.querySelector('#swapForm .row.sw-give').hidden"))
    pg.click('[data-rvym="1"]'); pg.wait_for_timeout(200)
    pg.locator('[data-rvd]').nth(3).click(); pg.wait_for_timeout(300)
    print('people', pg.locator('#swRecv [data-rvw]').count())
    pg.locator('#swRecv [data-rvw]').first.click(); pg.wait_for_timeout(300)
    pg.locator('#swRecv [data-rvm]').first.click(); pg.wait_for_timeout(500)
    print('result ok', pg.locator('#sResult .msg.ok').count(), 'save enabled', pg.is_enabled('#sSave'))
    pg.screenshot(path=SHOT+'/swap_recv.png',full_page=True)
    pg.click('#sSave'); pg.wait_for_timeout(800)
    rq=pg.evaluate("JSON.parse(sessionStorage.getItem('mockstore')).filter(([k])=>/requests\\//.test(k)).map(([k,v])=>v)")
    r=[x for x in rq if x.get('kind')=='swap']
    print('swap req', len(r), r and r[-1]['ev']['aId']=='B2' and r[-1]['ev']['bId']!='B2')
    print('errs', [e for e in errs if 'ERR_FAILED' not in e])
