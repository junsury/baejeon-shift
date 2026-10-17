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
        long eventId = -1; int color = 0; boolean memo = false; String who = "";
        Ev(String title, boolean allDay, long begin, String time) { this.title = title; this.allDay = allDay; this.begin = begin; this.time = time; }
        String text() { return time.isEmpty() ? title : title + " " + time; }
    }

    static boolean allowed(Context c) {
        return c.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED;
    }

    /** from~to(날짜 번호, 포함) 사이 일정. 키 = 날짜 번호 */
    /** 휴대폰에 있는 캘린더 목록 */
    static final class Cal {
        final long id; final String name, account; final int color;
        boolean visible = true, synced = true;
        Cal(long id, String name, String account, int color) { this.id = id; this.name = name; this.account = account; this.color = color; }
    }

    static List<Cal> list(Context c) {
        List<Cal> out = new ArrayList<>();
        if (!allowed(c)) return out;
        String[] cols = {CalendarContract.Calendars._ID, CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
                CalendarContract.Calendars.ACCOUNT_NAME, CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.Calendars.CALENDAR_COLOR,
                CalendarContract.Calendars.VISIBLE, CalendarContract.Calendars.SYNC_EVENTS};
        try (Cursor cur = c.getContentResolver().query(CalendarContract.Calendars.CONTENT_URI, cols,
                null, null, CalendarContract.Calendars.ACCOUNT_TYPE + "," + CalendarContract.Calendars.ACCOUNT_NAME)) {
            if (cur == null) return out;
            while (cur.moveToNext()) {
                String type = cur.getString(3) == null ? "" : cur.getString(3);
                String acc = cur.getString(2) == null ? "" : cur.getString(2);
                String where;
                if (type.equals("com.google")) where = "구글 · " + acc;
                else if (type.contains("osp") || type.contains("samsung")) where = "삼성 캘린더" + (acc.isEmpty() ? "" : " · " + acc);
                else if (type.equals(CalendarContract.ACCOUNT_TYPE_LOCAL) || type.isEmpty()) where = "휴대폰";
                else where = acc.isEmpty() ? type : acc;
                String name = cur.getString(1) == null ? "(이름 없음)" : cur.getString(1);
                Cal cl = new Cal(cur.getLong(0), name, where, cur.getInt(4));
                cl.visible = cur.getInt(5) == 1; cl.synced = cur.getInt(6) == 1;
                out.add(cl);
            }
        } catch (Exception ignored) {}
        return out;
    }

    static Map<Integer, List<Ev>> read(Context c, int from, int to) { return read(c, from, to, Store.calExcluded(c)); }

    /** excl = 뺄 캘린더 */
    static Map<Integer, List<Ev>> read(Context c, int from, int to, java.util.Set<Long> excl) {
        Map<Integer, List<Ev>> out = new HashMap<>();
        java.util.Set<String> seenAll = new java.util.HashSet<>();
        // 가족 공유 캘린더는 구글 서버에서 바로 받은 것을 먼저 (작성자·색 정보가 있어요)
        boolean famFirst = false;
        try {
            long fam = ShiftSync.localId(c);
            if (fam < 0 || excl == null || !excl.contains(fam)) { FamApi.addTo(c, out, from, to, seenAll); famFirst = true; }
        } catch (Exception ignored) {}
        if (!allowed(c)) return out;
        TimeZone kst = ShiftData.KST;
        long start = from * 86400000L - kst.getOffset(from * 86400000L);
        long end = (to + 1) * 86400000L - kst.getOffset(to * 86400000L);
        Uri.Builder b = CalendarContract.Instances.CONTENT_URI.buildUpon();
        ContentUris.appendId(b, start - 86400000L);
        ContentUris.appendId(b, end + 86400000L);
        String[] cols = {CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN, CalendarContract.Instances.END,
                CalendarContract.Instances.ALL_DAY, CalendarContract.Instances.VISIBLE, CalendarContract.Instances.CALENDAR_ID,
                CalendarContract.Instances.EVENT_ID, CalendarContract.Instances.DISPLAY_COLOR, CalendarContract.Instances.DESCRIPTION};
        try (Cursor cur = c.getContentResolver().query(b.build(), cols, CalendarContract.Instances.VISIBLE + "=1", null,
                CalendarContract.Instances.BEGIN + " ASC")) {
            if (cur == null) return out;
            java.util.Set<String> seen = seenAll;
            while (cur.moveToNext()) {
                if (excl != null && excl.contains(cur.getLong(5))) continue;
                String desc = cur.getString(8);
                if (desc != null && desc.contains(ShiftSync.TAG)) continue;   // 앱이 넣은 근무 일정은 위젯에선 칩으로 보여서 빼요
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
                    if (!seen.add(n + "|" + title.trim() + "|" + (n == d0 ? time : ""))) continue;   // 같은 날 같은 제목은 한 번만
                    List<Ev> l = out.get(n);
                    if (l == null) { l = new ArrayList<>(); out.put(n, l); }
                    Ev ev = new Ev(title.trim(), all || n != d0, bg, n == d0 ? time : "");
                    ev.eventId = cur.getLong(6); ev.color = cur.getInt(7);
                    l.add(ev);
                }
            }
        } catch (Exception ignored) {}
        // 가족 공유 캘린더는 구글 서버에서 바로 받은 것도 합쳐요 (휴대폰 동기화가 늦을 때 대비)
        for (List<Ev> l : out.values()) Collections.sort(l, (x, y) -> x.allDay != y.allDay ? (x.allDay ? -1 : 1) : Long.compare(x.begin, y.begin));
        return out;
    }

    static boolean canWrite(Context c) {
        return c.checkSelfPermission(Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED;
    }

    /** 일정을 넣을 수 있는 캘린더 (휴일 캘린더 등 읽기 전용 제외) */
    static List<Cal> writable(Context c) {
        List<Cal> out = new ArrayList<>();
        if (!allowed(c)) return out;
        String[] cols = {CalendarContract.Calendars._ID, CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
                CalendarContract.Calendars.ACCOUNT_NAME, CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.Calendars.CALENDAR_COLOR};
        String where = CalendarContract.Calendars.VISIBLE + "=1 AND " + CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL + ">=" + CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR;
        try (Cursor cur = c.getContentResolver().query(CalendarContract.Calendars.CONTENT_URI, cols, where, null,
                CalendarContract.Calendars.IS_PRIMARY + " DESC")) {
            if (cur == null) return out;
            while (cur.moveToNext()) {
                String type = cur.getString(3) == null ? "" : cur.getString(3);
                String acc = cur.getString(2) == null ? "" : cur.getString(2);
                String w = type.equals("com.google") ? "구글 · " + acc : (type.contains("osp") || type.contains("samsung")) ? "삼성 캘린더" : (type.equals(CalendarContract.ACCOUNT_TYPE_LOCAL) || type.isEmpty()) ? "휴대폰" : acc;
                out.add(new Cal(cur.getLong(0), cur.getString(1) == null ? "(이름 없음)" : cur.getString(1), w, cur.getInt(4)));
            }
        } catch (Exception ignored) {}
        return out;
    }

    /** 일정 넣기. 종일이면 startMin/endMin 무시. 성공하면 true */
    static boolean insert(Context c, long calId, String title, int day, boolean allDay, int startMin, int endMin, String memo) {
        try {
            android.content.ContentValues v = new android.content.ContentValues();
            v.put(CalendarContract.Events.CALENDAR_ID, calId);
            v.put(CalendarContract.Events.TITLE, title);
            if (memo != null && !memo.isEmpty()) v.put(CalendarContract.Events.DESCRIPTION, memo);
            if (allDay) {
                v.put(CalendarContract.Events.DTSTART, day * 86400000L);
                v.put(CalendarContract.Events.DTEND, (day + 1) * 86400000L);
                v.put(CalendarContract.Events.ALL_DAY, 1);
                v.put(CalendarContract.Events.EVENT_TIMEZONE, "UTC");
            } else {
                long base = day * 86400000L - ShiftData.KST.getOffset(day * 86400000L);
                long st = base + startMin * 60000L;
                long en = base + (endMin <= startMin ? endMin + 1440 : endMin) * 60000L;   // 끝이 더 이르면 다음날
                v.put(CalendarContract.Events.DTSTART, st);
                v.put(CalendarContract.Events.DTEND, en);
                v.put(CalendarContract.Events.EVENT_TIMEZONE, "Asia/Seoul");
            }
            return c.getContentResolver().insert(CalendarContract.Events.CONTENT_URI, v) != null;
        } catch (Exception e) { return false; }
    }

    /** 그 캘린더의 계정을 지금 동기화해 달라고 (넣은 일정이 구글로 바로 올라가게) */
    static void syncCalendar(Context c, long calId) {
        String[] cols = {CalendarContract.Calendars.ACCOUNT_NAME, CalendarContract.Calendars.ACCOUNT_TYPE};
        try (Cursor cur = c.getContentResolver().query(android.content.ContentUris.withAppendedId(CalendarContract.Calendars.CONTENT_URI, calId), cols, null, null, null)) {
            if (cur != null && cur.moveToFirst()) requestSync(cur.getString(0), cur.getString(1));
        } catch (Exception ignored) {}
    }

    /** 휴대폰의 구글 계정 캘린더를 모두 동기화 요청 (가족이 넣은 일정을 빨리 받기) */
    static void syncAllGoogle(Context c) {
        if (!allowed(c)) return;
        java.util.Set<String> done = new java.util.HashSet<>();
        String[] cols = {CalendarContract.Calendars.ACCOUNT_NAME, CalendarContract.Calendars.ACCOUNT_TYPE};
        try (Cursor cur = c.getContentResolver().query(CalendarContract.Calendars.CONTENT_URI, cols,
                CalendarContract.Calendars.ACCOUNT_TYPE + "='com.google'", null, null)) {
            if (cur != null) while (cur.moveToNext()) if (done.add(cur.getString(0))) requestSync(cur.getString(0), cur.getString(1));
        } catch (Exception ignored) {}
    }

    static void requestSync(String name, String type) {
        if (name == null || type == null || CalendarContract.ACCOUNT_TYPE_LOCAL.equals(type)) return;
        try {
            android.os.Bundle b = new android.os.Bundle();
            b.putBoolean(android.content.ContentResolver.SYNC_EXTRAS_MANUAL, true);
            b.putBoolean(android.content.ContentResolver.SYNC_EXTRAS_EXPEDITED, true);
            android.content.ContentResolver.requestSync(new android.accounts.Account(name, type), CalendarContract.AUTHORITY, b);
        } catch (Exception ignored) {}
    }

    /** 캘린더별 일정 개수 (진단용) */
    static java.util.Map<Long, Integer> countByCal(Context c, int from, int to) {
        java.util.Map<Long, Integer> m = new HashMap<>();
        if (!allowed(c)) return m;
        Uri.Builder b = CalendarContract.Instances.CONTENT_URI.buildUpon();
        ContentUris.appendId(b, from * 86400000L);
        ContentUris.appendId(b, (to + 1) * 86400000L);
        try (Cursor cur = c.getContentResolver().query(b.build(), new String[]{CalendarContract.Instances.CALENDAR_ID}, null, null, null)) {
            if (cur != null) while (cur.moveToNext()) { long id = cur.getLong(0); Integer v = m.get(id); m.put(id, v == null ? 1 : v + 1); }
        } catch (Exception ignored) {}
        return m;
    }

    /** 숨김·동기화 꺼짐 캘린더 켜기 */
    static boolean enable(Context c, long id) {
        try {
            android.content.ContentValues v = new android.content.ContentValues();
            v.put(CalendarContract.Calendars.VISIBLE, 1);
            v.put(CalendarContract.Calendars.SYNC_EVENTS, 1);
            c.getContentResolver().update(ContentUris.withAppendedId(CalendarContract.Calendars.CONTENT_URI, id), v, null, null);
            syncCalendar(c, id);
            return true;
        } catch (Exception e) { return false; }
    }
}
