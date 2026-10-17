package kr.baejeon.shift;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.os.Bundle;
import android.provider.CalendarContract;

import java.util.HashMap;
import java.util.Map;

/**
 * 내 근무를 가족 공유 캘린더(구글 "근무표")에 종일 일정으로 넣고, 바뀌면 고쳐요.
 * 앱이 넣은 일정은 설명에 TAG가 붙어 있어서 그것만 건드려요 (가족이 넣은 일정은 그대로).
 */
final class ShiftSync {
    private ShiftSync() {}

    static final String TAG = "#근무표자동";
    static final int AHEAD = 62;

    /** 공유 캘린더의 휴대폰 안 번호 (아직 동기화 전이면 -1) */
    static long localId(Context c) {
        String gid = Store.p(c).getString("famCalId", "");
        if (gid.isEmpty() || !CalendarReader.allowed(c)) return -1;
        String[] cols = {CalendarContract.Calendars._ID, CalendarContract.Calendars.VISIBLE, CalendarContract.Calendars.SYNC_EVENTS};
        try (Cursor cur = c.getContentResolver().query(CalendarContract.Calendars.CONTENT_URI, cols,
                CalendarContract.Calendars._SYNC_ID + "=? OR " + CalendarContract.Calendars.OWNER_ACCOUNT + "=?", new String[]{gid, gid}, null)) {
            if (cur != null && cur.moveToFirst()) {
                long id = cur.getLong(0);
                if (cur.getInt(1) != 1 || cur.getInt(2) != 1) {   // 숨김·동기화 꺼짐이면 켜기
                    try {
                        ContentValues v = new ContentValues();
                        v.put(CalendarContract.Calendars.VISIBLE, 1);
                        v.put(CalendarContract.Calendars.SYNC_EVENTS, 1);
                        c.getContentResolver().update(ContentUris.withAppendedId(CalendarContract.Calendars.CONTENT_URI, id), v, null, null);
                    } catch (Exception ignored) {}
                }
                return id;
            }
        } catch (Exception ignored) {}
        return -1;
    }

    /** 구글 계정 캘린더를 지금 동기화해 달라고 요청 */
    static void requestSync(Context c) {
        String acc = Store.p(c).getString("famAccount", "");
        try {
            Bundle b = new Bundle();
            b.putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true);
            b.putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true);
            if (!acc.isEmpty()) ContentResolver.requestSync(new android.accounts.Account(acc, "com.google"), CalendarContract.AUTHORITY, b);
        } catch (Exception ignored) {}
    }

    static String titleOf(String code, boolean changed) {
        String t;
        switch (code) {
            case "주": t = "주간 근무"; break;
            case "야": t = "야간 근무"; break;
            case "24": t = "24시간 근무"; break;
            case "지원": t = "근무지원"; break;
            case "연": t = "휴가"; break;
            default: return null;
        }
        return changed ? t + " (변경)" : t;
    }

    /** 근무 → 공유 캘린더. 바뀐 것만 고쳐요. */
    static synchronized void sync(Context c) {
        if (!Store.p(c).getBoolean("famShifts", true)) return;
        if (!CalendarReader.canWrite(c)) return;
        ShiftData d = ShiftData.parse(Store.data(c));
        if (d == null) return;
        long cal = localId(c);
        if (cal < 0) { requestSync(c); return; }
        int from = ShiftData.todayNum() - 1, to = from + 1 + AHEAD;

        // 원하는 상태
        Map<Integer, String> want = new HashMap<>();
        for (int n = from; n <= to; n++) {
            String code = d.code(n);
            if (code == null) continue;
            String t = titleOf(code, d.changed(n));
            if (t != null) want.put(n, t);
        }
        // 지금 들어 있는 앱 일정
        ContentResolver cr = c.getContentResolver();
        Map<Integer, String> have = new HashMap<>();
        Map<Integer, Long> ids = new HashMap<>();
        String[] cols = {CalendarContract.Events._ID, CalendarContract.Events.DTSTART, CalendarContract.Events.TITLE};
        try (Cursor cur = cr.query(CalendarContract.Events.CONTENT_URI, cols,
                CalendarContract.Events.CALENDAR_ID + "=? AND " + CalendarContract.Events.DELETED + "=0 AND "
                        + CalendarContract.Events.DESCRIPTION + " LIKE ? AND " + CalendarContract.Events.DTSTART + ">=? AND " + CalendarContract.Events.DTSTART + "<?",
                new String[]{String.valueOf(cal), "%" + TAG + "%", String.valueOf(from * 86400000L), String.valueOf((to + 1) * 86400000L)}, null)) {
            if (cur != null) while (cur.moveToNext()) {
                int n = (int) Math.floorDiv(cur.getLong(1), 86400000L);
                if (have.containsKey(n)) { cr.delete(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, cur.getLong(0)), null, null); continue; }
                have.put(n, cur.getString(2)); ids.put(n, cur.getLong(0));
            }
        } catch (Exception e) { return; }

        for (Map.Entry<Integer, Long> e : ids.entrySet()) {
            String w = want.get(e.getKey());
            if (w == null) cr.delete(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, e.getValue()), null, null);
            else if (!w.equals(have.get(e.getKey()))) {
                ContentValues v = new ContentValues();
                v.put(CalendarContract.Events.TITLE, w);
                cr.update(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, e.getValue()), v, null, null);
            }
        }
        for (Map.Entry<Integer, String> e : want.entrySet()) {
            if (have.containsKey(e.getKey())) continue;
            ContentValues v = new ContentValues();
            v.put(CalendarContract.Events.CALENDAR_ID, cal);
            v.put(CalendarContract.Events.TITLE, e.getValue());
            v.put(CalendarContract.Events.DESCRIPTION, "근무표 앱이 자동으로 넣은 일정이에요. 근무가 바뀌면 같이 바뀌어요. " + TAG);
            v.put(CalendarContract.Events.DTSTART, e.getKey() * 86400000L);
            v.put(CalendarContract.Events.DTEND, (e.getKey() + 1) * 86400000L);
            v.put(CalendarContract.Events.ALL_DAY, 1);
            v.put(CalendarContract.Events.EVENT_TIMEZONE, "UTC");
            v.put(CalendarContract.Events.AVAILABILITY, CalendarContract.Events.AVAILABILITY_FREE);
            v.put(CalendarContract.Events.HAS_ALARM, 0);
            try { cr.insert(CalendarContract.Events.CONTENT_URI, v); } catch (Exception ignored) {}
        }
    }

    /** 앱이 넣은 근무 일정을 모두 지워요 (근무 넣기를 끌 때) */
    static void clear(Context c) {
        long cal = localId(c);
        if (cal < 0 || !CalendarReader.canWrite(c)) return;
        try {
            c.getContentResolver().delete(CalendarContract.Events.CONTENT_URI,
                    CalendarContract.Events.CALENDAR_ID + "=? AND " + CalendarContract.Events.DESCRIPTION + " LIKE ?",
                    new String[]{String.valueOf(cal), "%" + TAG + "%"});
        } catch (Exception ignored) {}
    }
}
