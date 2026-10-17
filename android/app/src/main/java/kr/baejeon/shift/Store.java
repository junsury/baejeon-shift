package kr.baejeon.shift;

import android.content.Context;
import android.content.SharedPreferences;

/** 휴대폰 안에 저장하는 값들 (연결 정보, 받아 둔 근무, 위젯별 설정). */
final class Store {
    private Store() {}

    static SharedPreferences p(Context c) {
        return c.getApplicationContext().getSharedPreferences("bj", Context.MODE_PRIVATE);
    }

    static String token(Context c) { return p(c).getString("token", ""); }
    static void setToken(Context c, String t) { p(c).edit().putString("token", t).putString("data", "").apply(); }
    static String data(Context c) { return p(c).getString("data", ""); }
    static void setData(Context c, String json) { p(c).edit().putString("data", json).putLong("fetched", System.currentTimeMillis()).apply(); }
    static long fetched(Context c) { return p(c).getLong("fetched", 0); }
    static boolean unlinked(Context c) { return p(c).getBoolean("unlinked", false); }
    static void setUnlinked(Context c, boolean v) { p(c).edit().putBoolean("unlinked", v).apply(); }

    /** 위젯 하나의 설정 */
    static final class Cfg {
        int opacity = 100;     // 0~100
        int corner = 26;       // 0~32
        int text = 1;          // 0 작게 1 보통 2 크게
        int chip = 1;
        int theme = 2;         // 0 밝게 1 어둡게 2 휴대폰 설정 따라
        boolean events = true;
        int weekMode = 0;      // 0 이번 주(월~일) 1 오늘부터 7일
        int open = 0;          // 누르면 열 화면: OPEN_KEYS 순서
        int monthOffset = 0;
        long offsetAt = 0;
    }

    static final String[] OPEN_KEYS = {"my-day", "my-week", "my-month", "team-day", "team-month"};
    static final String[] OPEN_NAMES = {"내 근무 · 일", "내 근무 · 주", "내 근무 · 월", "팀 근무표 · 하루", "팀 근무표 · 한달"};
    static int defOpen(String kind) { return "month".equals(kind) ? 2 : "week".equals(kind) ? 1 : 0; }

    static Cfg cfg(Context c, int id, String kind) {
        SharedPreferences s = p(c);
        String k = "w" + id + ".";
        Cfg f = new Cfg();
        f.corner = "week".equals(kind) ? 22 : 26;
        f.opacity = s.getInt(k + "op", f.opacity);
        f.corner = s.getInt(k + "co", f.corner);
        f.text = s.getInt(k + "tx", f.text);
        f.chip = s.getInt(k + "ch", f.chip);
        f.theme = s.getInt(k + "th", f.theme);
        f.events = s.getBoolean(k + "ev", f.events);
        f.weekMode = s.getInt(k + "wm", f.weekMode);
        f.open = s.getInt(k + "oc", defOpen(kind));
        f.monthOffset = s.getInt(k + "mo", 0);
        f.offsetAt = s.getLong(k + "ma", 0);
        return f;
    }

    static void saveCfg(Context c, int id, Cfg f) {
        String k = "w" + id + ".";
        p(c).edit().putInt(k + "op", f.opacity).putInt(k + "co", f.corner).putInt(k + "tx", f.text)
                .putInt(k + "ch", f.chip).putInt(k + "th", f.theme).putBoolean(k + "ev", f.events)
                .putInt(k + "wm", f.weekMode).putInt(k + "oc", f.open).putInt(k + "mo", f.monthOffset).putLong(k + "ma", f.offsetAt).apply();
    }

    static void setOffset(Context c, int id, int off) {
        String k = "w" + id + ".";
        p(c).edit().putInt(k + "mo", off).putLong(k + "ma", System.currentTimeMillis()).apply();
    }

    static void remove(Context c, int id) {
        String k = "w" + id + ".";
        SharedPreferences.Editor e = p(c).edit();
        for (String x : new String[]{"op", "co", "tx", "ch", "th", "ev", "wm", "oc", "mo", "ma"}) e.remove(k + x);
        e.apply();
    }
}
