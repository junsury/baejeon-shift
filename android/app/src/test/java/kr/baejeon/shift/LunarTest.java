package kr.baejeon.shift;

import static org.junit.Assert.*;

import org.junit.Test;

import java.util.List;

/** 음력 변환·반복 일정 펼치기 점검 */
public class LunarTest {
    static int d(String ymd) { return (int) java.time.LocalDate.parse(ymd).toEpochDay(); }

    @Test public void matchesKasiTable() {
        for (int[] c : LunarData.CHECK) {
            if (c[0] > Lunar.LAST_YEAR) continue;
            assertEquals("음력 " + c[0] + "-" + c[1] + "-" + c[2], c[4], Lunar.toSolar(c[0], c[1], c[2], c[3] == 1));
            int[] back = Lunar.fromSolar(c[4]);
            assertNotNull(back);
            assertArrayEquals(new int[]{c[0], c[1], c[2], c[3]}, back);
        }
    }

    @Test public void knownHolidays() {
        assertEquals(d("2026-02-17"), Lunar.toSolar(2026, 1, 1, false));   // 2026 설날
        assertEquals(d("2026-09-25"), Lunar.toSolar(2026, 8, 15, false));  // 2026 추석
        assertEquals(d("2027-09-15"), Lunar.toSolar(2027, 8, 15, false));  // 2027 추석
    }

    @Test public void roundTripEveryDay() {
        for (int n = d("2020-02-01"); n < d("2049-12-01"); n++) {
            int[] l = Lunar.fromSolar(n);
            assertNotNull("날짜 " + n, l);
            assertEquals(n, Lunar.toSolar(l[0], l[1], l[2], l[3] == 1));
        }
    }

    @Test public void lunarYearlyExpands() {
        EvSpec sp = new EvSpec();
        sp.title = "아버지 생신"; sp.repeat = EvSpec.LUNAR; sp.lunarM = 9; sp.lunarD = 8; sp.day0 = sp.day1 = d("2026-10-18");
        sp.label = EvSpec.LABEL_AGE; sp.baseYear = 1958;
        List<EvSpec.Occ> o = sp.occurrences();
        assertTrue(o.size() >= 20);
        assertEquals(d("2026-10-18"), o.get(0).day0);
        assertEquals("아버지 생신 (68세)", o.get(0).title);
        for (EvSpec.Occ x : o) assertEquals(Lunar.fromSolar(x.day0)[1], 9);
    }

    @Test public void yearlyAnniversaryLabels() {
        EvSpec sp = new EvSpec();
        sp.title = "결혼"; sp.repeat = EvSpec.YEARLY; sp.label = EvSpec.LABEL_YEARS; sp.baseYear = 2015; sp.day0 = sp.day1 = d("2026-11-07");
        List<EvSpec.Occ> o = sp.occurrences();
        assertEquals(EvSpec.YEARS_AHEAD, o.size());
        assertEquals("결혼 11주년", o.get(0).title);
        assertEquals(d("2027-11-07"), o.get(1).day0);
        assertEquals("결혼", EvSpec.baseTitle("결혼 12주년"));
        assertEquals(" 12주년", EvSpec.suffixOf("결혼 12주년"));
        assertNull(sp.rrule());
        sp.label = EvSpec.LABEL_NONE;
        assertEquals("RRULE:FREQ=YEARLY", sp.rrule());
        assertEquals(1, sp.occurrences().size());
    }

    @Test public void seriesTag() {
        String desc = "메모\n\n" + EvSpec.TAG + "abc123";
        assertEquals("abc123", EvSpec.seriesOf(desc));
        assertEquals("메모", EvSpec.stripTag(desc));
    }

    @Test public void lunarFlagYearly() {
        EvSpec sp = new EvSpec();
        sp.title = "어머니 생신"; sp.repeat = EvSpec.YEARLY; sp.lunar = true; sp.lunarM = 9; sp.lunarD = 8; sp.day0 = sp.day1 = d("2026-10-18");
        assertNull(sp.rrule());
        assertTrue(sp.expands());
        List<EvSpec.Occ> o = sp.occurrences();
        assertTrue(o.size() >= 20);
        assertEquals(d("2027-10-07"), o.get(1).day0);   // 2027 음력 9/8
        sp.repeat = EvSpec.NONE;
        assertEquals(1, sp.occurrences().size());
    }

    @Test public void remindMinutes() {
        assertEquals(900, EvSpec.remindMinutes(1, 9 * 60, true, 0));      // 종일 · 전날 9시
        assertEquals(-9, EvSpec.remindMinutes(0, 9 * 60, true, 0));       // 종일 · 당일 9시는 못 해요
        assertEquals(60, EvSpec.remindMinutes(0, 14 * 60, false, 15 * 60)); // 15시 일정 · 당일 14시
        assertEquals(7 * 1440 - 540, EvSpec.remindMinutes(7, 9 * 60, true, 0));
        assertEquals(-1, EvSpec.remindMinutes(-1, 0, true, 0));
    }

    @Test public void customRepeat() {
        EvSpec sp = new EvSpec();
        sp.title = "학원"; sp.day0 = sp.day1 = d("2026-11-03"); sp.allDay = false;   // 화요일
        sp.repeat = EvSpec.WEEKLY; sp.interval = 2; sp.weekDays = (1 << 2) | (1 << 4); sp.untilDay = d("2027-06-30");
        assertEquals("RRULE:FREQ=WEEKLY;INTERVAL=2;BYDAY=TU,TH;UNTIL=20270630T145959Z", sp.rrule());
        assertEquals("2주마다 화·목, 2027.06.30까지", sp.repeatText());
        sp.repeat = EvSpec.MONTHLY; sp.interval = 1; sp.weekDays = 0; sp.untilDay = Integer.MIN_VALUE; sp.count = 8; sp.allDay = true;
        assertEquals("RRULE:FREQ=MONTHLY;COUNT=8", sp.rrule());
        sp.repeat = EvSpec.YEARLY; sp.count = 0; sp.untilDay = d("2031-12-31");
        assertEquals("RRULE:FREQ=YEARLY;UNTIL=20311231", sp.rrule());
        // 시작 요일이 안 골라져 있으면 가장 가까운 고른 요일로
        EvSpec w = new EvSpec(); w.repeat = EvSpec.WEEKLY; w.weekDays = 1 << 4; w.day0 = w.day1 = d("2026-11-03"); w.alignStart();
        assertEquals(d("2026-11-05"), w.day0);
        // 몇 주년 + 끝 연도
        EvSpec y = new EvSpec(); y.title = "결혼"; y.repeat = EvSpec.YEARLY; y.label = EvSpec.LABEL_YEARS; y.baseYear = 2015;
        y.day0 = y.day1 = d("2027-11-07"); y.untilDay = d("2031-12-31");
        assertEquals(5, y.occurrences().size());
        y.untilDay = Integer.MIN_VALUE; y.count = 3; y.interval = 2;
        List<EvSpec.Occ> o = y.occurrences();
        assertEquals(3, o.size()); assertEquals("결혼 14주년", o.get(1).title);
    }
}
