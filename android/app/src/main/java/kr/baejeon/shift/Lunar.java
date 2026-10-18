package kr.baejeon.shift;

/** 음력 ↔ 양력 (LunarData 표 사용, 2020~2049년). 날짜번호 = 1970-01-01부터 날 수 */
final class Lunar {
    private Lunar() {}
    static final int MIN = Integer.MIN_VALUE;
    static final int LAST_YEAR = LunarData.Y1 - 1;   // 마지막 해는 표가 다 차 있지 않아서 제외

    /** 음력 y년 m월 d일(윤달이면 leap) → 양력 날짜번호. 그 해에 없는 날이면 MIN */
    static int toSolar(int y, int m, int d, boolean leap) {
        if (y < LunarData.Y0 || y > LAST_YEAR || m < 1 || m > 12 || d < 1 || d > 30) return MIN;
        int[] r = LunarData.T[y - LunarData.Y0];
        int day = r[0], idx = 0, lm = r[1];
        for (int mm = 1; mm <= 12; mm++) {
            for (int lp = 0; lp < 2; lp++) {
                if (lp == 1 && lm != mm) continue;
                if (2 + idx >= r.length) return MIN;
                int len = r[2 + idx];
                if (mm == m && (lp == 1) == leap) return d <= len ? day + d - 1 : MIN;
                day += len; idx++;
            }
        }
        return MIN;
    }

    /** 생일·제사처럼 해마다: 윤달이 없는 해는 평달, 30일이 없는 달은 29일(그믐)로 */
    static int yearly(int y, int m, int d, boolean leap) {
        int s = toSolar(y, m, d, leap);
        if (s == MIN && leap) s = toSolar(y, m, d, false);
        if (s == MIN && d == 30) s = yearly(y, m, 29, leap);
        return s;
    }

    /** 양력 날짜번호 → {음력 연, 월, 일, 윤달(1/0)}, 범위 밖이면 null */
    static int[] fromSolar(int n) {
        for (int y = LAST_YEAR; y >= LunarData.Y0; y--) {
            int[] r = LunarData.T[y - LunarData.Y0];
            if (r[0] > n) continue;
            int day = r[0], idx = 0, lm = r[1];
            for (int mm = 1; mm <= 12; mm++) {
                for (int lp = 0; lp < 2; lp++) {
                    if (lp == 1 && lm != mm) continue;
                    if (2 + idx >= r.length) return null;
                    int len = r[2 + idx];
                    if (n < day + len) return new int[]{y, mm, n - day + 1, lp};
                    day += len; idx++;
                }
            }
            return null;
        }
        return null;
    }

    static String text(int m, int d, boolean leap) { return "음력 " + (leap ? "윤" : "") + m + "월 " + d + "일"; }
}
