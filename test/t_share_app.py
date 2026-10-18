# 갤럭시 앱(TWA)에서 [카톡 등으로 공유하기]: 서버에 올린 뒤 앱 공유로 넘어가는지
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
    store=R['store_of'](pg); ctx.close()
    ctx=b.new_context(**p.devices['Pixel 7'],service_workers='block'); pg=ctx.new_page(); errs=[]
    pg.on('pageerror',lambda e:errs.append(str(e)))
    pg.route('**/fonts.googleapis.com/**',lambda r:r.abort())
    pg.route('**/www.gstatic.com/firebasejs/**',lambda r:r.fulfill(body=R['MOCK'],content_type='application/javascript'))
    pg.route('**/config.js',lambda r:r.fulfill(body=R['CFG'],content_type='application/javascript'))
    pg.add_init_script("window.__fbNext=%s;"%json.dumps(R['ADMIN']))
    pg.add_init_script("sessionStorage.setItem('mockstore',%s);sessionStorage.setItem('mockuser',%s);try{localStorage.setItem('shift-simple','0')}catch(e){}"%(json.dumps(store),json.dumps(json.dumps(R['ADMIN']))))
    # 파일 만드는 부분(html2canvas)은 CI에서만 진짜 — 여기선 가짜 그림으로 바로 저장 호출
    pg.goto(R['BASE']+'?src=twa'); pg.wait_for_timeout(1500)
    pg.evaluate("(async()=>{const dl=await window.__appDownloads();const big=new Uint8Array(900000).fill(65);window.__res=dl.save({filename:'근무편성표_test.png',data:new Blob([big],{type:'image/png'})}).then(r=>window.__done=r,e=>window.__done=e)})()")
    pg.wait_for_timeout(1500)
    print('button:', pg.inner_text('.shsheet [data-x=share]'))
    nav=[]; pg.on('request',lambda r:nav.append(r.url) if 'intent' in r.url else None)
    pg.click('.shsheet [data-x=share]'); pg.wait_for_timeout(800)
    st=json.loads(R['store_of'](pg)); sh=[k for k,v in st if '/shares/' in k]
    print('shares docs:', len(sh), [k.split('/shares/')[1][-6:] for k in sh][:4])
    print('done:', pg.evaluate('JSON.stringify(window.__done)'), 'sheet gone:', pg.locator('.shsheet').count()==0)
    print('errs', [e for e in errs if 'ERR_FAILED' not in e])
