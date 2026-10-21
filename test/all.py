# 모든 화면 테스트를 차례로 돌려요. 하나라도 실패하면 종료 코드 1 (배포 전에 자동 실행)
#   python3 test/all.py <그림 저장 폴더>      BROWSER=webkit 이면 사파리(아이폰) 엔진으로
import os, sys, subprocess, re
OUT = sys.argv[1] if len(sys.argv) > 1 else '/tmp'
HERE = os.path.dirname(os.path.abspath(__file__))
# 테스트별 통과 조건: 'errs []' 줄 개수 + 꼭 나와야 하는 글자
CASES = {
  'run.py': (4, []), 't_go.py': (4, []), 't_swap.py': (1, []), 't_swap2.py': (1, []), 't_history.py': (1, []),
  't_simple.py': (1, ['simple on: True', 'events 1 -> 2', '요청을 보냈어요']),
  't_chain.py': (1, ['OK']), 't_print.py': (1, []), 't_pending.py': (1, ['detail result: outShown True outRes 1 inRes 0 btn 보낸 요청 1', 'after cancel rq: []', 'result card: 1', 'after rseen: 0 [True]']),
  't_share_app.py': (1, ['done: {"status":"saved"}']),
  't_back.py': (1, ['back1 team month', 'back2 team day', 'acct closed True', 'flow back True', 'exit dialog 1', 'dialog gone True', 'exit again 1']),
  't_refresh.py': (1, ['appv=40 refresh calls 1 page alive True errs []', '?src=twa refresh calls 0 page alive True', ' refresh calls 0 page alive True']),
  't_notif.py': (1, ['nothing-left closed True', 'pending-left closed False']),
}
if os.environ.get('NET') == '1': CASES['t_png.py'] = (0, ['#pPng OK', '#pPdf OK', '#rpPng OK', '#rpPdf OK'])
bad = []
for f, (n, must) in CASES.items():
    try:
        r = subprocess.run([sys.executable, os.path.join(HERE, f), OUT], capture_output=True, text=True, timeout=420)
        out = r.stdout + r.stderr
    except subprocess.TimeoutExpired:
        out = 'TIMEOUT'
    ok = out.count('errs []') >= n and all(m in out for m in must) and 'Traceback' not in out
    print(('PASS ' if ok else 'FAIL ') + f, flush=True)
    if not ok:
        bad.append(f); print('\n'.join(out.strip().splitlines()[-12:]))
print('결과:', '모두 통과' if not bad else '실패 ' + ', '.join(bad))
sys.exit(1 if bad else 0)
