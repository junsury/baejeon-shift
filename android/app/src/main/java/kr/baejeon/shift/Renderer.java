package kr.baejeon.shift;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.text.TextPaint;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 위젯 그림을 그려요. 단위는 dp(디자인 시안의 px과 같음)로 계산하고 마지막에 화면 밀도만큼 키워요.
 * 색·크기는 위젯 디자인 시안(달력형/오늘·이번 주형/주간형/작은 위젯)과 같게 맞췄어요.
 */
final class Renderer {

    /** 그림 그릴 때 쓰는 색·크기 묶음 */
    static final class St {
        boolean dark; float a; float t; float c; float r;
        int fg, muted, bg, soft, sat, ringBg, ring, evFg, evBg, dot, change;
        St(Store.Cfg f, boolean dark) {
            this.dark = dark;
            a = Math.max(0, Math.min(100, f.opacity)) / 100f;
            t = tScale(f.text);
            c = cScale(f.chip);
            r = Math.max(0, Math.min(32, f.corner));
            fg = dark ? 0xFFEEF1F4 : 0xFF161C24;
            muted = dark ? 0xFFC6CED7 : 0xFF4F5A67;
            bg = dark ? Color.argb(Math.round(a * 255), 31, 35, 40) : Color.argb(Math.round(a * 255), 255, 255, 255);
            soft = dark ? 0x1FFFFFFF : 0x14141E28;
            sat = dark ? 0xFF7FB0FF : 0xFF2F6FD6;
            ringBg = dark ? 0xFF1F2328 : 0xFFFFFFFF;
            ring = dark ? 0xFF8CBCFF : 0xFF1D5D8F;
            change = dark ? 0xFF7FB0FF : 0xFF1D5D8F;
            evFg = dark ? 0xFFEFE6FF : 0xFF4A2F93;
            evBg = dark ? Color.argb(82, 160, 130, 240) : Color.argb(Math.round(Math.max(a, 0.85f) * 255), 239, 233, 251);
            dot = dark ? 0xFFC7B3F6 : 0xFF6B4CC0;
        }
        private static int clamp(int i) { return Math.max(0, Math.min(3, i)); }
        boolean shadow() { return a < 0.6f; }
        int[] chip(String code) {   // {배경, 글자}
            if (code == null) return new int[]{Color.TRANSPARENT, muted};
            switch (code) {
                case "주": return new int[]{0xFFF4AD00, 0xFF2B1D00};
                case "야": return new int[]{dark ? 0xFF4C5BE0 : 0xFF2B3990, 0xFFFFFFFF};
                case "비": return new int[]{dark ? 0xFF55468A : 0xFFE5DDF5, dark ? 0xFFF0E9FF : 0xFF5A3D99};
                case "연": return new int[]{0xFF1F9D55, 0xFFFFFFFF};
                case "지원": return new int[]{dark ? 0xFF3CC4D1 : 0xFF0D8794, dark ? 0xFF04262A : 0xFFFFFFFF};
                case "T": return new int[]{dark ? 0xFF163A3F : 0xFFD2EDF0, dark ? 0xFF6DD3DD : 0xFF0B6670};
                case "24": return new int[]{0xFFF4AD00, 0xFFFFFFFF};
                case "휴": return new int[]{dark ? 0x29FFFFFF : (a < 0.6f ? soft : Color.TRANSPARENT), dark ? 0xFFE6EBF0 : 0xFF6B7581};
                default: return new int[]{Color.TRANSPARENT, muted};
            }
        }
    }

    /** 위젯 밖(일정 창)에서 쓰는 근무 칩 색 {배경, 글자} */
    static int[] chipColors(String code, boolean dark) { return new St(new Store.Cfg(), dark).chip(code); }

    static final int RED = 0xFFD92D20, RED_TXT = 0xFFE0362A;

    final Canvas cv; final St s; final float W, H;
    final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    final TextPaint tp = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    final Typeface REG = Typeface.create("sans-serif", Typeface.NORMAL);
    final Typeface BOLD = Typeface.create("sans-serif", Typeface.BOLD);
    final Typeface MED = Typeface.create("sans-serif-medium", Typeface.NORMAL);
    final float fs;   // 1em

    private Renderer(Canvas cv, St s, float w, float h) {
        this.cv = cv; this.s = s; this.W = w; this.H = h;
        stroke.setStyle(Paint.Style.STROKE);
        fs = 10f * s.t;
    }

    /** w,h = dp, scale = 화면 밀도 */
    static Bitmap make(String kind, float w, float h, float scale, St s, ShiftData d, Map<Integer, List<CalendarReader.Ev>> evs, Store.Cfg f, boolean unlinked) {
        int pw = Math.max(1, Math.round(w * scale)), ph = Math.max(1, Math.round(h * scale));
        Bitmap bm = Bitmap.createBitmap(pw, ph, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(bm);
        cv.scale(scale, scale);
        Renderer r = new Renderer(cv, s, w, h);
        r.background();
        if (d == null) { r.notLinked(unlinked); return bm; }
        if (evs == null || !f.events) evs = Collections.emptyMap();
        evs = withMemos(d, evs);
        switch (kind) {
            case "month": r.month(d, evs, f.monthOffset); break;
            case "today": r.today(d, evs); break;
            case "week": r.week(d, evs, f.weekMode); break;
            default: r.mini(d); break;
        }
        return bm;
    }

    /** 근무표 앱에 적은 내 메모를 그날 맨 앞에 끼워 넣어요 */
    static Map<Integer, List<CalendarReader.Ev>> withMemos(ShiftData d, Map<Integer, List<CalendarReader.Ev>> evs) {
        Map<Integer, List<CalendarReader.Ev>> m = new java.util.HashMap<>();
        for (Map.Entry<Integer, List<CalendarReader.Ev>> e : evs.entrySet()) m.put(e.getKey(), new java.util.ArrayList<>(e.getValue()));
        int t = ShiftData.todayNum();
        for (int n = t - 50; n <= t + 100; n++) {
            String memo = d.memo(n);
            if (memo.isEmpty()) continue;
            CalendarReader.Ev ev = new CalendarReader.Ev(memo.replace('\n', ' '), true, 0, "");
            ev.memo = true;
            List<CalendarReader.Ev> l = m.get(n);
            if (l == null) { l = new java.util.ArrayList<>(); m.put(n, l); }
            l.add(0, ev);
        }
        return m;
    }

    int memoBg() { return s.dark ? Color.argb(80, 244, 173, 0) : Color.argb(Math.round(Math.max(s.a, 0.85f) * 255), 255, 240, 196); }
    int memoFg() { return s.dark ? 0xFFFFE2A0 : 0xFF6B4A00; }

    // ---------- 공통 그리기 도구 ----------
    void background() {
        fill.setColor(s.bg);
        cv.drawRoundRect(new RectF(0, 0, W, H), s.r, s.r, fill);
    }

    void rr(float l, float t, float r, float b, float rad, int color) {
        if (Color.alpha(color) == 0) return;
        fill.setColor(color);
        cv.drawRoundRect(new RectF(l, t, r, b), rad, rad, fill);
    }

    void text(String str, float x, float baseline, float size, Typeface tf, int color, boolean shadow) {
        tp.setTypeface(tf); tp.setTextSize(size); tp.setColor(color); tp.setTextAlign(Paint.Align.LEFT);
        tp.setLetterSpacing(0);
        if (shadow && s.shadow()) tp.setShadowLayer(3f, 0, 1f, s.dark ? 0xBF000000 : 0xE6FFFFFF);
        else tp.clearShadowLayer();
        cv.drawText(str, x, baseline, tp);
    }

    float width(String str, float size, Typeface tf) { tp.setTypeface(tf); tp.setTextSize(size); tp.setLetterSpacing(0); return tp.measureText(str); }

    String fit(String str, float size, Typeface tf, float max) {
        tp.setTypeface(tf); tp.setTextSize(size); tp.setLetterSpacing(0);
        return TextUtils.ellipsize(str, tp, Math.max(0, max), TextUtils.TruncateAt.END).toString();
    }

    void center(String str, float cx, float cy, float size, Typeface tf, int color, boolean shadow) {
        tp.setTypeface(tf); tp.setTextSize(size); tp.setColor(color); tp.setTextAlign(Paint.Align.CENTER);
        if (shadow && s.shadow()) tp.setShadowLayer(3f, 0, 1f, s.dark ? 0xBF000000 : 0xE6FFFFFF);
        else tp.clearShadowLayer();
        Paint.FontMetrics fm = tp.getFontMetrics();
        cv.drawText(str, cx, cy - (fm.ascent + fm.descent) / 2f, tp);
        tp.setTextAlign(Paint.Align.LEFT);
        tp.setLetterSpacing(0);
    }

    /** 근무 칩 (주/야/비/휴/휴가/24/지/T) */
    void chip(String code, float l, float t, float w, float h, float rad, float fontPx, boolean ringed) {
        RectF box = new RectF(l, t, l + w, t + h);
        if (ringed) {
            stroke.setStrokeWidth(1.5f); stroke.setColor(s.ringBg);
            RectF o1 = new RectF(box); o1.inset(-0.75f, -0.75f);
            cv.drawRoundRect(o1, rad + 0.75f, rad + 0.75f, stroke);
            stroke.setColor(s.ring);
            RectF o2 = new RectF(box); o2.inset(-2.25f, -2.25f);
            cv.drawRoundRect(o2, rad + 2.25f, rad + 2.25f, stroke);
        }
        int[] col = s.chip(code);
        if ("24".equals(code)) {   // 주간색/야간색 대각선 반반
            cv.save();
            Path clip = new Path(); clip.addRoundRect(box, rad, rad, Path.Direction.CW);
            cv.clipPath(clip);
            fill.setColor(0xFFF4AD00); cv.drawRect(box, fill);
            Path tri = new Path(); tri.moveTo(box.right, box.top); tri.lineTo(box.right, box.bottom); tri.lineTo(box.left, box.bottom); tri.close();
            fill.setColor(s.dark ? 0xFF4C5BE0 : 0xFF2B3990); cv.drawPath(tri, fill);
            cv.restore();
            tp.setShadowLayer(3f, 0, 0, 0xE6000000);
        } else {
            fill.setColor(col[0]);
            if (Color.alpha(col[0]) > 0) cv.drawRoundRect(box, rad, rad, fill);
        }
        String lab = ShiftData.label(code);
        if (lab.isEmpty()) return;
        float size = lab.length() > 1 ? fontPx * 0.8f : fontPx;
        tp.setTypeface("휴".equals(code) ? MED : BOLD); tp.setTextSize(size);
        tp.setLetterSpacing(lab.length() > 1 ? -0.06f : 0);
        while (tp.measureText(lab) > w - 1 && size > 5) { size -= 0.5f; tp.setTextSize(size); }
        tp.setColor(col[1]); tp.setTextAlign(Paint.Align.CENTER);
        if (!"24".equals(code)) tp.clearShadowLayer();
        Paint.FontMetrics fm = tp.getFontMetrics();
        cv.drawText(lab, box.centerX(), box.centerY() - (fm.ascent + fm.descent) / 2f, tp);
        tp.clearShadowLayer(); tp.setTextAlign(Paint.Align.LEFT); tp.setLetterSpacing(0);
    }

    void todayBox(float l, float t, float r, float b, float rad) {
        rr(l, t, r, b, rad, Color.argb(Math.round((s.dark ? 0.18f : 0.08f) * 255), 217, 45, 32));
        stroke.setStrokeWidth(2f); stroke.setColor(RED);
        cv.drawRoundRect(new RectF(l + 1, t + 1, r - 1, b - 1), Math.max(0, rad - 1), Math.max(0, rad - 1), stroke);
    }

    void dot(float cx, float cy, float rad) { fill.setColor(s.dot); cv.drawCircle(cx, cy, rad, fill); }

    /** 일정 점(보라)·메모 점(노랑) */
    void dots(List<CalendarReader.Ev> el, float cx, float cy, float rad) {
        if (el == null || el.isEmpty()) return;
        boolean memo = false, ev = false;
        for (CalendarReader.Ev e : el) { if (e.memo) memo = true; else ev = true; }
        if (memo && ev) { fill.setColor(0xFFF4AD00); cv.drawCircle(cx - rad * 1.4f, cy, rad, fill); fill.setColor(s.dot); cv.drawCircle(cx + rad * 1.4f, cy, rad, fill); }
        else if (memo) { fill.setColor(0xFFF4AD00); cv.drawCircle(cx, cy, rad, fill); }
        else dot(cx, cy, rad);
    }

    int dayColor(ShiftData d, int n, int normal) {
        int w = ShiftData.dow(n);
        if (w == 0 || d.holiday(n)) return RED_TXT;
        if (w == 6) return s.sat;
        return normal;
    }

    float asc(float size, Typeface tf) { tp.setTypeface(tf); tp.setTextSize(size); return -tp.getFontMetrics().ascent; }
    float mid(float cy, float size, Typeface tf) { tp.setTypeface(tf); tp.setTextSize(size); Paint.FontMetrics fm = tp.getFontMetrics(); return cy - (fm.ascent + fm.descent) / 2f; }
    float lineH(float size) { return size * 1.25f; }

    // ---------- 연결 안 됨 ----------
    void notLinked(boolean unlinked) {
        float y = H / 2f - fs * 1.6f;
        String a = unlinked ? "위젯 연결이 끊겼어요" : "근무표 위젯";
        String b = "근무표 앱을 열고";
        String c = "👤 메뉴 → [갤럭시 위젯 연결]";
        center(a, W / 2f, y, fs * 1.5f, BOLD, s.fg, true);
        center(b, W / 2f, y + fs * 2.1f, fs * 1.2f, REG, s.muted, true);
        center(c, W / 2f, y + fs * 3.7f, fs * 1.2f, BOLD, s.change, true);
    }

    // ---------- 달력형 4×4 ----------
    /** 달력 위젯 칸 계산: {first, nextFirst, start(첫 칸 날짜), weeks} — 위젯 터치 칸도 같은 값 사용 */
    static int[] monthGeom(int offset) {
        int today = ShiftData.todayNum();
        java.util.Calendar c = ShiftData.cal(today);
        c.set(java.util.Calendar.DAY_OF_MONTH, 1);
        c.add(java.util.Calendar.MONTH, offset);
        int first = (int) Math.floorDiv(c.getTimeInMillis(), 86400000L);
        c.add(java.util.Calendar.MONTH, 1);
        int nextFirst = (int) Math.floorDiv(c.getTimeInMillis(), 86400000L);
        int start = first - ShiftData.dow(first);
        return new int[]{first, nextFirst, start, (int) Math.ceil((nextFirst - start) / 7.0)};
    }
    /** 글자·아이콘 크기 단계: 작게 · 보통 · 크게 · 아주 크게 */
    static float tScale(int i) { return new float[]{0.95f, 1.15f, 1.35f, 1.6f}[Math.max(0, Math.min(3, i))]; }
    static float cScale(int i) { return new float[]{0.95f, 1.15f, 1.35f, 1.6f}[Math.max(0, Math.min(3, i))]; }
    /** 머리줄·요일 줄 글자는 너무 커지지 않게 */
    static float headScale(float t) { return Math.min(t, 1.25f); }
    /** 달력 칸 영역의 위쪽 여백(dp) — 위젯 터치 칸도 이 값을 써요 */
    static float monthGridTop(float t) { return 14 + 28 + 6 + 10f * headScale(t) * 1.3f + 6; }

    void month(ShiftData d, Map<Integer, List<CalendarReader.Ev>> evs, int offset) {
        float k = s.r / 26f;
        float padL = 12, padT = 14, padB = 12;
        int today = ShiftData.todayNum();
        java.util.Calendar c = ShiftData.cal(today);
        c.set(java.util.Calendar.DAY_OF_MONTH, 1);
        c.add(java.util.Calendar.MONTH, offset);
        int first = (int) Math.floorDiv(c.getTimeInMillis(), 86400000L);
        int mon = c.get(java.util.Calendar.MONTH) + 1, year = c.get(java.util.Calendar.YEAR);
        c.add(java.util.Calendar.MONTH, 1);
        int nextFirst = (int) Math.floorDiv(c.getTimeInMillis(), 86400000L);
        int start = first - ShiftData.dow(first);
        int weeks = (int) Math.ceil((nextFirst - start) / 7.0);

        // 머리줄
        float hh = 28, cy = padT + hh / 2f;
        float x = padL + 4;
        String title = (year != ShiftData.cal(today).get(java.util.Calendar.YEAR) ? (year % 100) + "년 " : "") + mon + "월";
        float hf = 10f * headScale(s.t);
        float tsz = hf * 2f;
        text(title, x, mid(cy, tsz, BOLD), tsz, BOLD, s.fg, true);
        x += width(title, tsz, BOLD) + 8;
        float nsz = hf * 1.2f;
        // 버튼 ‹ ›
        float bs = 28, bR = s.r < 8 ? 6 : 14;
        float nextL = W - padL - bs, prevL = nextL - 8 - bs;
        rr(prevL, cy - bs / 2, prevL + bs, cy + bs / 2, bR, s.soft);
        rr(nextL, cy - bs / 2, nextL + bs, cy + bs / 2, bR, s.soft);
        arrow(prevL + bs / 2, cy, true);
        arrow(nextL + bs / 2, cy, false);
        // 오늘 pill
        String code0 = d.code(today);
        String pill = "오늘 " + (code0 == null ? "-" : ShiftData.shortName(code0));
        float psz = hf * 1.1f, pw = width(pill, psz, BOLD) + 16;
        float pr = prevL - 8, pl = pr - pw;
        float nameMax = pl - 8 - x;
        if (nameMax > 20) text(fit(d.name, nsz, REG, nameMax), x, mid(cy, nsz, REG), nsz, REG, s.muted, true);
        if (pl > x) {
            rr(pl, cy - psz * 0.8f, pr, cy + psz * 0.8f, 999, RED);
            center(pill, (pl + pr) / 2f, cy, psz, BOLD, 0xFFFFFFFF, false);
        }

        // 요일 줄
        float top = padT + hh + 6;
        float gw = W - padL * 2, cw = gw / 7f;
        float wsz = hf;
        for (int i = 0; i < 7; i++) {
            int col = i == 0 ? RED : i == 6 ? s.sat : s.muted;
            center(ShiftData.WD[i], padL + i * cw + cw / 2f, top + wsz * 0.6f, wsz, BOLD, col, true);
        }
        top += wsz * 1.3f + 6;
        float gh = H - padB - top;
        float rh = gh / weeks;
        // 칸 구분선 (가로·세로)
        int line = s.dark ? 0x33FFFFFF : 0x24101820;
        stroke.setStrokeWidth(1f); stroke.setColor(line);
        for (int wk = 0; wk <= weeks; wk++) cv.drawLine(padL, top + wk * rh, W - padL, top + wk * rh, stroke);
        for (int i = 1; i < 7; i++) cv.drawLine(padL + i * cw, top, padL + i * cw, top + weeks * rh, stroke);
        float numSz = fs * 1.25f, evSz = fs * 1.0f;
        float csz = Math.round(20 * s.c);
        for (int wk = 0; wk < weeks; wk++) {
            for (int i = 0; i < 7; i++) {
                int n = start + wk * 7 + i;
                boolean out = n < first || n >= nextFirst;
                float l = padL + i * cw + 1, t = top + wk * rh + 1, r = l + cw - 2, b = t + rh - 2;
                int save = out ? cv.saveLayerAlpha(new RectF(l - 4, t - 4, r + 4, b + 4), 102) : cv.save();
                if (n == today) todayBox(l, t, r, b, 6 * k);
                String code = d.code(n);
                int ncol = dayColor(d, n, s.fg);
                if (ShiftData.dow(n) == 0 || d.holiday(n)) ncol = RED_TXT;
                String num = String.valueOf(ShiftData.dayOfMonth(n));
                float numW = width(num, numSz, BOLD);
                float ct = t + 3;
                float chH = Math.min(csz, (rh - 6) * 0.48f);
                String lab = code == null ? "" : ShiftData.label(code);
                float chW = Math.max(chH, lab.length() > 1 ? chH * 1.25f : chH);
                chW = Math.min(chW, cw - 6);
                boolean side = numW + 4 + chW <= (r - l) - 4;     // 숫자 옆에 칩이 들어가면 한 줄, 아니면 두 줄
                float rowH = Math.max(chH, numSz * 1.05f);
                text(num, l + 3, mid(ct + (side ? rowH : numSz * 1.1f) / 2f, numSz, BOLD), numSz, BOLD, ncol, true);
                float belowTop;
                if (code != null && !"없".equals(code)) {
                    if (side) { chip(code, r - 2 - chW, ct + (rowH - chH) / 2f, chW, chH, 5 * s.c * k, 11 * s.c, !out && d.changed(n)); belowTop = ct + rowH; }
                    else { float cy2 = ct + numSz * 1.1f + 2; chip(code, (l + r) / 2f - chW / 2f, cy2, chW, chH, 5 * s.c * k, 11 * s.c, !out && d.changed(n)); belowTop = cy2 + chH; }
                } else belowTop = ct + (side ? rowH : numSz * 1.1f);
                // 일정
                List<CalendarReader.Ev> list = out ? null : evs.get(n);
                if (list != null && !list.isEmpty()) {
                    float ey = belowTop + 3, eh = lineH(evSz) + 2;
                    int shown = 0;
                    cv.save();
                    cv.clipRect(l + 2, t, r - 2, b);
                    for (CalendarReader.Ev e : list) {
                        boolean last = ey + eh * 2 + 2 > b - 2 && shown < list.size() - 1;
                        if (ey + eh > b - 2) break;
                        rr(l + 2, ey, r - 2, ey + eh, 3, e.memo ? memoBg() : s.evBg);
                        float tw = cw - 8;
                        if (last) {   // 마지막 줄: 제목 + 남은 개수
                            String more = "+" + (list.size() - shown - 1);
                            float mw = width(more, evSz, BOLD);
                            text(more, r - 4 - mw, ey + 1 + asc(evSz, BOLD), evSz, BOLD, s.evFg, false);
                            tw -= mw + 2;
                        }
                        text(fit(e.title, evSz, MED, tw), l + 4, ey + 1 + asc(evSz, MED), evSz, MED, e.memo ? memoFg() : s.evFg, false);
                        ey += eh + 2; shown++;
                        if (last) break;
                    }
                    cv.restore();
                }
                cv.restoreToCount(save);
            }
        }
    }

    void arrow(float cx, float cy, boolean left) {
        stroke.setStrokeWidth(2.4f * 14f / 24f); stroke.setColor(s.fg);
        stroke.setStrokeCap(Paint.Cap.ROUND); stroke.setStrokeJoin(Paint.Join.ROUND);
        Path p = new Path();
        float u = 14f / 24f;
        if (left) { p.moveTo(cx + 3 * u, cy - 6 * u); p.lineTo(cx - 3 * u, cy); p.lineTo(cx + 3 * u, cy + 6 * u); }
        else { p.moveTo(cx - 3 * u, cy - 6 * u); p.lineTo(cx + 3 * u, cy); p.lineTo(cx - 3 * u, cy + 6 * u); }
        cv.drawPath(p, stroke);
        stroke.setStrokeCap(Paint.Cap.BUTT);
    }

    // ---------- 오늘·이번 주형 4×2 ----------
    void today(ShiftData d, Map<Integer, List<CalendarReader.Ev>> evs) {
        float k = s.r / 26f;
        float pad = 14, padB = 12;
        int n0 = ShiftData.todayNum();
        // 아래 6일 줄 높이
        float dsz = fs, chH = Math.round(26 * s.c), chW = Math.round(30 * s.c);
        float rowH = lineH(dsz) + 3 + chH + 3 + 5;
        float bottomTop = H - padB - rowH;
        float topB = bottomTop - 10;
        float colsW = W - pad * 2;
        float leftW = (colsW - 12) * 1.1f / 2f;
        float panelL = pad + leftW + 12, panelR = W - pad;

        // 왼쪽: 큰 칩 + 글
        String code = d.code(n0);
        float big = Math.min(Math.round(52 * s.c), topB - pad);
        float midY = (pad + topB) / 2f;
        chip(code == null ? "휴" : code, pad, midY - big / 2, big, big, 14 * s.c * k, 24 * s.c, d.changed(n0));
        float tx = pad + big + 10, tmax = pad + leftW - tx;
        float s1 = fs * 1.1f, s2 = fs * 1.7f, s3 = fs * 1.2f;
        float blockH = lineH(s1) + lineH(s2) + lineH(s3);
        float y = midY - blockH / 2f;
        text(fit("오늘 " + ShiftData.mdw(n0), s1, BOLD, tmax), tx, y + asc(s1, BOLD), s1, BOLD, RED_TXT, true);
        y += lineH(s1);
        String nm = ShiftData.longName(code);
        String part = d.part(n0);
        if (!part.isEmpty()) nm = "시간휴가 " + part;
        float sz2 = s2;
        while (width(nm, sz2, BOLD) > tmax && sz2 > fs * 1.2f) sz2 -= 0.5f;
        text(fit(nm, sz2, BOLD, tmax), tx, y + asc(s2, BOLD), sz2, BOLD, s.fg, true);
        y += lineH(s2);
        List<String> mates = d.mates(n0);
        String with = TextUtils.join(", ", mates);
        if (!with.isEmpty()) {
            float wx = tx;
            text("같이 ", wx, y + asc(s3, REG), s3, REG, s.muted, true);
            wx += width("같이 ", s3, REG);
            text(fit(with, s3, BOLD, tx + tmax - wx), wx, y + asc(s3, BOLD), s3, BOLD, s.fg, true);
        }

        // 오른쪽 패널
        rr(panelL, pad, panelR, topB, 14 * k, s.soft);
        float px = panelL + 10, pmax = panelR - 10 - px, py = pad + 8;
        float h1 = fs, h2 = fs * 1.2f, h3 = fs * 1.1f;
        text("오늘 일정 · 메모", px, py + asc(h1, BOLD), h1, BOLD, s.muted, true);
        py += lineH(h1) + 4;
        float lastY = topB - 8 - lineH(h3);
        List<CalendarReader.Ev> list = evs.get(n0);
        if (list == null || list.isEmpty()) {
            if (py + lineH(h2) <= lastY) text("일정 없음", px, py + asc(h2, REG), h2 * 0.92f, REG, s.muted, true);
        } else {
            for (int i = 0; i < list.size(); i++) {
                if (py + lineH(h2) > lastY) break;
                boolean more = py + lineH(h2) * 2 + 4 > lastY && i < list.size() - 1;
                CalendarReader.Ev ev = list.get(i);
                String str = (ev.memo ? "📝 " : "") + (more ? ev.text() + "  +" + (list.size() - i - 1) : ev.text());
                text(fit(str, h2, MED, pmax), px, py + asc(h2, MED), h2, MED, ev.memo ? memoFg() : s.evFg, true);
                py += lineH(h2) + 4;
                if (more) break;
            }
        }
        String c1 = d.code(n0 + 1);
        String tm = "내일 " + (c1 == null ? "-" : ShiftData.shortName(c1));
        float ty = topB - 8 - lineH(h3) + asc(h3, REG);
        text(tm, px, ty, h3, REG, s.muted, true);
        if (d.changed(n0 + 1)) {
            float wx = px + width(tm, h3, REG);
            text(" · ", wx, ty, h3, REG, s.muted, true);
            wx += width(" · ", h3, REG);
            text("변경", wx, ty, h3, BOLD, s.change, true);
        }

        // 앞으로 6일
        float gw = W - pad * 2, cw = (gw - 5 * 4) / 6f;
        for (int i = 0; i < 6; i++) {
            int n = n0 + 1 + i;
            float cx = pad + i * (cw + 4) + cw / 2f;
            float yy = bottomTop;
            center(ShiftData.dayOfMonth(n) + " " + ShiftData.WD[ShiftData.dow(n)], cx, yy + lineH(dsz) / 2f, dsz, BOLD, dayColor(d, n, s.muted), true);
            yy += lineH(dsz) + 3;
            String cd = d.code(n);
            if (cd != null && !"없".equals(cd)) chip(cd, cx - chW / 2f, yy, chW, chH, 7 * s.c * k, 13 * s.c, d.changed(n));
            yy += chH + 3;
            List<CalendarReader.Ev> el = evs.get(n);
            dots(el, cx, yy + 2.5f, 2.5f);
        }
    }

    // ---------- 주간형 4×1 ----------
    void week(ShiftData d, Map<Integer, List<CalendarReader.Ev>> evs, int mode) {
        float kr = s.r / 22f;
        float padT = 8, padX = 10, padB = 6;
        int n0 = ShiftData.todayNum();
        int start = mode == 1 ? n0 : n0 - Math.floorMod(ShiftData.dow(n0) - 1, 7);
        String title = mode == 1 ? "앞으로 7일" : "이번 주";
        String range = ShiftData.md(start) + "–" + ShiftData.md(start + 6);
        float s1 = fs * 1.2f, s2 = fs * 1.05f;
        float base = padT + asc(s1, BOLD);
        float x = padX + 2;
        text(title, x, base, s1, BOLD, s.fg, true);
        x += width(title, s1, BOLD) + 6;
        text(range, x, base, s2, REG, s.muted, true);
        x += width(range, s2, REG) + 8;
        float nw = width(d.name, s2, REG);
        if (W - padX - 2 - nw > x) text(d.name, W - padX - 2 - nw, base, s2, REG, s.muted, true);

        float top = padT + lineH(s1) + 4, bot = H - padB;
        float gw = W - padX * 2, cw = (gw - 6 * 3) / 7f;
        float dsz = fs, chH = Math.round(24 * s.c), chW = Math.min(Math.round(30 * s.c), cw - 4);
        float inner = lineH(dsz) + 3 + chH + 3 + 4;
        float avail = bot - top;
        if (inner > avail) { chH = Math.max(12, chH - (inner - avail)); inner = lineH(dsz) + 3 + chH + 3 + 4; }
        stroke.setStrokeWidth(1f); stroke.setColor(s.dark ? 0x33FFFFFF : 0x24101820);
        for (int i = 1; i < 7; i++) { float lx = padX + i * (cw + 3) - 1.5f; cv.drawLine(lx, top + 4, lx, bot - 4, stroke); }
        for (int i = 0; i < 7; i++) {
            int n = start + i;
            float l = padX + i * (cw + 3), r = l + cw;
            if (n == n0) todayBox(l, top, r, bot, 10 * kr);
            float cx = (l + r) / 2f;
            float y = top + (avail - inner) / 2f;
            int col = dayColor(d, n, n == n0 ? s.fg : s.muted);
            center(ShiftData.dayOfMonth(n) + " " + ShiftData.WD[ShiftData.dow(n)], cx, y + lineH(dsz) / 2f, dsz, BOLD, col, true);
            y += lineH(dsz) + 3;
            String cd = d.code(n);
            if (cd != null && !"없".equals(cd)) chip(cd, cx - chW / 2f, y, chW, chH, 7 * s.c * kr, 13 * s.c, d.changed(n));
            y += chH + 3;
            List<CalendarReader.Ev> el = evs.get(n);
            dots(el, cx, y + 2, 2);
        }
    }

    // ---------- 작은 위젯 2×2 ----------
    void mini(ShiftData d) {
        float k = s.r / 26f;
        float pad = 14;
        int n0 = ShiftData.todayNum();
        float s1 = fs * 1.1f;
        float y = pad;
        text(fit("오늘 " + ShiftData.mdw(n0), s1, BOLD, W - pad * 2), pad, y + asc(s1, BOLD), s1, BOLD, RED_TXT, true);
        y += lineH(s1) + 8;
        String code = d.code(n0);
        float h3 = fs * 1.1f;
        float panelH = Math.max(Math.round(22 * s.c), lineH(h3)) + 14;
        float panelT = H - pad - panelH;
        float big = Math.min(Math.round(46 * s.c), panelT - 8 - y);
        chip(code == null ? "휴" : code, pad, y, big, big, 13 * s.c * k, 22 * s.c, d.changed(n0));
        String[] two = ShiftData.twoLine(code);
        String part = d.part(n0);
        if (!part.isEmpty()) two = new String[]{"시간휴가", part};
        float tx = pad + big + 10, tmax = W - pad - tx;
        float a1 = fs * 1.6f, a2 = fs * 1.2f;
        float bh = lineH(a1) + (two[1].isEmpty() ? 0 : lineH(a2));
        float ty = y + big / 2f - bh / 2f;
        float sz = a1;
        while (width(two[0], sz, BOLD) > tmax && sz > fs) sz -= 0.5f;
        text(fit(two[0], sz, BOLD, tmax), tx, ty + asc(a1, BOLD), sz, BOLD, s.fg, true);
        if (!two[1].isEmpty()) text(fit(two[1], a2, REG, tmax), tx, ty + lineH(a1) + asc(a2, REG), a2, REG, s.muted, true);

        rr(pad, panelT, W - pad, H - pad, 12 * k, s.soft);
        float cy = panelT + panelH / 2f, x = pad + 9;
        text("내일", x, mid(cy, h3, BOLD), h3, BOLD, s.muted, true);
        x += width("내일", h3, BOLD) + 8;
        String c1 = d.code(n0 + 1);
        float cw = Math.round(24 * s.c), ch = Math.round(22 * s.c);
        boolean chg = d.changed(n0 + 1);
        if (c1 != null && !"없".equals(c1)) {
            chip(c1, x, cy - ch / 2f, cw, ch, 6 * s.c * k, 12 * s.c, chg);
            x += cw + 8;
        }
        if (chg) text("변경", x, mid(cy, h3, BOLD), h3, BOLD, s.change, true);
        else if (c1 != null) text(fit(ShiftData.shortName(c1), h3, REG, W - pad - 9 - x), x, mid(cy, h3, REG), h3, REG, s.muted, true);
    }
}
