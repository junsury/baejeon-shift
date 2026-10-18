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
}
