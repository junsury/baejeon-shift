package kr.baejeon.shift;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 휴대폰 캘린더 일정(위젯에 보이는 것과 같은 캘린더)을 근무표 앱 '내 근무 · 월'에도 보이게 서버로 보내요.
 * 본인 계정에서만 읽을 수 있어요. 바뀐 게 있을 때만 보내요. (백그라운드 스레드에서 호출)
 */
final class CalUpload {
    private CalUpload() {}
    static final String API = "https://asia-northeast3-baejeon-shift.cloudfunctions.net/widgetEvents";

    static synchronized void upload(Context c, boolean force) {
        try {
            String tok = Store.token(c);
            if (tok.isEmpty()) return;
            boolean on = Store.p(c).getBoolean("appCal", true) && CalendarReader.allowed(c);
            int n0 = ShiftData.todayNum(), from = n0 - 45, to = n0 + 100;
            JSONObject body = new JSONObject().put("t", tok);
            if (!on) body.put("off", true);
            else {
                Map<Integer, List<CalendarReader.Ev>> m = new TreeMap<>(CalendarReader.read(c, from, to));
                JSONArray items = new JSONArray();
                for (Map.Entry<Integer, List<CalendarReader.Ev>> e : m.entrySet())
                    for (CalendarReader.Ev ev : e.getValue())
                        items.put(new JSONObject().put("d", ShiftData.ymd(e.getKey())).put("t", ev.title).put("h", ev.time).put("c", ev.color).put("w", ev.who));
                body.put("items", items).put("from", ShiftData.ymd(from)).put("to", ShiftData.ymd(to));
            }
            String key = on ? body.optJSONArray("items").toString() : "off";
            String hash = Integer.toHexString(key.hashCode()) + ":" + n0;
            long last = Store.p(c).getLong("calUpAt", 0);
            if (!force && hash.equals(Store.p(c).getString("calHash", "")) && System.currentTimeMillis() - last < 6 * 3600e3) return;
            HttpURLConnection con = (HttpURLConnection) new URL(API).openConnection();
            con.setRequestMethod("POST"); con.setDoOutput(true);
            con.setConnectTimeout(8000); con.setReadTimeout(10000);
            con.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            try (OutputStream o = con.getOutputStream()) { o.write(body.toString().getBytes(StandardCharsets.UTF_8)); }
            int code = con.getResponseCode();
            con.disconnect();
            if (code == 200) Store.p(c).edit().putString("calHash", hash).putLong("calUpAt", System.currentTimeMillis()).apply();
        } catch (Exception ignored) {}
    }
}
