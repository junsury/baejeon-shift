package kr.baejeon.shift;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.widget.RemoteViews;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/** 근무 데이터를 받아 오고 모든 위젯을 다시 그려요. */
final class WidgetUpdater {
    private WidgetUpdater() {}

    static final String API = "https://asia-northeast3-baejeon-shift.cloudfunctions.net/widgetData?t=";
    static final Class<?>[] KINDS = {MonthWidget.class, TodayWidget.class, WeekWidget.class, MiniWidget.class};

    /** 서버에서 새로 받기 (백그라운드 스레드에서 호출). 성공하면 true */
    static boolean fetch(Context c) {
        String tok = Store.token(c);
        if (tok.isEmpty()) return false;
        HttpURLConnection con = null;
        try {
            con = (HttpURLConnection) new URL(API + tok).openConnection();
            con.setConnectTimeout(7000); con.setReadTimeout(7000);
            int code = con.getResponseCode();
            if (code == 404) { Store.setUnlinked(c, true); Store.setData(c, ""); return false; }
            if (code != 200) return false;
            try (InputStream in = con.getInputStream()) {
                ByteArrayOutputStream bo = new ByteArrayOutputStream();
                byte[] buf = new byte[8192]; int r;
                while ((r = in.read(buf)) > 0) bo.write(buf, 0, r);
                String body = new String(bo.toByteArray(), StandardCharsets.UTF_8);
                if (ShiftData.parse(body) == null) return false;
                Store.setData(c, body);
                try { ShiftSync.sync(c); } catch (Exception ignored) {}
                Store.setUnlinked(c, false);
                return true;
            }
        } catch (Exception e) {
            return false;
        } finally { if (con != null) con.disconnect(); }
    }

    /** 새로 받은 뒤 다시 그리기 — 별도 스레드 */
    static void refreshAsync(Context ctx, Runnable done) {
        final Context c = ctx.getApplicationContext();
        new Thread(() -> {
            try { CalendarReader.syncAllGoogle(c); FamApi.refresh(c); fetch(c); renderAll(c); scheduleMidnight(c); CalUpload.upload(c, false); }
            finally { if (done != null) done.run(); }
        }).start();
    }

    static String kindOf(Context c, int id) {
        AppWidgetProviderInfo info = AppWidgetManager.getInstance(c).getAppWidgetInfo(id);
        if (info == null) return null;
        String n = info.provider.getClassName();
        if (n.endsWith("MonthWidget")) return "month";
        if (n.endsWith("TodayWidget")) return "today";
        if (n.endsWith("WeekWidget")) return "week";
        if (n.endsWith("MiniWidget")) return "mini";
        return null;
    }

    static void renderAll(Context c) {
        AppWidgetManager m = AppWidgetManager.getInstance(c);
        for (Class<?> k : KINDS) for (int id : m.getAppWidgetIds(new ComponentName(c, k))) render(c, m, id);
    }

    static boolean systemDark(Context c) {
        return (c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
    }

    /** 위젯 크기 (dp): 세로 화면 기준 = 최소 너비 × 최대 높이 */
    static float[] sizeOf(Context c, AppWidgetManager m, int id, String kind) {
        Bundle o = m.getAppWidgetOptions(id);
        float w = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0);
        float h = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0);
        float[] def = defSize(kind);
        if (w <= 0 || h <= 0) return def;
        return new float[]{w, h};
    }

    static float[] defSize(String kind) {
        switch (kind) {
            case "month": return new float[]{360, 380};
            case "today": return new float[]{360, 184};
            case "week": return new float[]{360, 92};
            default: return new float[]{172, 172};
        }
    }

    static void render(Context c, AppWidgetManager m, int id) {
        String kind = kindOf(c, id);
        if (kind == null) return;
        Store.Cfg f = Store.cfg(c, id, kind);
        // 달을 넘겨 보고 30분이 지나면 이번 달로 돌아오기
        if (f.monthOffset != 0 && System.currentTimeMillis() - f.offsetAt > 30 * 60000L) { f.monthOffset = 0; Store.setOffset(c, id, 0); }
        ShiftData d = ShiftData.parse(Store.data(c));
        float[] sz = sizeOf(c, m, id, kind);
        float dens = c.getResources().getDisplayMetrics().density;
        // 비트맵 용량 한도 안에 들어가게 (두 장까지 고려)
        float maxPx = 2.2e6f;
        float scale = dens;
        if (sz[0] * sz[1] * scale * scale > maxPx) scale = (float) Math.sqrt(maxPx / (sz[0] * sz[1]));

        Map<Integer, List<CalendarReader.Ev>> evs = null;
        if (f.events && d != null) {
            int n0 = ShiftData.todayNum();
            evs = CalendarReader.read(c, n0 - 50 + f.monthOffset * 31, n0 + 50 + f.monthOffset * 31, Store.calExcluded(c));
        }
        RemoteViews rv;
        if (f.theme == 2 && Build.VERSION.SDK_INT >= 31) {
            RemoteViews light = views(c, id, kind, Renderer.make(kind, sz[0], sz[1], scale, new Renderer.St(f, false), d, evs, f, Store.unlinked(c)));
            RemoteViews dark = views(c, id, kind, Renderer.make(kind, sz[0], sz[1], scale, new Renderer.St(f, true), d, evs, f, Store.unlinked(c)));
            rv = new RemoteViews(light, dark);
        } else {
            boolean dk = f.theme == 1 || (f.theme == 2 && systemDark(c));
            rv = views(c, id, kind, Renderer.make(kind, sz[0], sz[1], scale, new Renderer.St(f, dk), d, evs, f, Store.unlinked(c)));
        }
        try { m.updateAppWidget(id, rv); } catch (Exception e) {
            // 너무 크면 한 번 더 작게
            boolean dk = f.theme == 1 || (f.theme == 2 && systemDark(c));
            m.updateAppWidget(id, views(c, id, kind, Renderer.make(kind, sz[0], sz[1], Math.min(scale, 1.6f), new Renderer.St(f, dk), d, evs, f, Store.unlinked(c))));
        }
    }

    static RemoteViews views(Context c, int id, String kind, Bitmap bm) {
        boolean month = "month".equals(kind);
        boolean linked = ShiftData.parse(Store.data(c)) != null;
        RemoteViews rv = new RemoteViews(c.getPackageName(), month ? R.layout.widget_month : R.layout.widget_root);
        rv.setImageViewBitmap(R.id.img, bm);
        int fl = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        Store.Cfg f = Store.cfg(c, id, kind);
        String go = Store.OPEN_KEYS[Math.max(0, Math.min(Store.OPEN_KEYS.length - 1, f.open))];
        Intent open = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://baejeon-shift.web.app/?src=twa&go=" + go))
                .setClass(c, MainActivity.class).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        rv.setOnClickPendingIntent(R.id.img, PendingIntent.getActivity(c, 1000 + id, open, fl));
        if (month && linked) {
            rv.setViewVisibility(R.id.prev, android.view.View.VISIBLE);
            rv.setViewVisibility(R.id.next, android.view.View.VISIBLE);
            rv.setOnClickPendingIntent(R.id.prev, PendingIntent.getBroadcast(c, id * 10 + 1,
                    new Intent(c, MonthWidget.class).setAction(BaseWidget.ACT_PREV).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id), fl));
            rv.setOnClickPendingIntent(R.id.next, PendingIntent.getBroadcast(c, id * 10 + 2,
                    new Intent(c, MonthWidget.class).setAction(BaseWidget.ACT_NEXT).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id), fl));
            // 날짜 칸 터치 → 그날 일정 창
            float dens = c.getResources().getDisplayMetrics().density;
            float t = Renderer.tScale(f.text);
            rv.setViewVisibility(R.id.grid, android.view.View.VISIBLE);
            rv.setViewPadding(R.id.grid, Math.round(12 * dens), Math.round(Renderer.monthGridTop(t) * dens), Math.round(12 * dens), Math.round(12 * dens));
            int[] g = Renderer.monthGeom(f.monthOffset);
            int[] rowIds = {R.id.r0, R.id.r1, R.id.r2, R.id.r3, R.id.r4, R.id.r5};
            for (int r = 0; r < 6; r++) rv.setViewVisibility(rowIds[r], r < g[3] ? android.view.View.VISIBLE : android.view.View.GONE);
            for (int i = 0; i < g[3] * 7; i++) {
                int n = g[2] + i;
                int vid = c.getResources().getIdentifier("c" + i, "id", c.getPackageName());
                Intent di = new Intent(c, DayActivity.class)
                        .setData(android.net.Uri.parse("baejeonshift://day/" + id + "/" + n))
                        .putExtra("day", n).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
                rv.setOnClickPendingIntent(vid, PendingIntent.getActivity(c, 200000 + id * 64 + i, di, fl));
            }
        } else {
            rv.setViewVisibility(R.id.prev, android.view.View.GONE);
            rv.setViewVisibility(R.id.next, android.view.View.GONE);
            if (month) rv.setViewVisibility(R.id.grid, android.view.View.GONE);
        }
        return rv;
    }

    /** 자정이 지나면 오늘 표시가 바뀌도록 */
    static void scheduleMidnight(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        long now = System.currentTimeMillis();
        long off = ShiftData.KST.getOffset(now);
        long next = ((now + off) / 86400000L + 1) * 86400000L - off + 60000L;
        PendingIntent pi = PendingIntent.getBroadcast(c, 77, new Intent(c, RefreshReceiver.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        try { am.setAndAllowWhileIdle(AlarmManager.RTC, next, pi); } catch (Exception ignored) {}
        CalWatchWorker.watch(c);
        FamPollWorker.schedule(c);
    }
}
