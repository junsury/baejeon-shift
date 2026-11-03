# 근태 겹침: 휴무 → 야간 변경 → 야간 휴가(대근) → 주간 변경. 등록 순서대로 쌓이고, 맨 위부터만 취소, 반대 변경은 원래 기록 지우기
import sys, os, json, tempfile
SHOT=sys.argv[1]; sys.argv=[sys.argv[0]]
src=open(os.path.join(os.path.dirname(__file__),'run.py')).read().replace('\nrun()','\n')
R={'__file__':os.path.join(os.path.dirname(os.path.abspath(__file__)),'run.py')}; exec(compile(src,'run.py','exec'),R)
D=json.loads(json.dumps(R['TEST_DATA']))
D['events']=[
 {"_id":"s1","type":"swap","aId":"B1","aDate":"2026-11-05","bId":"B2","bDate":"2026-11-11","shift":"야","createdAt":1792000000001},
 {"_id":"l1","type":"leave","memberId":"B2","date":"2026-11-05","shift":"야","leaveType":"연차","coverType":"cover","coverId":"A1","createdAt":1792000000002},
 {"_id":"s2","type":"swap","aId":"A2","aDate":"2026-11-05","bId":"B2","bDate":"2026-11-07","shift":"주","createdAt":1792000000003}]
D['requests']=[]
from playwright.sync_api import sync_playwright
ev=lambda pg:pg.evaluate("JSON.parse(sessionStorage.getItem('mockstore')).filter(([k])=>/events\\//.test(k)).map(([k,v])=>k.split('/').pop()).sort().join(',')")
with sync_playwright() as p:
    b=getattr(p,os.environ.get('BROWSER','chromium')).launch()
    ctx,pg,errs=R['page'](b,R['ADMIN'],900); pg.goto(R['BASE']); pg.wait_for_timeout(500)
    pg.click('[data-g=google]'); pg.wait_for_timeout(500)
    f=tempfile.NamedTemporaryFile('w',suffix='.json',delete=False); json.dump(D,f,ensure_ascii=False); f.close()
    pg.set_input_files('#gImport',f.name); pg.click('[data-g=setup]'); pg.wait_for_timeout(1500)
    pg.click('[data-g=pick][data-id=B2]'); pg.wait_for_timeout(800)
    print('events', ev(pg))
    # 팀 근무표 11/5 최구름 칸
    pg.click('[data-tab=team]'); pg.wait_for_timeout(300); pg.click('[data-tv=month]'); pg.wait_for_timeout(300)
    while pg.inner_text('#mLbl')!='2026.11': pg.click('#mNext'); pg.wait_for_timeout(200)
    pg.click('td[data-m=B2][data-n="%d"]'%(20762)); pg.wait_for_timeout(300)
    det=pg.inner_text('#cellDetail'); print('detail', '주간' in det, '근태 순서' in det, '야간 연차' in det)
    pg.screenshot(path=SHOT+'/stack_detail.png')
    # 내역: 맨 아래(s1) 취소 → 막힘
    pg.click('[data-tab=attend]'); pg.wait_for_timeout(200); pg.click('[data-att=hist]'); pg.wait_for_timeout(300)
    pg.click('#histUp [data-del=s1]'); pg.wait_for_timeout(300)
    print('blocked', pg.locator('#histUp .cxbox .msg.warn').count(), 'cascade btn', pg.locator('#histUp [data-cx=cascade]').count())
    pg.screenshot(path=SHOT+'/stack_block.png')
    pg.click('#histUp [data-cx=no]'); pg.wait_for_timeout(200)
    print('above s1', pg.evaluate("__stackT.above('s1').join(',')"), 'above l1', pg.evaluate("__stackT.above('l1').join(',')"))
    print('reverse of s2', pg.evaluate("(__stackT.reverseOf({type:'swap',aId:'B2',aDate:'2026-11-05',bId:'A2',bDate:'2026-11-07',shift:'주'})||{})._id"), pg.evaluate("(__stackT.reverseOf({type:'swap',aId:'B2',aDate:'2026-11-05',bId:'B1',bDate:'2026-11-11',shift:'야'})||{})._id||'none'"))
    # 맨 위(s2) 바로 취소
    pg.click('#histUp [data-del=s2]'); pg.wait_for_timeout(300); pg.click('#histUp [data-cx=now]'); pg.wait_for_timeout(800)
    print('after top cancel', ev(pg))
    # 관리자: s1 위에 것까지 한꺼번에
    pg.click('#histUp [data-del=s1]'); pg.wait_for_timeout(300); pg.click('#histUp [data-cx=cascade]'); pg.wait_for_timeout(1500)
    print('after cascade', ev(pg))
    print('errs', [e for e in errs if 'ERR_FAILED' not in e])
