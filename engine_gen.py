"""shift-board.html의 근무 계산 코드(dayCells 등)를 알림 서버용 functions/engine.js로 복사.
화면과 서버가 같은 규칙으로 계산하도록 build.py가 매번 다시 만든다."""
import re, pathlib
ROOT = pathlib.Path(__file__).parent

def generate(src: str):
    def line(prefix):
        m = re.search(r'^' + re.escape(prefix) + r'.*$', src, re.M)
        assert m, prefix
        return m.group(0)
    def block(start):
        i = src.index(start)
        j = src.index('\n}\n', i) + 2
        return src[i:j]
    parts = [
        line('const PATTERN='), line('const REF='), line('const REF_POS='), line('const HOURS='),
        line('const DEFAULT_HOLIDAYS='), line('const WD='),
        line('const dn='), line('const ds='), line('const dow='), line('const mod='),
        line('function teamAt('), line('const present='), line('const isWorkday='),
        block('function dayCells('), line('function touches('), line('const tmin='), line('function hasShift('),
    ]
    body = '\n'.join(parts)
    out = ('// 자동 생성 파일 — 고치지 마세요. build.py가 src/shift-board.html의 근무 계산 코드를 복사해 만들어요.\n'
           '/* eslint-disable */\n'
           'module.exports = function makeEngine({ members, assignments, events, holidayDates }) {\n'
           '  let cache = new Map();\n'
           + body.replace('\n', '\n  ') + '\n'
           '  let holidays = new Set(holidayDates && holidayDates.length ? holidayDates : DEFAULT_HOLIDAYS);\n'
           '  assignments = (assignments || []).filter(x => x && x.date && x.teams).sort((x, y) => x.date < y.date ? -1 : 1);\n'
           '  return { dayCells, dn, ds, dow, teamAt, hasShift, HOURS, WD, PATTERN };\n'
           '};\n')
    (ROOT / 'functions' / 'engine.js').write_text('  ' + out if False else out)

if __name__ == '__main__':
    generate((ROOT / 'src' / 'shift-board.html').read_text())
    print('engine.js written')
