# 편성표·근무요약 이미지/PDF 저장 버튼이 실제로 파일을 만드는지
import sys, os, json, tempfile
SHOT=sys.argv[1]; sys.argv=[sys.argv[0]]
src=open(os.path.join(os.path.dirname(__file__),'run.py')).read().replace('\nrun()','\n')
R={'__file__':os.path.join(os.path.dirname(os.path.abspath(__file__)),'run.py')}; exec(compile(src,'run.py','exec'),R)
from playwright.sync_api import sync_playwright
with sync_playwright() as p:
    b=p.chromium.launch()
    MOB=os.environ.get('MOBILE')=='1'
    if MOB:
        ctx=b.new_context(**p.devices['Pixel 7'],service_workers='block',accept_downloads=True); pg=ctx.new_page(); errs=[]
        pg.on('pageerror',lambda e:errs.append(str(e))); pg.on('console',lambda m:errs.append('console:'+m.text) if m.type=='error' else None)
        pg.route('**/fonts.googleapis.com/**',lambda r:r.abort())
        pg.route('**/www.gstatic.com/firebasejs/**',lambda r:r.fulfill(body=R['MOCK'],content_type='application/javascript'))
        pg.route('**/config.js',lambda r:r.fulfill(body=R['CFG'],content_type='application/javascript'))
        pg.add_init_script("window.__fbNext=%s;"%json.dumps(R['ADMIN']))
        pg.add_init_script("try{localStorage.setItem('shift-simple','0')}catch(e){}")
        # 휴대폰 공유창 흉내: 사용자 동작 후 5초가 지나면 NotAllowedError (Chrome 규칙)
        pg.add_init_script("""(()=>{let t=0;addEventListener('click',()=>t=performance.now(),true);
          navigator.canShare=()=>true;navigator.share=async()=>{const dt=performance.now()-t;console.log('share after ms',Math.round(dt));if(dt>%s)throw new DOMException('Must be handling a user gesture','NotAllowedError');window.__shared=(window.__shared||0)+1}})()"""%os.environ.get('SHARE_LIMIT','5000'))
        pg.on('console',lambda m:print('  console:',m.text) if 'share after' in m.text else None)
        cdp=ctx.new_cdp_session(pg); cdp.send('Emulation.setCPUThrottlingRate',{'rate':4})
    else:
        ctx,pg,errs=R['page'](b,R['ADMIN'])
    pg.goto(R['BASE']); pg.wait_for_timeout(500)
    pg.click('[data-g=google]'); pg.wait_for_timeout(500)
    f=tempfile.NamedTemporaryFile('w',suffix='.json',delete=False); json.dump(R['TEST_DATA'],f,ensure_ascii=False); f.close()
    pg.set_input_files('#gImport',f.name); pg.click('[data-g=setup]'); pg.wait_for_timeout(1500)
    pg.click('[data-g=pick][data-id=B2]'); pg.wait_for_timeout(800)
    pg.click('[data-tab=team]'); pg.wait_for_timeout(200); pg.click('[data-tv=print]'); pg.wait_for_timeout(500)
    for bid,msg,tab in [('#pPng','#pMsg',None),('#pPdf','#pMsg',None),('#rpPng','#rpMsg','pay'),('#rpPdf','#rpMsg','pay')]:
        if tab: pg.click('[data-tab=%s]'%tab); pg.wait_for_timeout(300)
        if MOB:
            import time; t0=time.time(); pg.evaluate('window.__shared=0'); pg.click(bid)
            for _ in range(120):
                pg.wait_for_timeout(500); m=pg.inner_text(msg)
                if pg.locator('.pushask [data-x=go]').count(): print('  tap-again button shown'); pg.click('.pushask [data-x=go]'); continue
                if '✓' in m or '못했' in m: break
            print(bid,'MOBILE',round(time.time()-t0,1),'s shared=',pg.evaluate('window.__shared'),m[:120]); continue
        try:
            with pg.expect_download(timeout=20000) as d: pg.click(bid)
            path=SHOT+'/dl_'+d.value.suggested_filename; d.value.save_as(path); print(bid,'OK',d.value.suggested_filename,os.path.getsize(path))
        except Exception as e: print(bid,'FAIL',pg.inner_text(msg)[:200],str(e)[:100])
    print('errs', errs)
