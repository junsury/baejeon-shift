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
            long from = (n0 - 45) * 86400000L - ShiftData.KST.getRawOffset(), to = (n0 + 101) * 86400000L - ShiftData.KST.getRawOffset();
            JSONArray out = new JSONArray();
            int patched = 0;
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
                    if (have != want && patched < 60) {
                        try {
                            GoogleCal.call(tok, "PATCH", "/calendars/" + GoogleCal.enc(id) + "/events/" + GoogleCal.enc(e.optString("id")),
                                    new JSONObject().put("colorId", String.valueOf(want)));
                            patched++;
                        } catch (Exception ignored) {}
                    }
                    if (shift) continue;   // 근무는 위젯에선 칩으로 보여요
                    JSONObject st = e.optJSONObject("start"), en = e.optJSONObject("end");
                    if (st == null) continue;
                    JSONObject o = new JSONObject().put("t", e.optString("summary", "(제목 없음)")).put("id", e.optString("id"))
                            .put("c", FamColors.hex(want)).put("w", FamColors.who(c, creator));
                    if (st.has("date")) {
                        o.put("d0", st.getString("date"));
                        o.put("d1", en != null && en.has("date") ? en.getString("date") : st.getString("date"));
                        o.put("h", "");
                    } else {
                        String s = st.optString("dateTime"), x = en != null ? en.optString("dateTime", s) : s;
                        o.put("d0", s.substring(0, 10)).put("h", s.length() >= 16 ? s.substring(11, 16) : "");
                        o.put("e", x.length() >= 10 ? x.substring(0, 10) : s.substring(0, 10));
                    }
                    out.put(o);
                }
                page = res.optString("nextPageToken", null);
                if (page != null && page.isEmpty()) page = null;
            } while (page != null && ++guard < 8);
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
}
