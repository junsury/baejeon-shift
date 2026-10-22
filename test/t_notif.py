# 확인할 게 없으면 알림 창의 근무표 알림을 지움(아이콘 숫자 없애기), 남아 있으면 그대로
import sys, os, json, tempfile
SHOT=sys.argv[1]; sys.argv=[sys.argv[0]]
src=open(os.path.join(os.path.dirname(__file__),'run.py')).read().replace('\nrun()','\n')
R={'__file__':os.path.join(os.path.dirname(os.path.abspath(__file__)),'run.py')}; exec(compile(src,'run.py','exec'),R)
FAKE="""(()=>{window.__closed=0;const reg={getNotifications:()=>Promise.resolve([{tag:'rq-1',close(){window.__closed++}},{tag:'tomorrow',close(){window.__info=(window.__info||0)+1}}])};
try{Object.defineProperty(navigator,'serviceWorker',{configurable:true,value:{getRegistration:()=>Promise.resolve(reg),register:()=>Promise.resolve(reg),ready:Promise.resolve(reg),addEventListener(){} }})}catch(e){}})()"""
def go(b, data, label):
    ctx,pg,errs=R['page'](b,R['ADMIN']); pg.add_init_script(FAKE); pg.goto(R['BASE']); pg.wait_for_timeout(500)
    pg.click('[data-g=google]'); pg.wait_for_timeout(500)
    f=tempfile.NamedTemporaryFile('w',suffix='.json',delete=False); json.dump(data,f,ensure_ascii=False); f.close()
    pg.set_input_files('#gImport',f.name); pg.click('[data-g=setup]'); pg.wait_for_timeout(1500)
    pg.click('[data-g=pick][data-id=B2]'); pg.wait_for_timeout(2500)
    pg.click('[data-tab=team]'); pg.wait_for_timeout(1800); pg.click('[data-tab=my]'); pg.wait_for_timeout(500)
    print(label, 'closed', pg.evaluate('window.__closed')>0, 'info', pg.evaluate('(window.__info||0)')>0)
    ctx.close()
from playwright.sync_api import sync_playwright
with sync_playwright() as p:
    b=getattr(p,os.environ.get('BROWSER','chromium')).launch()
    D=json.loads(json.dumps(R['TEST_DATA'])); D['requests']=[]
    go(b, D, 'nothing-left')
    D2=json.loads(json.dumps(R['TEST_DATA']))
    D2['requests']=[{"_id":"q2","kind":"leave","from":"A1","to":"B2","status":"pending","createdAt":6,"note":"",
      "ev":{"type":"leave","memberId":"A1","date":"2026-12-21","shift":"주","leaveType":"연차","coverType":"cover","coverId":"B2"}}]
    go(b, D2, 'pending-left')
