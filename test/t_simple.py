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
    print('full first (admin):', pg.evaluate("document.getElementById('simple').hidden"))
    pg.click('#toSimple'); pg.wait_for_timeout(500)
    print('simple on:', pg.evaluate("!document.getElementById('simple').hidden && document.querySelector('header.top').hidden"))
    print('mode saved:', pg.evaluate("JSON.parse(sessionStorage.getItem('mockstore')).filter(([k])=>/users\\/uA$/.test(k)).map(([k,v])=>v.mode)"))
    for t in ['my','sheet','budget','attend','pay']:
        pg.click('[data-stab=%s]'%t); pg.wait_for_timeout(400); pg.screenshot(path=SHOT+'/sm_%s.png'%t)
        print(t, pg.inner_text('#simple .sm-body')[:90].replace('\n',' | '))
        print('  hscroll', pg.evaluate("document.documentElement.scrollWidth>innerWidth"))
    pg.click('[data-stab=sheet]'); pg.wait_for_timeout(400)
    print('sheet today visible:', pg.evaluate("(()=>{const r=document.querySelector('.sm-sh-row.today').getBoundingClientRect(),s=document.querySelector('.sm-sh-scroll').getBoundingClientRect();return r.top>=s.top&&r.bottom<=s.bottom})()"))
    pg.click('[data-s=shB]'); pg.wait_for_timeout(300); print('rows', pg.eval_on_selector_all('.sm-sh-row:not(.head)','r=>r.length'))
    # 휴가 바로 반영
    pg.click('[data-stab=attend]'); pg.wait_for_timeout(300); pg.click('[data-flow=leave]'); pg.wait_for_timeout(300)
    pg.click('[data-pick]'); pg.wait_for_timeout(600); pg.screenshot(path=SHOT+'/sm_leave.png')
    nc=pg.eval_on_selector_all('[data-cand]','b=>b.length'); print('leave cands', nc)
    ev0=pg.evaluate("JSON.parse(sessionStorage.getItem('mockstore')).filter(([k])=>/\\/events\\//.test(k)).length")
    if nc:
        pg.click('[data-cand]'); pg.wait_for_timeout(300); pg.screenshot(path=SHOT+'/sm_leave2.png')
        pg.click('[data-s=go-direct]'); pg.wait_for_timeout(800)
        print('after direct:', pg.inner_text('#simple .sm-ok')[:80])
        print('events', ev0, '->', pg.evaluate("JSON.parse(sessionStorage.getItem('mockstore')).filter(([k])=>/\\/events\\//.test(k)).length"))
    # 근무 바꾸기 요청
    pg.click('[data-flow=swap]'); pg.wait_for_timeout(300); pg.click('[data-pick]'); pg.wait_for_timeout(1500)
    nc=pg.eval_on_selector_all('[data-cand]','b=>b.length'); print('swap cands', nc)
    if nc:
        pg.click('[data-cand]'); pg.wait_for_timeout(300); pg.screenshot(path=SHOT+'/sm_swap.png')
        pg.click('[data-s=go-req]'); pg.wait_for_timeout(800); print('after req:', pg.inner_text('#simple .sm-ok')[:80])
    pg.click('[data-flow=tso]'); pg.wait_for_timeout(300)
    print('tso days', pg.eval_on_selector_all('[data-pick]','b=>b.length'))
    pg.click('[data-s=back]'); pg.wait_for_timeout(200)
    pg.click('[data-s=full]'); pg.wait_for_timeout(300)
    print('back to full:', pg.evaluate("document.getElementById('simple').hidden"))
    print('errs', [e for e in errs if 'ERR_FAILED' not in e])
    ctx.close()
    # 다크 모드
    ctx=b.new_context(viewport={'width':390,'height':844},color_scheme='dark',service_workers='block')
