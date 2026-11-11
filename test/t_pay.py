# 근무요약: 팀 표는 가나다순 · 이름/대근/야간/휴가, 시간(횟수) / 내 근무요약에 공휴일·명절
import sys, os, json, tempfile
SHOT=sys.argv[1]; sys.argv=[sys.argv[0]]
src=open(os.path.join(os.path.dirname(__file__),'run.py')).read().replace('\nrun()','\n')
R={'__file__':os.path.join(os.path.dirname(os.path.abspath(__file__)),'run.py')}; exec(compile(src,'run.py','exec'),R)
from playwright.sync_api import sync_playwright
with sync_playwright() as p:
    b=getattr(p,os.environ.get('BROWSER','chromium')).launch()
    for w in (390,900):
        ctx,pg,errs=R['page'](b,R['ADMIN'],w); pg.goto(R['BASE']); pg.wait_for_timeout(500)
        pg.click('[data-g=google]'); pg.wait_for_timeout(500)
        f=tempfile.NamedTemporaryFile('w',suffix='.json',delete=False); D=json.loads(json.dumps(R['TEST_DATA']));import datetime;D['events']=D.get('events',[])+[{'_id':'tsup','type':'leave','memberId':[m['id'] for m in D['members'] if m.get('role')!='TSO' and m['team']=='A'][0],'date':'2026-10-27','shift':'주','leaveType':'연차','coverType':'tso','coverId':[m['id'] for m in D['members'] if m.get('role')=='TSO'][0],'createdAt':1}];json.dump(D,f,ensure_ascii=False); f.close()
        pg.set_input_files('#gImport',f.name); pg.click('[data-g=setup]'); pg.wait_for_timeout(1500)
        pg.click('[data-g=pick][data-id=B2]'); pg.wait_for_timeout(800)
        pg.click('[data-tab=pay]'); pg.wait_for_timeout(400)
        heads=pg.eval_on_selector_all('#sumTable thead th','e=>e.map(x=>x.textContent).join("|")')
        names=pg.eval_on_selector_all('#sumTable tbody tr td:first-child','e=>e.map(x=>x.textContent)')
        print(w,'heads',heads,'sorted',names[:-1]==sorted(names[:-1]),'fmt',pg.inner_text('#sumTable tbody tr:nth-child(1) td:nth-child(3)'))
        print(w,'total',pg.inner_text('#sumTable tbody tr:last-child td:nth-child(2)'));print(w,'my',pg.inner_text('#myPay').replace('\n',' | ')[:160])
        pg.locator('#myPay').scroll_into_view_if_needed(); pg.screenshot(path=SHOT+'/pay_%d.png'%w,full_page=True)
        print('errs',[e for e in errs if 'ERR_FAILED' not in e]); ctx.close()
