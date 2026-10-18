# 연쇄 근무변경: 한별님이 10/18 주간을 최구름에게 넘기고, 같은 날 정이슬 주간을 받아온 경우 둘 다 편성표에 보여야 함
import sys, os, json, tempfile
SHOT=sys.argv[1]; sys.argv=[sys.argv[0]]
src=open(os.path.join(os.path.dirname(__file__),'run.py')).read().replace('\nrun()','\n')
R={'__file__':os.path.join(os.path.dirname(os.path.abspath(__file__)),'run.py')}; exec(compile(src,'run.py','exec'),R)
D=json.loads(json.dumps(R['TEST_DATA']))
D['events']=[{"_id":"s1","type":"swap","aId":"B2","aDate":"2026-10-16","bId":"C2","bDate":"2026-10-18","shift":"주","createdAt":10},
             {"_id":"s2","type":"swap","aId":"C2","aDate":"2026-10-22","bId":"C1","bDate":"2026-10-18","shift":"주","createdAt":20}]
from playwright.sync_api import sync_playwright
with sync_playwright() as p:
    b=p.chromium.launch()
    ctx,pg,errs=R['page'](b,R['ADMIN']); pg.goto(R['BASE']); pg.wait_for_timeout(500)
    pg.click('[data-g=google]'); pg.wait_for_timeout(500)
    f=tempfile.NamedTemporaryFile('w',suffix='.json',delete=False); json.dump(D,f,ensure_ascii=False); f.close()
    pg.set_input_files('#gImport',f.name); pg.click('[data-g=setup]'); pg.wait_for_timeout(1500)
    pg.click('[data-g=pick][data-id=B2]'); pg.wait_for_timeout(800)
    pg.click('#toSimple'); pg.wait_for_timeout(300); pg.click('[data-stab=sheet]'); pg.wait_for_timeout(400)
    row=pg.inner_text('.sm-sh-row.today .d'); print('10/18 주간:', row.replace('\n',' '))
    print('OK' if '변(10.16) 최구름' in row and '변(10.22) 한별님' in row else 'FAIL')
    print('errs', [e for e in errs if 'ERR_FAILED' not in e])
