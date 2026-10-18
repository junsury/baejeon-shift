# 글자 대비(시인성) 점검: 화면별로 대비가 낮은 글자 목록 (WCAG AA 기준)
import sys, os, json, tempfile
SHOT=sys.argv[1]; AUD=open(sys.argv[2] if len(sys.argv)>2 else os.path.join(os.path.dirname(os.path.abspath(__file__)),"contrast-audit.js")).read(); sys.argv=[sys.argv[0]]
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
    for scheme in ['light','dark']:
        ctx=b.new_context(viewport={'width':390,'height':844},color_scheme=scheme,service_workers='block'); pg=ctx.new_page()
        pg.route('**/fonts.googleapis.com/**',lambda r:r.abort())
        pg.route('**/www.gstatic.com/firebasejs/**',lambda r:r.fulfill(body=R['MOCK'],content_type='application/javascript'))
        pg.route('**/config.js',lambda r:r.fulfill(body=R['CFG'],content_type='application/javascript'))
        pg.add_init_script("window.__fbNext=%s;"%json.dumps(R['ADMIN']))
        pg.add_init_script("sessionStorage.setItem('mockstore',%s);sessionStorage.setItem('mockuser',%s);sessionStorage.setItem('bj-twa','1');try{localStorage.setItem('shift-simple','0')}catch(e){}"%(json.dumps(store),json.dumps(json.dumps(R['ADMIN']))))
        pg.goto(R['BASE']); pg.wait_for_timeout(1500)
        screens=[('my-day',"[data-tab=my]","[data-mv=day]"),('my-week',None,"[data-mv=week]"),('my-month',None,"[data-mv=month]"),
                 ('team-day',"[data-tab=team]","[data-tv=day]"),('team-month',None,"[data-tv=month]"),('team-print',None,"[data-tv=print]"),
                 ('attend',"[data-tab=attend]",None),('budget',"[data-tab=budget]",None),('pay',"[data-tab=pay]",None),('admin',"[data-tab=admin]",None)]
        for name,a,c in screens:
            try:
                if a: pg.click(a); pg.wait_for_timeout(250)
                if c: pg.click(c); pg.wait_for_timeout(250)
            except Exception as e: print(scheme,name,'skip'); continue
            r=pg.evaluate(AUD); print(scheme,name,len(r)); [print('   ',x) for x in r[:30]]
            pg.screenshot(path=SHOT+'/c_%s_%s.png'%(scheme,name))
        pg.click('#toSimple'); pg.wait_for_timeout(300)
        for t in ['my','sheet','budget','attend','pay']:
            pg.click('[data-stab=%s]'%t); pg.wait_for_timeout(250)
            r=pg.evaluate(AUD); print(scheme,'simple-'+t,len(r)); [print('   ',x) for x in r[:30]]
        ctx.close()
