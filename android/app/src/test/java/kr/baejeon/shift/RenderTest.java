package kr.baejeon.shift;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 위젯 그림 미리보기 PNG를 만들어요 (CI에서 확인용). 실제 근무 데이터가 아닌 예시 데이터예요. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class RenderTest {
    static final String[] PATTERN = {"주", "주", "휴", "야", "야", "비", "휴", "휴"};

    static String sample() throws Exception {
        int ref = (int) (java.time.LocalDate.parse("2026-10-21").toEpochDay());
        int t = ShiftData.todayNum();
        JSONObject days = new JSONObject();
        for (int n = t - 45; n <= t + 80; n++) {
            JSONObject d = new JSONObject();
            String c = PATTERN[Math.floorMod(5 + n - ref, 8)];
            d.put("c", c);
            if (n == t + 1) { d.put("c", "야"); d.put("ch", 1); }
            if (n == t + 9) d.put("c", "연");
            if (n == t + 2 || n == t) d.put("n", "아이 학교 행사");
            if (n == t + 12 && c.equals("휴")) { d.put("cv", "야"); d.put("ch", 1); }
            if (n == t + 5 && c.equals("주")) { d.put("cv", "야"); d.put("ch", 1); }
            if (n >= t - 1 && n <= t + 7 && (c.equals("주") || c.equals("야"))) d.put("m", new JSONArray().put("홍길동"));
            days.put(ShiftData.ymd(n), d);
        }
        return new JSONObject().put("v", 1).put("name", "김준수").put("days", days).toString();
    }

    static Map<Integer, List<CalendarReader.Ev>> evs() {
        int t = ShiftData.todayNum();
        Map<Integer, List<CalendarReader.Ev>> m = new HashMap<>();
        add(m, t, "저녁 약속", "19:00");
        add(m, t + 3, "치과", "15:00");
        add(m, t + 5, "아이 학예회", "");
        add(m, t + 8, "결혼식", "12:00");
        add(m, t + 8, "부모님 댁", "");
        add(m, t + 8, "세차", "");
        add(m, t + 12, "건강검진", "08:30");
        return m;
    }
    static void add(Map<Integer, List<CalendarReader.Ev>> m, int n, String title, String time) {
        m.computeIfAbsent(n, k -> new ArrayList<>()).add(new CalendarReader.Ev(title, time.isEmpty(), 0, time));
    }

    void shot(String file, String kind, float w, float h, int opacity, int text, int chip, boolean dark, int corner, int wall, boolean linked) throws Exception {
        Store.Cfg f = new Store.Cfg();
        f.opacity = opacity; f.text = text; f.chip = chip; f.corner = corner; f.weekMode = 0;
        ShiftData d = linked ? ShiftData.parse(sample()) : null;
        Bitmap bm = Renderer.make(kind, w, h, 3f, new Renderer.St(f, dark), d, evs(), f, false);
        int pad = 48;
        Bitmap out = Bitmap.createBitmap(bm.getWidth() + pad * 2, bm.getHeight() + pad * 2, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        c.drawColor(wall);
        c.drawBitmap(bm, pad, pad, null);
        File dir = new File("build/previews");
        dir.mkdirs();
        try (FileOutputStream o = new FileOutputStream(new File(dir, file))) { out.compress(Bitmap.CompressFormat.PNG, 100, o); }
    }

    @Test
    public void render() throws Exception {
        int gray = 0xFF33414D, light = 0xFFDDE2E7;
        shot("month_light.png", "month", 360, 380, 100, 2, 2, false, 26, gray, true);
        shot("month_xl.png", "month", 360, 380, 100, 3, 3, false, 26, gray, true);
        shot("week_xl.png", "week", 360, 92, 100, 3, 3, false, 22, gray, true);
        shot("today_xl.png", "today", 360, 184, 100, 3, 3, false, 26, gray, true);
        shot("month_dark40.png", "month", 360, 380, 40, 1, 1, true, 26, Color.rgb(79, 95, 69), true);
        shot("month_clear_light.png", "month", 360, 380, 0, 1, 1, false, 0, light, true);
        shot("today_dark65.png", "today", 360, 184, 65, 2, 1, true, 26, gray, true);
        shot("today_light.png", "today", 360, 184, 100, 1, 1, false, 26, gray, true);
        shot("week_light.png", "week", 360, 92, 100, 1, 1, false, 22, gray, true);
        shot("week_dark.png", "week", 360, 92, 100, 1, 1, true, 22, light, true);
        shot("mini_light.png", "mini", 172, 172, 100, 1, 1, false, 26, gray, true);
        shot("mini_dark.png", "mini", 172, 172, 100, 2, 2, true, 8, gray, true);
        shot("unlinked.png", "today", 360, 184, 100, 1, 1, false, 26, gray, false);
    }
}
