# 편성표·근무요약: A4(인쇄)·카톡용 큰 글씨 모양 확인
import sys, os, json, tempfile
SHOT=sys.argv[1]; sys.argv=[sys.argv[0]]
src=open(os.path.join(os.path.dirname(__file__),'run.py')).read().replace('\nrun()','\n')
R={'__file__':os.path.join(os.path.dirname(os.path.abspath(__file__)),'run.py')}; exec(compile(src,'run.py','exec'),R)
from playwright.sync_api import sync_playwright
with sync_playwright() as p:
    b=getattr(p,os.environ.get('BROWSER','chromium')).launch()
    ctx,pg,errs=R['page'](b,R['ADMIN'],w=1000); pg.goto(R['BASE']); pg.wait_for_timeout(500)
    pg.click('[data-g=google]'); pg.wait_for_timeout(500)
    f=tempfile.NamedTemporaryFile('w',suffix='.json',delete=False); json.dump(R['TEST_DATA'],f,ensure_ascii=False); f.close()
    pg.set_input_files('#gImport',f.name); pg.click('[data-g=setup]'); pg.wait_for_timeout(1500)
    pg.click('[data-g=pick][data-id=B2]'); pg.wait_for_timeout(800)
    pg.click('[data-tab=team]'); pg.wait_for_timeout(200); pg.click('[data-tv=print]'); pg.wait_for_timeout(500)
    pg.evaluate("""(()=>{const mk=(cls,html,a4)=>{const d=document.createElement('div');d.className=cls;d.style.cssText='position:absolute;left:0;top:0;z-index:99';d.innerHTML=a4?'<div class=a4in>'+html+'</div>':html;document.body.appendChild(d);if(a4)window.__fitA4(d);return d};
      window.__a=mk('sheet a4page',window.__sheetHtml(false),1);})()""")
    pg.wait_for_timeout(300); pg.locator('.sheet.a4page').last.screenshot(path=SHOT+'/print_a4.png')
    print('a4 scale', pg.evaluate("window.__a.firstElementChild.style.transform||'none'"))
    pg.evaluate("window.__a.remove()")
    pg.evaluate("(()=>{const d=document.createElement('div');d.className='sheet kk';d.id='kkx';d.style.cssText='position:absolute;left:0;top:0;z-index:99';d.innerHTML=window.__sheetHtml(true);document.body.appendChild(d)})()")
    pg.wait_for_timeout(300); pg.locator('#kkx').screenshot(path=SHOT+'/print_kk.png'); print('kk size', pg.evaluate("[kkx.offsetWidth,kkx.offsetHeight]"))
    pg.evaluate("kkx.remove()")
    pg.click('[data-tab=pay]'); pg.wait_for_timeout(300)
    pg.evaluate("(()=>{const d=document.createElement('div');d.className='sheet report kk';d.id='rk';d.style.cssText='position:absolute;left:0;top:0;z-index:99';d.innerHTML=window.__reportHtml();document.body.appendChild(d)})()")
    pg.wait_for_timeout(300); pg.locator('#rk').screenshot(path=SHOT+'/report_kk.png'); print('rk size', pg.evaluate("[rk.offsetWidth,rk.offsetHeight]"))
    print('errs', [e for e in errs if 'ERR_FAILED' not in e])
    ctx.close()
    # 휴대폰 화면의 편성표 미리보기
    ctx,pg,errs=R['page'](b,R['ADMIN']); R['with_store'](pg,None) if False else None
