package kr.baejeon.shift;

import android.Manifest;
import android.content.ContentUris;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CalendarContract;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/** 휴대폰 캘린더(구글·삼성 캘린더 등 휴대폰에 동기화된 일정)를 읽어요. */
final class CalendarReader {
    private CalendarReader() {}

    static final class Ev {
        final String title; final boolean allDay; final long begin; final String time;
        Ev(String title, boolean allDay, long begin, String time) { this.title = title; this.allDay = allDay; this.begin = begin; this.time = time; }
        String text() { return time.isEmpty() ? title : title + " " + time; }
    }

    static boolean allowed(Context c) {
        return c.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED;
    }

    /** from~to(날짜 번호, 포함) 사이 일정. 키 = 날짜 번호 */
    static Map<Integer, List<Ev>> read(Context c, int from, int to) {
        Map<Integer, List<Ev>> out = new HashMap<>();
        if (!allowed(c)) return out;
        TimeZone kst = ShiftData.KST;
        long start = from * 86400000L - kst.getOffset(from * 86400000L);
        long end = (to + 1) * 86400000L - kst.getOffset(to * 86400000L);
        Uri.Builder b = CalendarContract.Instances.CONTENT_URI.buildUpon();
        ContentUris.appendId(b, start - 86400000L);
        ContentUris.appendId(b, end + 86400000L);
        String[] cols = {CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN, CalendarContract.Instances.END,
                CalendarContract.Instances.ALL_DAY, CalendarContract.Instances.VISIBLE};
        try (Cursor cur = c.getContentResolver().query(b.build(), cols, CalendarContract.Instances.VISIBLE + "=1", null,
                CalendarContract.Instances.BEGIN + " ASC")) {
            if (cur == null) return out;
            while (cur.moveToNext()) {
                String title = cur.getString(0);
                if (title == null || title.trim().isEmpty()) title = "(제목 없음)";
                long bg = cur.getLong(1), en = cur.getLong(2);
                boolean all = cur.getInt(3) == 1;
                int d0, d1; String time = "";
                if (all) {   // 종일 일정은 UTC 자정 기준으로 저장됨
                    d0 = (int) Math.floorDiv(bg, 86400000L);
                    d1 = (int) Math.floorDiv(Math.max(bg, en - 1), 86400000L);
                } else {
                    d0 = (int) Math.floorDiv(bg + kst.getOffset(bg), 86400000L);
                    d1 = (int) Math.floorDiv(Math.max(bg, en - 1) + kst.getOffset(en), 86400000L);
                    long lm = bg + kst.getOffset(bg);
                    int mins = (int) Math.floorMod(lm / 60000L, 1440L);
                    time = String.format(Locale.US, "%02d:%02d", mins / 60, mins % 60);
                }
                for (int n = Math.max(d0, from); n <= Math.min(d1, to); n++) {
                    List<Ev> l = out.get(n);
                    if (l == null) { l = new ArrayList<>(); out.put(n, l); }
                    l.add(new Ev(title.trim(), all || n != d0, bg, n == d0 ? time : ""));
                }
            }
        } catch (Exception ignored) {}
        for (List<Ev> l : out.values()) Collections.sort(l, (x, y) -> x.allDay != y.allDay ? (x.allDay ? -1 : 1) : Long.compare(x.begin, y.begin));
        return out;
    }
}
