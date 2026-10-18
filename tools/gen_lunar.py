# 한국천문연구원 기준 음력 표(korean-lunar-calendar) → 갤럭시 앱용 LunarData.java
# 음력 연도마다: 음력 1월 1일의 날짜번호(1970-01-01=0), 윤달(없으면 0), 각 달 일수(윤달 포함 순서대로)
import datetime, json, sys
from korean_lunar_calendar import KoreanLunarCalendar as K
Y0, Y1 = 2020, 2050
rows = []; vec = []
def solar(y, m, d, leap):
    c = K()
    if not c.setLunarDate(y, m, d, leap): return None
    s = c.SolarIsoFormat()
    return (datetime.date.fromisoformat(s) - datetime.date(1970, 1, 1)).days
for y in range(Y0, Y1 + 1):
    first = solar(y, 1, 1, False)
    leap = 0; months = []
    for m in range(1, 13):
        for lp in (False, True):
            a = solar(y, m, 1, lp)
            if a is None: continue
            # 다음 달 1일까지 일수
            if lp: leap = m
            if solar(y, m, 30, lp) is not None: n = 30
            else: n = 29
            months.append(n)
    rows.append((y, first, leap, months))
for (y, m, d, lp) in [(2026, 9, 8, False), (2027, 1, 1, False), (2033, 11, 1, True), (2045, 8, 15, False), (2050, 12, 29, False), (2028, 5, 30, False)]:
    s = solar(y, m, d, lp)
    if s is not None: vec.append((y, m, d, 1 if lp else 0, s))
out = ['package kr.baejeon.shift;', '', '/** 자동 생성 (tools/gen_lunar.py) — 한국천문연구원 음력 기준 %d~%d년 */' % (Y0, Y1),
       'final class LunarData {', '    private LunarData() {}', '    static final int Y0 = %d, Y1 = %d;' % (Y0, Y1),
       '    /** {음력 1월 1일 날짜번호, 윤달(0=없음), 달별 일수...} */', '    static final int[][] T = {']
for y, first, leap, months in rows:
    out.append('        {%d, %d, %s}, // %d' % (first, leap, ', '.join(map(str, months)), y))
out += ['    };', '    /** 검사용 {음력 연, 월, 일, 윤달, 양력 날짜번호} */', '    static final int[][] CHECK = {']
for v in vec: out.append('        {%s},' % ', '.join(map(str, v)))
out += ['    };', '}', '']
open(sys.argv[1], 'w').write('\n'.join(out))
print('years', len(rows), 'checks', len(vec))
