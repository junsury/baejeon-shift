package kr.baejeon.shift;

import android.content.Context;

import com.google.android.gms.auth.api.identity.AuthorizationRequest;
import com.google.android.gms.auth.api.identity.AuthorizationResult;
import com.google.android.gms.auth.api.identity.Identity;
import com.google.android.gms.common.api.Scope;
import com.google.android.gms.tasks.Tasks;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * 가족 공유 캘린더("근무표")를 구글 서버와 바로 주고받아요.
 * 휴대폰 캘린더 동기화를 기다리지 않아서, 위젯에서 넣은 일정이 아이폰에 바로 가고
 * 가족이 넣은 일정도 15분 안에(앱을 열면 바로) 위젯에 들어와요. 백그라운드 스레드에서만 호출.
 */
final class FamApi {
    private FamApi() {}

    static String calId(Context c) { return Store.p(c).getString("famCalId", ""); }

    /** 이미 허용한 권한으로 화면 없이 토큰 받기 (안 되면 null) */
    static String token(Context c) {
        try {
            List<Scope> scopes = new ArrayList<>();
            for (String s : GoogleCal.SCOPES) scopes.add(new Scope(s));
            AuthorizationResult r = Tasks.await(Identity.getAuthorizationClient(c.getApplicationContext())
                    .authorize(AuthorizationRequest.builder().setRequestedScopes(scopes).build()), 15, TimeUnit.SECONDS);
            if (r.hasResolution()) return null;
            return r.getAccessToken();
        } catch (Exception e) { return null; }
    }

    static String rfc(long ms) {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US);
        f.setTimeZone(ShiftData.KST);
        return f.format(new Date(ms));
    }

    /** 공유 캘린더 일정 새로 받기 → 휴대폰에 저장 */
    static boolean refresh(Context c) {
        String id = calId(c);
        if (id.isEmpty()) return false;
        String tok = token(c);
        if (tok == null) return false;
        try {
            int n0 = ShiftData.todayNum();
            long from = (n0 - 370) * 86400000L - ShiftData.KST.getRawOffset(), to = (n0 + 400) * 86400000L - ShiftData.KST.getRawOffset();
            JSONArray out = new JSONArray();
            int patched = 0;
            java.util.Set<String> painted = new java.util.HashSet<>();
            String page = null;
            int guard = 0;
            do {
                String q = "/calendars/" + GoogleCal.enc(id) + "/events?singleEvents=true&orderBy=startTime&maxResults=250&timeZone=Asia%2FSeoul"
                        + "&timeMin=" + GoogleCal.enc(rfc(from)) + "&timeMax=" + GoogleCal.enc(rfc(to))
                        + (page != null ? "&pageToken=" + GoogleCal.enc(page) : "");
                JSONObject res = GoogleCal.call(tok, "GET", q, null);
                JSONArray items = res.optJSONArray("items");
                if (items != null) for (int i = 0; i < items.length(); i++) {
                    JSONObject e = items.getJSONObject(i);
                    if ("cancelled".equals(e.optString("status"))) continue;
                    // 누가 만들었는지에 따라 색을 자동으로 칠해요 (근무는 주간 노랑·야간 남색)
                    boolean shift = e.optString("description").contains(ShiftSync.TAG);
                    JSONObject cr = e.optJSONObject("creator");
                    String creator = cr != null ? cr.optString("email", "") : "";
                    int want = shift ? FamColors.shiftColor(e.optString("summary")) : FamColors.colorOf(c, creator);
                    int have = Integer.parseInt(e.optString("colorId", "0").isEmpty() ? "0" : e.optString("colorId", "0"));
                    // 누가 한 번이라도 색을 직접 골랐으면(구글 캘린더 앱이든 근무표 앱이든) 그대로 둬요. 색이 없는 일정만 만든 사람 색으로
                    if (!shift && have > 0) want = have;
                    if (have != want && patched < 60) {
                        // 반복 일정은 날짜별로 칠하면 쪼개져요 → 반복 전체를 한 번만
                        String target = e.has("recurringEventId") ? e.optString("recurringEventId") : e.optString("id");
                        if (painted.add(target)) try {
                            GoogleCal.call(tok, "PATCH", "/calendars/" + GoogleCal.enc(id) + "/events/" + GoogleCal.enc(target),
                                    new JSONObject().put("colorId", String.valueOf(want)));
                            patched++;
                        } catch (Exception ignored) {}
                    }
                    if (shift) continue;   // 근무는 위젯에선 칩으로 보여요
                    JSONObject st = e.optJSONObject("start"), en = e.optJSONObject("end");
                    if (st == null) continue;
                    JSONObject o = new JSONObject().put("t", e.optString("summary", "(제목 없음)")).put("id", e.optString("id"))
                            .put("c", FamColors.hex(want)).put("w", FamColors.who(c, creator)).put("m", e.optString("description", ""));
                    if (e.has("recurringEventId")) o.put("r", e.optString("recurringEventId"));
                    JSONObject xp = e.optJSONObject("extendedProperties"), pv = xp != null ? xp.optJSONObject("private") : null;
                    if (pv != null && pv.has("bjs")) o.put("s", pv.optString("bjs"));
                    if (st.has("date")) {
                        o.put("d0", st.getString("date"));
                        o.put("d1", en != null && en.has("date") ? en.getString("date") : st.getString("date"));
                        o.put("h", "");
                    } else {
                        String s = st.optString("dateTime"), x = en != null ? en.optString("dateTime", s) : s;
                        o.put("d0", s.substring(0, 10)).put("h", s.length() >= 16 ? s.substring(11, 16) : "");
                        // 끝이 자정(00:00)이면 그날은 포함하지 않아요 (예: 20일 23:00~21일 00:00 → 20일만)
                        String endDay = x.length() >= 10 ? x.substring(0, 10) : s.substring(0, 10);
                        if (x.length() >= 16 && x.substring(11, 16).equals("00:00") && !endDay.equals(s.substring(0, 10)))
                            endDay = ShiftData.ymd(dayNum(endDay) - 1);
                        o.put("e", endDay);
                        o.put("eh", x.length() >= 16 ? x.substring(11, 16) : "");
                    }
                    out.put(o);
                }
                page = res.optString("nextPageToken", null);
                if (page != null && page.isEmpty()) page = null;
            } while (page != null && ++guard < 24);
            Store.p(c).edit().putString("famEvents", out.toString()).putLong("famEventsAt", System.currentTimeMillis()).apply();
            return true;
        } catch (Exception e) { return false; }
    }

    static int dayNum(String ymd) {
        try {
            return (int) (java.time.LocalDate.parse(ymd).toEpochDay());
        } catch (Exception e) { return Integer.MIN_VALUE; }
    }

    /** 저장해 둔 공유 캘린더 일정 → 날짜별 */
    static void addTo(Context c, java.util.Map<Integer, List<CalendarReader.Ev>> out, int from, int to, java.util.Set<String> seen) {
        String js = Store.p(c).getString("famEvents", "");
        if (js.isEmpty() || calId(c).isEmpty()) return;
        // 서버에서 못 받은 지 오래되면(로그인 만료 등) 지워진 일정이 남지 않게 휴대폰 캘린더 것만 써요
        if (System.currentTimeMillis() - Store.p(c).getLong("famEventsAt", 0) > 3 * 3600000L) return;
        try {
            JSONArray a = new JSONArray(js);
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                String title = o.optString("t").trim(); String h = o.optString("h");
                int d0 = dayNum(o.optString("d0"));
                int d1 = h.isEmpty() ? dayNum(o.optString("d1")) - 1 : dayNum(o.optString("e", o.optString("d0")));
                if (d0 == Integer.MIN_VALUE) continue;
                if (d1 < d0) d1 = d0;
                for (int n = Math.max(d0, from); n <= Math.min(d1, to); n++) {
                    String time = n == d0 ? h : "";
                    if (!seen.add(n + "|" + title + "|" + time)) continue;
                    List<CalendarReader.Ev> l = out.get(n);
                    if (l == null) { l = new ArrayList<>(); out.put(n, l); }
                    CalendarReader.Ev ev = new CalendarReader.Ev(title, time.isEmpty(), 0, time);
                    ev.color = o.optInt("c", 0xFF8E24AA);
                    ev.who = o.optString("w", "");
                    ev.gid = o.optString("id", null);
                    ev.desc = o.optString("m", "");
                    ev.recurId = o.has("r") ? o.optString("r") : null;
                    ev.series = o.has("s") ? o.optString("s") : null;
                    ev.day0 = d0; ev.day1 = d1;
                    if (!h.isEmpty()) {
                        ev.startMin = hmToMin(h);
                        String eh = o.optString("eh", "");
                        ev.endMin = eh.isEmpty() ? -1 : hmToMin(eh);
                    }
                    l.add(ev);
                }
            }
        } catch (Exception ignored) {}
    }

    /** 공유 캘린더에 바로 일정 넣기 (실패하면 false → 휴대폰 캘린더로 넣어요) */
    static boolean insert(Context c, String title, int day, boolean allDay, int startMin, int endMin, String memo) {
        String id = calId(c);
        if (id.isEmpty()) return false;
        String tok = token(c);
        if (tok == null) return false;
        try {
            JSONObject b = new JSONObject().put("summary", title).put("colorId", String.valueOf(FamColors.myColor(c)));
            if (memo != null && !memo.isEmpty()) b.put("description", memo);
            if (allDay) {
                b.put("start", new JSONObject().put("date", ShiftData.ymd(day)));
                b.put("end", new JSONObject().put("date", ShiftData.ymd(day + 1)));
            } else {
                long base = day * 86400000L - ShiftData.KST.getRawOffset();
                int em = endMin <= startMin ? endMin + 1440 : endMin;
                b.put("start", new JSONObject().put("dateTime", rfc(base + startMin * 60000L)).put("timeZone", "Asia/Seoul"));
                b.put("end", new JSONObject().put("dateTime", rfc(base + em * 60000L)).put("timeZone", "Asia/Seoul"));
            }
            GoogleCal.call(tok, "POST", "/calendars/" + GoogleCal.enc(id) + "/events", b);
            refresh(c);
            return true;
        } catch (Exception e) { return false; }
    }

    /** 일정 내용(JSON) 만들기. remind: -3 그대로, -2 기본, -1 없음, 그 외 몇 분 전 */
    static JSONObject body(String title, int day0, int day1, boolean allDay, int startMin, int endMin, String memo, int remind, boolean forPatch) throws Exception {
        JSONObject b = new JSONObject().put("summary", title);
        if (memo != null || forPatch) b.put("description", memo == null ? "" : memo);
        if (day1 < day0) day1 = day0;
        if (allDay) {
            JSONObject st = new JSONObject().put("date", ShiftData.ymd(day0)), en = new JSONObject().put("date", ShiftData.ymd(day1 + 1));
            if (forPatch) { st.put("dateTime", JSONObject.NULL).put("timeZone", JSONObject.NULL); en.put("dateTime", JSONObject.NULL).put("timeZone", JSONObject.NULL); }
            b.put("start", st).put("end", en);
        } else {
            long b0 = day0 * 86400000L - ShiftData.KST.getRawOffset(), b1 = day1 * 86400000L - ShiftData.KST.getRawOffset();
            int em = (day1 == day0 && endMin <= startMin) ? endMin + 1440 : endMin;
            JSONObject st = new JSONObject().put("dateTime", rfc(b0 + startMin * 60000L)).put("timeZone", "Asia/Seoul");
            JSONObject en = new JSONObject().put("dateTime", rfc(b1 + em * 60000L)).put("timeZone", "Asia/Seoul");
            if (forPatch) { st.put("date", JSONObject.NULL); en.put("date", JSONObject.NULL); }
            b.put("start", st).put("end", en);
        }
        if (remind == -2) b.put("reminders", new JSONObject().put("useDefault", true));
        else if (remind >= -1) {
            JSONArray ov = new JSONArray();
            if (remind >= 0) ov.put(new JSONObject().put("method", "popup").put("minutes", remind));
            b.put("reminders", new JSONObject().put("useDefault", false).put("overrides", ov));
        }
        return b;
    }

    /** 고른 내용대로 공유 캘린더에 넣기 (반복은 규칙 하나로, 음력·몇 주년은 해마다 따로). 넣은 개수, 실패 -1 */
    static int insertSpec(Context c, EvSpec sp) {
        String id = calId(c);
        if (id.isEmpty()) return -1;
        String tok = token(c);
        if (tok == null) return -1;
        try {
            List<EvSpec.Occ> occ = sp.occurrences();
            String rr = sp.rrule(); String sid = sp.expands() ? EvSpec.newSeriesId() : null;
            int n = 0;
            for (EvSpec.Occ o : occ) {
                JSONObject b = body(o.title, o.day0, o.day1, sp.allDay, sp.startMin, sp.endMin, sp.memo.isEmpty() ? null : sp.memo, sp.remind, false);
                b.put("colorId", String.valueOf(sp.color > 0 ? sp.color : FamColors.myColor(c)));
                if (rr != null) b.put("recurrence", new JSONArray().put(rr));
                JSONObject pv = new JSONObject();
                if (sid != null) pv.put("bjs", sid);
                if (sp.color > 0) pv.put("bjc", "1");
                if (pv.length() > 0) b.put("extendedProperties", new JSONObject().put("private", pv));
                try { GoogleCal.callRetry(tok, "POST", "/calendars/" + GoogleCal.enc(id) + "/events", b); }
                catch (Exception e) { break; }   // 하나라도 넣었으면 다른 길로 또 넣지 않아요 (중복 방지)
                n++;
                if (occ.size() > 1) Thread.sleep(150);   // 여러 개는 천천히
            }
            refresh(c);
            return n > 0 ? n : -1;
        } catch (Exception e) { return -1; }
    }

    /** 일정 고치기 (remind -3이면 알림은 그대로) */
    static int editColor = 0;   // 수정할 때 고른 색 (0 = 그대로)
    static boolean update2(Context c, String gid, String title, int day0, int day1, boolean allDay, int startMin, int endMin, String memo, int remind, boolean doRefresh) {
        String id = calId(c); String tok = id.isEmpty() ? null : token(c);
        if (tok == null || gid == null) return false;
        try {
            JSONObject b = body(title, day0, day1, allDay, startMin, endMin, memo, remind, true);
            if (editColor > 0) b.put("colorId", String.valueOf(editColor)).put("extendedProperties", new JSONObject().put("private", new JSONObject().put("bjc", "1")));
            GoogleCal.callRetry(tok, "PATCH", "/calendars/" + GoogleCal.enc(id) + "/events/" + GoogleCal.enc(gid), b);
            if (doRefresh) refresh(c);
            return true;
        } catch (Exception e) { return false; }
    }

    /** 반복 일정 전체(규칙 하나짜리) 고치기: 날짜는 그대로 두고 제목·시간·메모·알림만 */
    static boolean updateMaster(Context c, String masterId, String title, boolean allDay, int startMin, int endMin, String memo, int remind) {
        String id = calId(c); String tok = id.isEmpty() ? null : token(c);
        if (tok == null || masterId == null) return false;
        try {
            JSONObject m = GoogleCal.call(tok, "GET", "/calendars/" + GoogleCal.enc(id) + "/events/" + GoogleCal.enc(masterId), null);
            JSONObject st = m.optJSONObject("start"), en = m.optJSONObject("end");
            String s0 = st.has("date") ? st.getString("date") : st.optString("dateTime").substring(0, 10);
            String s1 = en.has("date") ? en.getString("date") : en.optString("dateTime").substring(0, 10);
            int d0 = dayNum(s0), d1 = en.has("date") ? dayNum(s1) - 1 : dayNum(s1);
            if (allDay && !en.has("date")) d1 = d0;
            if (!allDay && en.has("date")) d1 = d0;
            JSONObject b = body(title, d0, Math.max(d0, d1), allDay, startMin, endMin, memo, remind, true);
            if (editColor > 0) b.put("colorId", String.valueOf(editColor)).put("extendedProperties", new JSONObject().put("private", new JSONObject().put("bjc", "1")));
            GoogleCal.callRetry(tok, "PATCH", "/calendars/" + GoogleCal.enc(id) + "/events/" + GoogleCal.enc(masterId), b);
            refresh(c);
            return true;
        } catch (Exception e) { return false; }
    }

    /** 같은 묶음(음력·몇 주년으로 펼친 일정)들: {id, 제목, 날짜번호} */
    static List<String[]> series(Context c, String sid) {
        List<String[]> out = new ArrayList<>();
        String id = calId(c); String tok = id.isEmpty() ? null : token(c);
        if (tok == null || sid == null) return out;
        try {
            JSONObject res = GoogleCal.call(tok, "GET", "/calendars/" + GoogleCal.enc(id) + "/events?maxResults=250&privateExtendedProperty=" + GoogleCal.enc("bjs=" + sid), null);
            JSONArray items = res.optJSONArray("items");
            if (items != null) for (int i = 0; i < items.length(); i++) {
                JSONObject e = items.getJSONObject(i);
                if ("cancelled".equals(e.optString("status"))) continue;
                JSONObject st = e.optJSONObject("start"); if (st == null) continue;
                String d = st.has("date") ? st.getString("date") : st.optString("dateTime").substring(0, 10);
                out.add(new String[]{e.optString("id"), e.optString("summary", ""), String.valueOf(dayNum(d))});
            }
        } catch (Exception ignored) {}
        return out;
    }

    static boolean deleteNoRefresh(Context c, String gid) {
        String id = calId(c); String tok = id.isEmpty() ? null : token(c);
        if (tok == null || gid == null) return false;
        try { GoogleCal.call(tok, "DELETE", "/calendars/" + GoogleCal.enc(id) + "/events/" + GoogleCal.enc(gid), null); return true; }
        catch (Exception e) { return false; }
    }

    static int hmToMin(String hm) {
        try { return Integer.parseInt(hm.substring(0, 2)) * 60 + Integer.parseInt(hm.substring(3, 5)); } catch (Exception e) { return -1; }
    }

    /** 공유 캘린더 일정 고치기 (색은 처음 만든 사람 색 그대로) */
    static boolean update(Context c, String gid, String title, int day, boolean allDay, int startMin, int endMin, String memo) {
        String id = calId(c); String tok = id.isEmpty() ? null : token(c);
        if (tok == null || gid == null) return false;
        try {
            JSONObject b = new JSONObject().put("summary", title).put("description", memo == null ? "" : memo);
            if (allDay) {
                b.put("start", new JSONObject().put("date", ShiftData.ymd(day)).put("dateTime", JSONObject.NULL).put("timeZone", JSONObject.NULL));
                b.put("end", new JSONObject().put("date", ShiftData.ymd(day + 1)).put("dateTime", JSONObject.NULL).put("timeZone", JSONObject.NULL));
            } else {
                long base = day * 86400000L - ShiftData.KST.getRawOffset();
                int em = endMin <= startMin ? endMin + 1440 : endMin;
                b.put("start", new JSONObject().put("dateTime", rfc(base + startMin * 60000L)).put("timeZone", "Asia/Seoul").put("date", JSONObject.NULL));
                b.put("end", new JSONObject().put("dateTime", rfc(base + em * 60000L)).put("timeZone", "Asia/Seoul").put("date", JSONObject.NULL));
            }
            GoogleCal.call(tok, "PATCH", "/calendars/" + GoogleCal.enc(id) + "/events/" + GoogleCal.enc(gid), b);
            refresh(c);
            return true;
        } catch (Exception e) { return false; }
    }

    static boolean delete(Context c, String gid) {
        String id = calId(c); String tok = id.isEmpty() ? null : token(c);
        if (tok == null || gid == null) return false;
        try {
            GoogleCal.call(tok, "DELETE", "/calendars/" + GoogleCal.enc(id) + "/events/" + GoogleCal.enc(gid), null);
            refresh(c);
            return true;
        } catch (Exception e) { return false; }
    }
}
