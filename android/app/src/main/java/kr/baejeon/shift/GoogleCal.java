package kr.baejeon.shift;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** 구글 캘린더 API (공유 캘린더 만들기·공유할 사람 관리에만 사용) */
final class GoogleCal {
    private GoogleCal() {}

    static final String BASE = "https://www.googleapis.com/calendar/v3";
    static final String[] SCOPES = {
            "https://www.googleapis.com/auth/calendar.app.created",
            "https://www.googleapis.com/auth/calendar.acls"};

    static final class Err extends Exception { final int code; Err(int code, String m) { super(m); this.code = code; } }

    static JSONObject call(String token, String method, String path, JSONObject body) throws Exception {
        HttpURLConnection con = (HttpURLConnection) new URL(BASE + path).openConnection();
        con.setRequestMethod(method);
        con.setConnectTimeout(10000); con.setReadTimeout(15000);
        con.setRequestProperty("Authorization", "Bearer " + token);
        if (body != null) {
            con.setDoOutput(true);
            con.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            try (OutputStream o = con.getOutputStream()) { o.write(body.toString().getBytes(StandardCharsets.UTF_8)); }
        }
        int code = con.getResponseCode();
        InputStream in = code < 400 ? con.getInputStream() : con.getErrorStream();
        String txt = "";
        if (in != null) try (InputStream i = in) {
            ByteArrayOutputStream bo = new ByteArrayOutputStream(); byte[] buf = new byte[8192]; int r;
            while ((r = i.read(buf)) > 0) bo.write(buf, 0, r);
            txt = new String(bo.toByteArray(), StandardCharsets.UTF_8);
        }
        con.disconnect();
        if (code >= 400) {
            String msg = txt;
            try { msg = new JSONObject(txt).getJSONObject("error").optString("message", txt); } catch (Exception ignored) {}
            throw new Err(code, msg);
        }
        return txt.isEmpty() ? new JSONObject() : new JSONObject(txt);
    }

    static String enc(String s) throws Exception { return URLEncoder.encode(s, "UTF-8"); }

    /** 새 캘린더 만들기 → 캘린더 id */
    static String createCalendar(String token, String summary) throws Exception {
        JSONObject b = new JSONObject().put("summary", summary).put("timeZone", "Asia/Seoul")
                .put("description", "배전센터 근무표 앱이 만든 가족 공유 캘린더예요. 근무가 자동으로 들어가고, 가족도 일정을 넣을 수 있어요.");
        return call(token, "POST", "/calendars", b).getString("id");
    }

    static final class Share { final String ruleId, email, role; Share(String r, String e, String ro) { ruleId = r; email = e; role = ro; } }

    static List<Share> listShares(String token, String calId) throws Exception {
        JSONArray items = call(token, "GET", "/calendars/" + enc(calId) + "/acl", null).optJSONArray("items");
        List<Share> out = new ArrayList<>();
        if (items != null) for (int i = 0; i < items.length(); i++) {
            JSONObject it = items.getJSONObject(i);
            JSONObject sc = it.optJSONObject("scope");
            if (sc == null || !"user".equals(sc.optString("type"))) continue;
            String role = it.optString("role");
            if ("owner".equals(role)) continue;   // 나(소유자)는 목록에서 빼요
            out.add(new Share(it.optString("id"), sc.optString("value"), role));
        }
        return out;
    }

    /** 캘린더 주인(내 구글 계정) 이메일 */
    static String ownerOf(String token, String calId) throws Exception {
        JSONArray items = call(token, "GET", "/calendars/" + enc(calId) + "/acl", null).optJSONArray("items");
        if (items != null) for (int i = 0; i < items.length(); i++) {
            JSONObject it = items.getJSONObject(i); JSONObject sc = it.optJSONObject("scope");
            if (sc != null && "user".equals(sc.optString("type")) && "owner".equals(it.optString("role"))
                    && !sc.optString("value").equals(calId)) return sc.optString("value");
        }
        return "";
    }

    static void share(String token, String calId, String email) throws Exception {
        JSONObject b = new JSONObject().put("role", "writer").put("scope", new JSONObject().put("type", "user").put("value", email));
        call(token, "POST", "/calendars/" + enc(calId) + "/acl?sendNotifications=true", b);
    }

    static void unshare(String token, String calId, String ruleId) throws Exception {
        call(token, "DELETE", "/calendars/" + enc(calId) + "/acl/" + enc(ruleId), null);
    }
}
