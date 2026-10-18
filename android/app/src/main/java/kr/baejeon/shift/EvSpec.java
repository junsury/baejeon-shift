package kr.baejeon.shift;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** 일정 추가 화면에서 고른 내용 → 실제로 넣을 일정들 */
final class EvSpec {
    static final int NONE = 0, WEEKLY = 1, MONTHLY = 2, YEARLY = 3, LUNAR = 4;
    static final String[] REPEAT_NAMES = {"🔁 반복 없음", "🔁 매주", "🔁 매달", "🔁 매년", "🔁 매년 (음력)"};
    /** 몇 주년·나이 꼬리표 종류 */
    static final int LABEL_NONE = 0, LABEL_YEARS = 1, LABEL_AGE = 2;
    static final int YEARS_AHEAD = 30;

    String title = ""; String memo = "";
    int day0, day1;                 // 시작·끝 날(포함)
    boolean allDay = true; int startMin = 9 * 60, endMin = 10 * 60;
    int repeat = NONE;
    int lunarM, lunarD; boolean lunarLeap;
    int label = LABEL_NONE, baseYear = 0;
    int remind = -2;                // -2 기본 알림, -1 없음, 그 외 몇 분 전

    static final class Occ {
        final String title; final int day0, day1;
        Occ(String t, int a, int b) { title = t; day0 = a; day1 = b; }
    }

    /** 반복 규칙 하나로 넣을 수 있으면 RRULE, 아니면 null (펼쳐서 넣어요) */
    String rrule() {
        if (repeat == WEEKLY) return "RRULE:FREQ=WEEKLY";
        if (repeat == MONTHLY) return "RRULE:FREQ=MONTHLY";
        if (repeat == YEARLY && label == LABEL_NONE) return "RRULE:FREQ=YEARLY";
        return null;
    }

    boolean expands() { return repeat == LUNAR || (repeat == YEARLY && label != LABEL_NONE); }

    String titleFor(int year) {
        if (label == LABEL_NONE || baseYear <= 0) return title;
        int n = year - baseYear;
        if (n <= 0) return title;
        return label == LABEL_YEARS ? title + " " + n + "주년" : title + " (" + n + "세)";
    }

    /** 펼친 일정들 (반복 없음·RRULE이면 1개) */
    List<Occ> occurrences() {
        List<Occ> out = new ArrayList<>();
        int span = Math.max(0, day1 - day0);
        if (repeat == LUNAR) {
            int[] lt = Lunar.fromSolar(day0);
            int y0 = lt != null ? lt[0] : 2026;
            for (int y = y0; y <= Lunar.LAST_YEAR; y++) {
                int s = Lunar.yearly(y, lunarM, lunarD, lunarLeap);
                if (s == Lunar.MIN || s < day0) continue;
                out.add(new Occ(titleFor(solarYear(s)), s, s + span));
            }
        } else if (repeat == YEARLY && label != LABEL_NONE) {
            java.util.Calendar c = ShiftData.cal(day0);
            int y0 = c.get(java.util.Calendar.YEAR), m = c.get(java.util.Calendar.MONTH), d = c.get(java.util.Calendar.DAY_OF_MONTH);
            for (int y = y0; y < y0 + YEARS_AHEAD; y++) {
                java.time.LocalDate ld;
                try { ld = java.time.LocalDate.of(y, m + 1, d); } catch (Exception e) { ld = java.time.LocalDate.of(y, m + 1, 28); }  // 2/29 → 2/28
                int s = (int) ld.toEpochDay();
                out.add(new Occ(titleFor(y), s, s + span));
            }
        } else out.add(new Occ(title, day0, day1));
        return out;
    }

    static int solarYear(int n) { return ShiftData.cal(n).get(java.util.Calendar.YEAR); }

    /** 이미 붙은 몇 주년·나이 꼬리표 떼고 제목만 */
    static final Pattern SUFFIX = Pattern.compile("( \\d+주년| \\(\\d+세\\))$");
    static String baseTitle(String t) { return SUFFIX.matcher(t).replaceFirst(""); }
    static String suffixOf(String t) { java.util.regex.Matcher m = SUFFIX.matcher(t); return m.find() ? m.group(1) : ""; }

    static String newSeriesId() {
        String a = "abcdefghjkmnpqrstuvwxyz23456789"; StringBuilder b = new StringBuilder();
        java.security.SecureRandom r = new java.security.SecureRandom();
        for (int i = 0; i < 12; i++) b.append(a.charAt(r.nextInt(a.length())));
        return b.toString();
    }

    /** 휴대폰 캘린더 일정은 설명에 반복 묶음 표시를 숨겨 둬요 */
    static final String TAG = "#근무표반복:";
    static String seriesOf(String desc) {
        if (desc == null) return null; int i = desc.indexOf(TAG); if (i < 0) return null;
        String s = desc.substring(i + TAG.length()).trim(); int sp = s.indexOf('\n'); return sp >= 0 ? s.substring(0, sp).trim() : s;
    }
    static String stripTag(String desc) {
        if (desc == null) return ""; int i = desc.indexOf(TAG); return (i < 0 ? desc : desc.substring(0, i)).trim();
    }

    /** 알림 고르기 */
    static final int[] ALLDAY_REMIND = {-2, -1, 900, 2340, 9540, 0};
    static final String[] ALLDAY_REMIND_NAMES = {"🔔 알림: 캘린더 기본", "🔕 알림 없음", "🔔 전날 오전 9시", "🔔 2일 전 오전 9시", "🔔 1주 전 오전 9시", "🔔 당일 0시"};
    static final int[] TIME_REMIND = {-2, -1, 10, 30, 60, 1440};
    static final String[] TIME_REMIND_NAMES = {"🔔 알림: 캘린더 기본", "🔕 알림 없음", "🔔 10분 전", "🔔 30분 전", "🔔 1시간 전", "🔔 하루 전"};

    static String hm(int m) { m = Math.floorMod(m, 1440); return String.format(Locale.US, "%02d:%02d", m / 60, m % 60); }
}
