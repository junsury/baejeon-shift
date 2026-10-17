package kr.baejeon.shift;

import android.Manifest;
import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;
import java.util.Map;

/** 위젯 설정: 바꾸는 즉시 위 미리보기에 보여요. */
public class ConfigActivity extends Activity {
    int id = AppWidgetManager.INVALID_APPWIDGET_ID;
    String kind = "month";
    Store.Cfg f;
    ImageView preview;
    LinearLayout previewBox;
    Switch evSwitch;
    int wall = 0;
    static final int[] WALLS = {0xFF33414D, 0xFFDDE2E7, 0xFF4F5F45};
    float dp;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setResult(RESULT_CANCELED);
        Intent in = getIntent();
        if (in != null && in.getExtras() != null)
            id = in.getExtras().getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return; }
        String k = WidgetUpdater.kindOf(this, id);
        if (k != null) kind = k;
        f = Store.cfg(this, id, kind);
        dp = getResources().getDisplayMetrics().density;
        build();
        // 처음 넣을 때 아직 데이터가 없으면 한 번 받아 오기
        if (!Store.token(this).isEmpty() && ShiftData.parse(Store.data(this)) == null)
            WidgetUpdater.refreshAsync(this, () -> runOnUiThread(this::draw));
    }

    int px(float v) { return Math.round(v * dp); }

    TextView label(String s, float size, boolean bold, int color) {
        TextView t = new TextView(this);
        t.setText(s); t.setTextSize(size); t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    LinearLayout.LayoutParams mlp(int top) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = px(top);
        return lp;
    }

    void build() {
        String title;
        switch (kind) {
            case "month": title = "근무 달력 위젯"; break;
            case "today": title = "오늘·이번 주 위젯"; break;
            case "week": title = "주간 근무 위젯"; break;
            default: title = "오늘 근무 위젯";
        }
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(0xFFF4F6F8);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(px(18), px(18), px(18), px(28));
        sv.addView(col);

        col.addView(label(title, 22, true, 0xFF161C24));

        if (Store.token(this).isEmpty() || Store.unlinked(this)) {
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(px(14), px(12), px(14), px(12));
            GradientDrawable g = new GradientDrawable(); g.setColor(0xFFFFF4D6); g.setCornerRadius(px(12));
            card.setBackground(g);
            card.addView(label(Store.unlinked(this) ? "위젯 연결이 끊겼어요" : "아직 내 근무와 연결되지 않았어요", 15, true, 0xFF5B3B00));
            card.addView(label("근무표 앱을 열어 로그인한 뒤 👤 메뉴의 [갤럭시 위젯 연결]을 누르세요. 설정은 지금 저장해 두면 연결 후 그대로 쓰여요.", 13.5f, false, 0xFF5B3B00), mlp(4));
            Button open = new Button(this);
            open.setText("근무표 앱 열기");
            open.setOnClickListener(v -> startActivity(new Intent(this, MainActivity.class)));
            card.addView(open, mlp(8));
            col.addView(card, mlp(12));
        }

        // 미리보기
        previewBox = new LinearLayout(this);
        previewBox.setGravity(Gravity.CENTER);
        previewBox.setPadding(px(14), px(18), px(14), px(18));
        preview = new ImageView(this);
        preview.setAdjustViewBounds(true);
        previewBox.addView(preview, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        col.addView(previewBox, mlp(14));
        TextView wallBtn = label("배경화면 바꿔 보기 ›", 13, false, 0xFF1D5D8F);
        wallBtn.setGravity(Gravity.END);
        wallBtn.setPadding(0, px(6), 0, 0);
        wallBtn.setOnClickListener(v -> { wall = (wall + 1) % WALLS.length; draw(); });
        col.addView(wallBtn, mlp(0));

        // 배경 투명도
        final TextView opL = label("", 15, true, 0xFF161C24);
        col.addView(opL, mlp(16));
        SeekBar op = new SeekBar(this);
        op.setMax(20); op.setProgress(f.opacity / 5);
        Runnable opT = () -> opL.setText("배경 진하기  " + f.opacity + "%  (0 = 완전 투명)");
        opT.run();
        op.setOnSeekBarChangeListener(new Bar(v -> { f.opacity = v * 5; opT.run(); draw(); }));
        col.addView(op, mlp(4));

        // 모서리
        final TextView coL = label("", 15, true, 0xFF161C24);
        col.addView(coL, mlp(14));
        SeekBar co = new SeekBar(this);
        co.setMax(16); co.setProgress(f.corner / 2);
        Runnable coT = () -> coL.setText("모서리  " + (f.corner == 0 ? "각지게" : f.corner >= 26 ? "둥글게 (" + f.corner + ")" : String.valueOf(f.corner)));
        coT.run();
        co.setOnSeekBarChangeListener(new Bar(v -> { f.corner = v * 2; coT.run(); draw(); }));
        col.addView(co, mlp(4));

        col.addView(label("글자 크기", 15, true, 0xFF161C24), mlp(14));
        col.addView(choice(new String[]{"작게", "보통", "크게"}, f.text, v -> { f.text = v; draw(); }), mlp(4));
        col.addView(label("근무 아이콘 크기", 15, true, 0xFF161C24), mlp(14));
        col.addView(choice(new String[]{"작게", "보통", "크게"}, f.chip, v -> { f.chip = v; draw(); }), mlp(4));
        col.addView(label("화면", 15, true, 0xFF161C24), mlp(14));
        col.addView(choice(new String[]{"밝게", "어둡게", "휴대폰 설정"}, f.theme, v -> { f.theme = v; draw(); }), mlp(4));

        if ("week".equals(kind)) {
            col.addView(label("보여줄 기간", 15, true, 0xFF161C24), mlp(14));
            col.addView(choice(new String[]{"이번 주 (월~일)", "오늘부터 7일"}, f.weekMode, v -> { f.weekMode = v; draw(); }), mlp(4));
        }

        if (!"mini".equals(kind)) {
            evSwitch = new Switch(this);
            evSwitch.setText("내 캘린더 일정 보이기");
            evSwitch.setTextSize(15);
            evSwitch.setTypeface(Typeface.DEFAULT_BOLD);
            evSwitch.setTextColor(0xFF161C24);
            evSwitch.setChecked(f.events && CalendarReader.allowed(this));
            evSwitch.setOnCheckedChangeListener((CompoundButton bt, boolean on) -> {
                f.events = on;
                if (on && !CalendarReader.allowed(this)) requestPermissions(new String[]{Manifest.permission.READ_CALENDAR}, 5);
                draw();
            });
            col.addView(evSwitch, mlp(16));
            col.addView(label("휴대폰에 있는 구글·삼성 캘린더 일정을 근무 위에 겹쳐 보여줘요. 일정은 휴대폰 안에서만 읽고 어디에도 보내지 않아요.", 12.5f, false, 0xFF4F5A67), mlp(2));
            if (f.events && !CalendarReader.allowed(this)) f.events = false;
        }

        Button done = new Button(this);
        done.setText("완료");
        done.setTextSize(16);
        done.setOnClickListener(v -> save());
        col.addView(done, mlp(22));

        setContentView(sv);
        draw();
    }

    interface IntCb { void on(int v); }

    static final class Bar implements SeekBar.OnSeekBarChangeListener {
        final IntCb cb;
        Bar(IntCb cb) { this.cb = cb; }
        @Override public void onProgressChanged(SeekBar s, int v, boolean user) { if (user) cb.on(v); }
        @Override public void onStartTrackingTouch(SeekBar s) {}
        @Override public void onStopTrackingTouch(SeekBar s) {}
    }

    View choice(String[] names, int sel, IntCb cb) {
        RadioGroup g = new RadioGroup(this);
        g.setOrientation(RadioGroup.HORIZONTAL);
        for (int i = 0; i < names.length; i++) {
            RadioButton r = new RadioButton(this);
            r.setText(names[i]); r.setTextSize(14); r.setTextColor(0xFF161C24);
            r.setId(View.generateViewId());
            final int v = i;
            r.setOnClickListener(x -> cb.on(v));
            g.addView(r, new RadioGroup.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            if (i == sel) r.setChecked(true);
        }
        return g;
    }

    void draw() {
        if (preview == null) return;
        float[] sz = WidgetUpdater.defSize(kind);
        AppWidgetManager m = AppWidgetManager.getInstance(this);
        float[] real = WidgetUpdater.sizeOf(this, m, id, kind);
        if (real[0] > 40 && real[1] > 30) sz = real;
        boolean dk = f.theme == 1 || (f.theme == 2 && WidgetUpdater.systemDark(this));
        ShiftData d = ShiftData.parse(Store.data(this));
        Map<Integer, List<CalendarReader.Ev>> evs = null;
        if (f.events && d != null) { int n0 = ShiftData.todayNum(); evs = CalendarReader.read(this, n0 - 45, n0 + 45); }
        Store.Cfg pf = Store.cfg(this, id, kind);
        f.monthOffset = pf.monthOffset; f.offsetAt = pf.offsetAt;
        Bitmap bm = Renderer.make(kind, sz[0], sz[1], dp, new Renderer.St(f, dk), d, evs, f, Store.unlinked(this));
        preview.setImageBitmap(bm);
        int w = Math.round(sz[0] * dp);
        ViewGroup.LayoutParams lp = preview.getLayoutParams();
        lp.width = Math.min(w, getResources().getDisplayMetrics().widthPixels - px(64));
        preview.setLayoutParams(lp);
        GradientDrawable g = new GradientDrawable(); g.setColor(WALLS[wall]); g.setCornerRadius(px(16));
        previewBox.setBackground(g);
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        super.onRequestPermissionsResult(code, perms, res);
        if (!CalendarReader.allowed(this)) {
            f.events = false;
            if (evSwitch != null) evSwitch.setChecked(false);
            Toast.makeText(this, "캘린더 권한을 허용해야 일정이 보여요", Toast.LENGTH_LONG).show();
        }
        draw();
    }

    void save() {
        Store.Cfg pf = Store.cfg(this, id, kind);
        f.monthOffset = pf.monthOffset; f.offsetAt = pf.offsetAt;
        Store.saveCfg(this, id, f);
        WidgetUpdater.render(this, AppWidgetManager.getInstance(this), id);
        WidgetUpdater.scheduleMidnight(this);
        setResult(RESULT_OK, new Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id));
        finish();
    }
}
