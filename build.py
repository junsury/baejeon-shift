"""근무표 아티팩트(shift-board.html) → 앱 페이지(app/index.html)로 변환.
아티팩트를 고친 뒤 이 스크립트를 다시 돌리면 앱에도 그대로 반영된다."""
import re, sys, pathlib

ROOT = pathlib.Path(__file__).parent
SRC = pathlib.Path(sys.argv[1] if len(sys.argv) > 1 else ROOT / 'src' / 'shift-board.html')
OUT = ROOT / 'app' / 'index.html'

s = SRC.read_text()
layer = (ROOT / 'src' / 'app-layer.js').read_text()
css = (ROOT / 'src' / 'app.css').read_text()

def rep(old, new, count=1):
    global s
    n = s.count(old)
    assert n == count, (n, old[:80])
    s = s.replace(old, new)

# 1) 문서 머리: 아티팩트 게시용 껍데기 제거, PWA 정보 추가
first_nl = s.index('\n')
assert s[:first_nl].startswith('<!doctype html>')
s = s[first_nl + 1:]
rep('<title>배전센터 근무표</title>\n<meta name="claude:viewport-fit-sides" content="cover">\n', '')
HEAD = '''<!doctype html>
<html lang="ko"><head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover">
<title>배전센터 근무표</title>
<meta name="theme-color" content="#1d5d8f">
<meta name="apple-mobile-web-app-capable" content="yes">
<meta name="mobile-web-app-capable" content="yes">
<meta name="apple-mobile-web-app-title" content="근무표">
<meta name="apple-mobile-web-app-status-bar-style" content="default">
<link rel="manifest" href="manifest.webmanifest">
<link rel="icon" href="icons/icon-192.png">
<link rel="apple-touch-icon" href="icons/apple-touch-icon.png">
<script src="config.js"></script>
<script>if('serviceWorker' in navigator)addEventListener('load',()=>navigator.serviceWorker.register('sw.js').catch(()=>{}))</script>
'''
s = HEAD + s
rep('</style>\n\n<header class="top">', css + '</style>\n</head><body>\n'
    '<div id="gate" hidden><div id="gateBody"></div></div>\n'
    '<div id="acct" hidden><div id="acctBody" role="dialog" aria-label="내 정보"></div></div>\n'
    '<header class="top">')

# 2) 머리줄: "나" 선택 + 가족 표시 + 내 정보 버튼
rep('''      <label class="me" for="meSel">나
        <select id="meSel"><option value="">선택</option></select>
      </label>''', '''      <div class="right">
        <span id="famLab" hidden></span>
        <label class="me" for="meSel" id="meLab">나
          <select id="meSel"><option value="">선택</option></select>
        </label>
        <button class="acctbtn" id="acctBtn" aria-label="내 정보">👤</button>
      </div>''')

# 3) 기본 상태: 로그인 전엔 권한 없음
rep('let db=null, canWrite=true, live=false, isAdmin=true, viewerPreview=false;',
    'let db=null, canWrite=false, live=false, isAdmin=false, viewerPreview=false;')

# 4) 렌더링: 가족은 내 근무만, 역할별 화면 정리
rep("  const admin=isAdmin&&!viewerPreview;\n  $('#tabAdmin').hidden=!admin;",
    "  if(isFamily())ui.tab='my';\n  syncMemos();\n  const admin=isAdmin&&!viewerPreview;\n  $('#tabAdmin').hidden=!admin;")
rep("  else if(!live){b.hidden=false;b.textContent='미리보기 모드예요. 여기서 등록한 내용은 저장되지 않고 팀원에게도 보이지 않아요.'}\n  else b.hidden=true;\n}",
    "  else b.hidden=true;\n  applyRoleUI();renderAcct();\n}")

# 5) 메모: 가족은 읽기 전용
m = re.search(r"    h\+='<div class=\"card form\" style=\"gap:8px\"><div class=\"period\"><h3>내 메모</h3>.*?\n", s)
assert m
memo_line = m.group(0)
s = s.replace(memo_line,
    "    if(isFamily()){if(mm)h+='<div class=\"card form\" style=\"gap:6px\"><h3>메모</h3><p style=\"margin:0;white-space:pre-wrap\">'+esc(mm)+'</p></div>'}\n"
    "    else if(me&&me===ownMid())" + memo_line.lstrip().replace('팀원은 볼 수 없어요 · 앱에서는 가족과 함께 봐요', '팀원은 볼 수 없고, 가족과 함께 봐요'))

# 6) 계정 연결·메모 저장은 앱 연결층으로 교체
a = s.index('/* 나 = 계정 연결 */\nasync function linkMe(id){')
b = s.index('/* ---------- 저장 ---------- */')
s = s[:a] + '/* 계정 연결 · 메모 저장: 앱 연결층에 있음 */\n' + s[b:]
m = re.search(r"\$\('#linkCard'\)\.addEventListener\('click',async e=>\{const b=e\.target\.closest\('\[data-unlink\]'\).*?\n", s)
assert m
s = s.replace(m.group(0), '')

# 7) 오류 문구
rep("b.textContent=e&&e.code==='invalid_argument'?'저장할 권한이 없어요. 신청·승인·메모를 쓰려면 이 페이지 권한이 \"참여(Contributor)\" 이상이어야 해요. 관리자에게 요청하세요.':'저장하지 못했어요. 잠시 후 다시 시도해 주세요.';",
    "b.textContent=e&&/permission/.test(e.code||'')?'저장할 권한이 없어요. 관리자에게 문의하세요.':'저장하지 못했어요. 인터넷 연결을 확인하고 다시 시도해 주세요.';")
rep("  if(e&&e.code==='invalid_argument'){canWrite=false;render();return}",
    "  if(e&&/permission/.test(e.code||'')){const b=$('#banner');b.hidden=false;b.textContent='저장할 권한이 없어요. 관리자 권한이 바뀌었는지 확인하세요.';return}")

# 8) 파일 저장: 휴대폰 공유창(카톡) / PC 다운로드
rep("window.claude&&window.claude.use?await window.claude.use('downloads'):null", "await appDownloads()", 3)

# 9) 저장소 연결: 아티팩트 → Firebase
a = s.index('render();\n\n/* ---------- 공유 저장소 연결 ---------- */')
b = s.rindex('})();\n</script>')
s = s[:a] + layer + '\nrender();\n' + s[b:]

OUT.parent.mkdir(exist_ok=True)
OUT.write_text(s)
print('wrote', OUT, len(s))
