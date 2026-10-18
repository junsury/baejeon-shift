# 편성표·근무요약 이미지/PDF 저장 버튼이 실제로 파일을 만드는지
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
    pg.click('[data-tab=team]'); pg.wait_for_timeout(200); pg.click('[data-tv=print]'); pg.wait_for_timeout(500)
    for bid,msg,tab in [('#pPng','#pMsg',None),('#pPdf','#pMsg',None),('#rpPng','#rpMsg','pay'),('#rpPdf','#rpMsg','pay')]:
        if tab: pg.click('[data-tab=%s]'%tab); pg.wait_for_timeout(300)
        try:
            with pg.expect_download(timeout=20000) as d: pg.click(bid)
            path=SHOT+'/dl_'+d.value.suggested_filename; d.value.save_as(path); print(bid,'OK',d.value.suggested_filename,os.path.getsize(path))
        except Exception as e: print(bid,'FAIL',pg.inner_text(msg)[:200],str(e)[:100])
    print('errs', errs)
