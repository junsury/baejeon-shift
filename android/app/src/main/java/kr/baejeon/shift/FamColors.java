package kr.baejeon.shift;

import android.content.Context;

import org.json.JSONObject;

/**
 * 가족 공유 캘린더 일정 색: 누가 만들었는지에 따라 앱이 자동으로 칠해요.
 * 구글 캘린더의 '일정 색'(11가지)을 써서, 갤럭시·아이폰 구글 캘린더 앱에서도 같은 색으로 보여요.
 */
final class FamColors {
    private FamColors() {}

    // 구글 캘린더 일정 색 번호 → 색
    static final String[] NAMES = {"", "라벤더", "세이지", "포도", "플라밍고", "바나나", "귤", "공작", "흑연", "블루베리", "바질", "토마토"};
    static final int[] HEX = {0, 0xFF7986CB, 0xFF33B679, 0xFF8E24AA, 0xFFE67C73, 0xFFF6BF26, 0xFFF4511E, 0xFF039BE5, 0xFF616161, 0xFF3F51B5, 0xFF0B8043, 0xFFD50000};
    static final int DAY = 5, NIGHT = 9, BOTH = 6;            // 근무: 주간 바나나(노랑), 야간 블루베리(남색), 주간·야간 귤
    static final int[] PEOPLE = {4, 2, 3, 11, 10, 1, 8, 7};   // 사람에게 줄 색 (근무 색은 빼고)

    static int hex(int id) { return id > 0 && id < HEX.length ? HEX[id] : 0; }

    static String owner(Context c) { return Store.p(c).getString("famAccount", "").toLowerCase(); }
    static int myColor(Context c) { return Store.p(c).getInt("famMyColor", 7); }

    static JSONObject map(Context c) {
        try { return new JSONObject(Store.p(c).getString("famColors", "{}")); } catch (Exception e) { return new JSONObject(); }
    }

    /** 그 사람 색 (처음 보는 사람이면 남은 색을 자동으로 정해요) */
    static synchronized int colorOf(Context c, String email) {
        email = email == null ? "" : email.toLowerCase();
        if (email.isEmpty() || email.equals(owner(c))) return myColor(c);
        JSONObject m = map(c);
        if (m.has(email)) return m.optInt(email);
        java.util.Set<Integer> used = new java.util.HashSet<>();
        used.add(myColor(c));
        java.util.Iterator<String> it = m.keys();
        while (it.hasNext()) used.add(m.optInt(it.next()));
        int pick = PEOPLE[m.length() % PEOPLE.length];
        for (int p : PEOPLE) if (!used.contains(p)) { pick = p; break; }
        try { m.put(email, pick); } catch (Exception ignored) {}
        Store.p(c).edit().putString("famColors", m.toString()).apply();
        return pick;
    }

    static void set(Context c, String email, int color) {
        email = email.toLowerCase();
        if (email.equals(owner(c))) { Store.p(c).edit().putInt("famMyColor", color).apply(); return; }
        JSONObject m = map(c);
        try { m.put(email, color); } catch (Exception ignored) {}
        Store.p(c).edit().putString("famColors", m.toString()).apply();
    }

    /** 다음 색 (근무 색은 건너뛰어요) */
    static int next(int cur) {
        for (int i = 0; i < PEOPLE.length; i++) if (PEOPLE[i] == cur) return PEOPLE[(i + 1) % PEOPLE.length];
        return PEOPLE[0];
    }

    static String who(Context c, String email) {
        if (email == null || email.isEmpty() || email.equalsIgnoreCase(owner(c))) return "나";
        String nm = Store.p(c).getString("famName." + email.toLowerCase(), "");
        if (!nm.isEmpty()) return nm;
        int at = email.indexOf('@');
        return at > 0 ? email.substring(0, at) : email;
    }

    static int shiftColor(String title) {
        if (title.contains("·")) return BOTH;
        if (title.startsWith("야간")) return NIGHT;
        return DAY;
    }
}
