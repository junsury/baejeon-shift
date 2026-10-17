package kr.baejeon.shift;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/** 서버(widgetData)에서 받은 내 근무 */
final class ShiftData {
    static final TimeZone KST = TimeZone.getTimeZone("Asia/Seoul");
    static final String[] WD = {"일", "월", "화", "수", "목", "금", "토"};

    final String name;
    final JSONObject days;

    private ShiftData(String name, JSONObject days) { this.name = name; this.days = days; }

    static ShiftData parse(String json) {
        try {
            if (json == null || json.isEmpty()) return null;
            JSONObject o = new JSONObject(json);
            return new ShiftData(o.optString("name", ""), o.optJSONObject("days") != null ? o.getJSONObject("days") : new JSONObject());
        } catch (Exception e) { return null; }
    }

    /** 날짜 계산용: 1970-01-01부터 며칠째인지 (한국 시간 기준) */
    static int todayNum() {
        long now = System.currentTimeMillis() + KST.getOffset(System.currentTimeMillis());
        return (int) Math.floorDiv(now, 86400000L);
    }
    static Calendar cal(int n) {
        Calendar c = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        c.setTimeInMillis(n * 86400000L);
        return c;
    }
    static String ymd(int n) {
        Calendar c = cal(n);
        return String.format(Locale.US, "%04d-%02d-%02d", c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH));
    }
    static int dow(int n) { return Math.floorMod(n + 4, 7); }  // 1970-01-01 = 목요일
    static int dayOfMonth(int n) { return cal(n).get(Calendar.DAY_OF_MONTH); }
    static int month(int n) { return cal(n).get(Calendar.MONTH) + 1; }
    static String md(int n) { Calendar c = cal(n); return (c.get(Calendar.MONTH) + 1) + "/" + c.get(Calendar.DAY_OF_MONTH); }
    static String mdw(int n) { return md(n) + "(" + WD[dow(n)] + ")"; }

    JSONObject raw(int n) { return days.optJSONObject(ymd(n)); }

    private static boolean hours(String c) { return "주".equals(c) || "야".equals(c) || "T".equals(c) || "지원".equals(c); }

    /** 화면에 보여줄 근무 코드: 주 야 비 휴 연 24 지원 T 없, 데이터 없으면 null */
    String code(int n) {
        JSONObject d = raw(n);
        if (d == null) return null;
        String c = d.optString("c", "");
        String cv = d.isNull("cv") ? "" : d.optString("cv", "");
        String code = c;
        if (!cv.isEmpty() && !hours(c)) code = cv;
        if (!cv.isEmpty() && hours(c)) code = "24";
        if ("휴".equals(code)) {
            JSONObject p = raw(n - 1);
            if (p != null && ("야".equals(p.optString("c")) || "야".equals(p.optString("cv")))) code = "비";
        }
        return code;
    }

    boolean changed(int n) { JSONObject d = raw(n); return d != null && d.optInt("ch", 0) == 1; }
    boolean holiday(int n) { JSONObject d = raw(n); return d != null && d.optInt("h", 0) == 1; }
    String memo(int n) { JSONObject d = raw(n); return d == null ? "" : d.optString("n", ""); }
    String part(int n) { JSONObject d = raw(n); return d == null ? "" : d.optString("p", ""); }

    List<String> mates(int n) {
        List<String> r = new ArrayList<>();
        JSONObject d = raw(n);
        if (d == null) return r;
        JSONArray a = d.optJSONArray("m");
        if (a != null) for (int i = 0; i < a.length(); i++) r.add(a.optString(i));
        return r;
    }

    static String label(String code) {
        if (code == null) return "";
        switch (code) {
            case "연": return "휴가";
            case "지원": return "지";
            case "없": return "";
            default: return code;
        }
    }

    /** "주간 08–18" 같은 긴 이름 */
    static String longName(String code) {
        if (code == null) return "근무 정보 없음";
        switch (code) {
            case "주": return "주간 08–18";
            case "야": return "야간 18–08";
            case "24": return "주+야 24시간";
            case "비": return "비번";
            case "휴": return "휴무";
            case "연": return "휴가";
            case "지원": return "근무지원 08–18";
            case "T": return "TSO 근무";
            default: return "근무 없음";
        }
    }

    /** "주간" "08–18" 처럼 두 줄로 */
    static String[] twoLine(String code) {
        if (code == null) return new String[]{"정보 없음", ""};
        switch (code) {
            case "주": return new String[]{"주간", "08–18"};
            case "야": return new String[]{"야간", "18–08"};
            case "24": return new String[]{"24시간", "08–08"};
            case "지원": return new String[]{"근무지원", "08–18"};
            case "T": return new String[]{"TSO", "근무"};
            default: return new String[]{longName(code), ""};
        }
    }

    static String shortName(String code) {
        if (code == null) return "";
        switch (code) {
            case "주": return "주간";
            case "야": return "야간";
            case "24": return "24시간";
            case "비": return "비번";
            case "휴": return "휴무";
            case "연": return "휴가";
            case "지원": return "지원";
            case "T": return "근무";
            default: return "";
        }
    }
}
