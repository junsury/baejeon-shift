package kr.baejeon.shift;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashSet;
import java.util.Set;

/**
 * 앱 안 업데이트.
 * - GitHub 릴리스의 version.json(새 버전 번호 + 바뀐 점)을 가끔 확인해 저장해 둬요.
 * - 앱을 열 때 새 버전이 있으면 물어보고, [업데이트]면 APK를 받아 설치 화면을 띄워요.
 * - 업데이트 후 처음 열면 앱에 들어 있는 changes.json으로 '이번에 바뀐 점'을 보여줘요.
 */
final class Updater {
    static final String BASE = "https://github.com/junsury/baejeon-shift/releases/download/android/";
    static final String VER = BASE + "version.json";
    static final String APK = BASE + "baejeon-shift.apk";
    static final long CHECK_EVERY = 30 * 60 * 1000L;     // 30분에 한 번만 확인
    static final long LATER_FOR = 20 * 60 * 60 * 1000L;  // [나중에] 누르면 그 버전은 20시간 동안 안 물어봄

    static SharedPreferences p(Context c) { return Store.p(c); }

    static int myCode(Context c) {
        try {
            PackageInfo pi = c.getPackageManager().getPackageInfo(c.getPackageName(), 0);
            return (int) pi.getLongVersionCode();
        } catch (Exception e) { return 0; }
    }

    static boolean due(Context c) {
        return System.currentTimeMillis() - p(c).getLong("updAt", 0) > CHECK_EVERY;
    }

    /** 새 버전 정보 확인 (네트워크, 백그라운드에서) */
    static void check(Context c, boolean force) {
        if (!force && !due(c)) return;
        try {
            String s = get(VER + "?t=" + System.currentTimeMillis());
            JSONObject j = new JSONObject(s);
            if (j.optInt("code", 0) <= 0) return;
            p(c).edit().putString("updJson", s).putLong("updAt", System.currentTimeMillis()).apply();
        } catch (Exception ignore) { }
    }

    static void checkAsync(Context c) {
        final Context a = c.getApplicationContext();
        new Thread(() -> check(a, false)).start();
    }

    /** 앱을 여는 순간: 최근에 확인 안 했으면 최대 ms 동안만 기다려 확인 */
    static void checkBriefly(Context c, long ms) {
        if (!due(c)) return;
        final Context a = c.getApplicationContext();
        Thread t = new Thread(() -> check(a, true));
        t.start();
        try { t.join(ms); } catch (InterruptedException ignore) { }
    }

    /** 물어볼 새 버전 (없으면 null) */
    static JSONObject offer(Context c) {
        JSONObject j;
        try { j = new JSONObject(p(c).getString("updJson", "")); } catch (Exception e) { return null; }
        int code = j.optInt("code", 0);
        if (code <= myCode(c)) return null;
        SharedPreferences sp = p(c);
        if (sp.getInt("updLaterCode", 0) == code
                && System.currentTimeMillis() - sp.getLong("updLaterAt", 0) < LATER_FOR) return null;
        return j;
    }

    static void later(Context c, int code) {
        p(c).edit().putInt("updLaterCode", code).putLong("updLaterAt", System.currentTimeMillis()).apply();
    }

    /** 새 버전에서 바뀐 점 (지금 앱에 이미 들어 있는 내용은 빼고) */
    static String offerText(Context c, JSONObject j) {
        return fmt(j.optJSONArray("changes"), ids(bundled(c)), 4);
    }

    // ---------- 업데이트 후 '이번에 바뀐 점' ----------

    static JSONArray bundled(Context c) {
        try (InputStream in = c.getAssets().open("changes.json")) {
            return new JSONArray(new String(readAll(in), "UTF-8"));
        } catch (Exception e) { return new JSONArray(); }
    }

    static Set<String> ids(JSONArray a) {
        Set<String> s = new HashSet<>();
        if (a != null) for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o != null) s.add(o.optString("id"));
        }
        return s;
    }

    /** 업데이트 후 처음 열 때 보여줄 내용 (없으면 null) */
    static String whatsNew(Context c) {
        SharedPreferences sp = p(c);
        int code = myCode(c);
        if (sp.getInt("updLastCode", 0) == code) return null;
        JSONArray b = bundled(c);
        if (b.length() == 0) { markSeen(c); return null; }
        Set<String> seen = sp.getStringSet("updSeen", null);
        String t;
        if (seen == null) {
            // 이 기능이 처음 들어간 버전: 새로 설치한 사람은 건너뛰고, 쓰던 사람에게는 맨 위 내용만
            boolean fresh = !sp.contains("askedNotify") && !sp.contains("token") && !sp.contains("data");
            if (fresh) { markSeen(c); return null; }
            t = fmt(b, new HashSet<>(), 1);
        } else {
            t = fmt(b, seen, 4);
        }
        if (t.isEmpty()) { markSeen(c); return null; }
        return t;
    }

    static void markSeen(Context c) {
        p(c).edit().putInt("updLastCode", myCode(c)).putStringSet("updSeen", ids(bundled(c))).apply();
    }

    /** 바뀐 점을 '• 내용' 줄로 (skip에 있는 묶음은 빼고, 최대 max묶음) */
    static String fmt(JSONArray a, Set<String> skip, int max) {
        StringBuilder sb = new StringBuilder();
        int n = 0;
        if (a != null) for (int i = 0; i < a.length() && n < max; i++) {
            JSONObject o = a.optJSONObject(i);
            if (o == null || skip.contains(o.optString("id"))) continue;
            JSONArray items = o.optJSONArray("items");
            if (items == null) continue;
            for (int k = 0; k < items.length(); k++) {
                if (sb.length() > 0) sb.append("\n\n");
                sb.append("• ").append(items.optString(k));
            }
            n++;
        }
        return sb.toString();
    }

    // ---------- 내려받기 ----------

    interface Progress { void at(int pct); }

    static File apkFile(Context c) {
        File d = new File(c.getCacheDir(), "upd");
        d.mkdirs();
        return new File(d, "baejeon-shift.apk");
    }

    /** 이미 받아 둔 파일이 그 버전이면 다시 안 받아요 */
    static boolean ready(Context c, int code) {
        File f = apkFile(c);
        if (!f.exists()) return false;
        PackageInfo pi = c.getPackageManager().getPackageArchiveInfo(f.getPath(), 0);
        return pi != null && c.getPackageName().equals(pi.packageName) && pi.getLongVersionCode() >= code;
    }

    static boolean download(Context c, int code, Progress pr) {
        if (ready(c, code)) return true;
        File f = apkFile(c), tmp = new File(f.getPath() + ".part");
        HttpURLConnection h = null;
        try {
            h = (HttpURLConnection) new URL(APK + "?t=" + System.currentTimeMillis()).openConnection();
            h.setInstanceFollowRedirects(true);
            h.setConnectTimeout(15000);
            h.setReadTimeout(30000);
            long total = h.getContentLengthLong(), got = 0;
            int last = -1;
            try (InputStream in = h.getInputStream(); OutputStream out = new FileOutputStream(tmp)) {
                byte[] buf = new byte[64 * 1024];
                int r;
                while ((r = in.read(buf)) > 0) {
                    out.write(buf, 0, r);
                    got += r;
                    if (total > 0) {
                        int pct = (int) (got * 100 / total);
                        if (pct != last) { last = pct; pr.at(pct); }
                    }
                }
            }
            f.delete();
            if (!tmp.renameTo(f)) return false;
            if (ready(c, 1)) return true;   // 진짜 이 앱의 APK인지 확인
            f.delete();
            return false;
        } catch (Exception e) {
            tmp.delete();
            return false;
        } finally {
            if (h != null) h.disconnect();
        }
    }

    // ---------- 도우미 ----------

    static String get(String u) throws Exception {
        HttpURLConnection h = (HttpURLConnection) new URL(u).openConnection();
        h.setInstanceFollowRedirects(true);
        h.setConnectTimeout(5000);
        h.setReadTimeout(8000);
        try (InputStream in = h.getInputStream()) {
            if (h.getResponseCode() != 200) throw new Exception("http " + h.getResponseCode());
            return new String(readAll(in), "UTF-8");
        } finally { h.disconnect(); }
    }

    static byte[] readAll(InputStream in) throws Exception {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] b = new byte[8192];
        int r;
        while ((r = in.read(b)) > 0) bo.write(b, 0, r);
        return bo.toByteArray();
    }
}
