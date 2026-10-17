package kr.baejeon.shift;

import android.Manifest;
import android.app.Activity;
import android.app.TimePickerDialog;
import android.appwidget.AppWidgetManager;
import android.content.ContentUris;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.CalendarContract;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 달력 위젯에서 날짜를 누르면 뜨는 창.
 * 그날 내 근무와 캘린더 일정을 보여주고, 바로 일정을 추가해요 (구글·삼성 등 휴대폰 캘린더에 저장).
 */
public class DayActivity extends Activity {
    int day, wid;
    float dp;
    LinearLayout col, form;
    EditText title, memo;
    CheckBox allDay;
    Button startBtn, endBtn, saveBtn;
    Spinner calSpin;
    List<CalendarReader.Cal> cals = new ArrayList<>();
    int startMin = 9 * 60, endMin = 10 * 60;
    CalendarReader.Ev editing = null;          // 고치는 중인 일정
    String pendingEdit = null;                 // 근무표 앱에서 누른 일정 (g:구글id 또는 l:휴대폰id)
    TextView formTitle, calLabel;
    Button delBtn, cancelEdit, openCal;
    Runnable showTimes;
    List<CalendarReader.Ev> dayEvents = new ArrayList<>();
    static final int REQ_READ = 11, REQ_WRITE = 12;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        dp = getResources().getDisplayMetrics().density;
        day = getIntent().getIntExtra("day", ShiftData.todayNum());
        // 근무표 앱(웹)에서 baejeonshift://day?d=2026-10-20 로 열 때
        Uri u = getIntent().getData();
        String wantEdit = null;
        if (u != null && "day".equals(u.getHost()) && u.getQueryParameter("d") != null) {
            int n = FamApi.dayNum(u.getQueryParameter("d"));
            if (n != Integer.MIN_VALUE) day = n;
            wantEdit = u.getQueryParameter("e");
        }
        pendingEdit = wantEdit;
        wid = getIntent().getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 0);
        getWindow().setLayout(Math.min(getResources().getDisplayMetrics().widthPixels - px(24), px(460)), ViewGroup.LayoutParams.WRAP_CONTENT);
        build();
        if (pendingEdit != null) {
            for (CalendarReader.Ev e : dayEvents) {
                if ((pendingEdit.startsWith("g:") && pendingEdit.substring(2).equals(e.gid))
                        || (pendingEdit.startsWith("l:") && pendingEdit.substring(2).equals(String.valueOf(e.eventId)))) { startEdit(e); break; }
            }
            pendingEdit = null;
        }
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

    static String hm(int m) { m = Math.floorMod(m, 1440); return String.format(Locale.US, "%02d:%02d", m / 60, m % 60); }

    void build() {
        ScrollView sv = new ScrollView(this);
        col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(px(20), px(18), px(20), px(16));
        sv.addView(col);

        // 날짜 + 내 근무
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        int w = ShiftData.dow(day);
        int dateColor = w == 0 ? 0xFFE0362A : w == 6 ? 0xFF2F6FD6 : 0xFF161C24;
        ShiftData d = ShiftData.parse(Store.data(this));
        if (d != null && d.holiday(day)) dateColor = 0xFFE0362A;
        head.addView(label(ShiftData.mdw(day), 22, true, dateColor));
        if (d != null) {
            String code = d.code(day);
            if (code != null && !"없".equals(code)) {
                TextView chip = label(ShiftData.label(code), 15, true, Renderer.chipColors(code, false)[1]);
                chip.setGravity(Gravity.CENTER);
                GradientDrawable g = new GradientDrawable();
                g.setColor(Renderer.chipColors(code, false)[0]);
                g.setCornerRadius(px(7));
                if ("휴".equals(code)) g.setStroke(px(1), 0xFFD5DAE0);
                if (d.changed(day)) g.setStroke(px(2), 0xFF1D5D8F);
                chip.setBackground(g);
                chip.setPadding(px(8), px(3), px(8), px(3));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.leftMargin = px(10);
                head.addView(chip, lp);
                String nm = ShiftData.longName(code) + (d.changed(day) ? " · 변경" : "");
                String part = d.part(day);
                if (!part.isEmpty()) nm = "시간휴가 " + part;
                LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                lp2.leftMargin = px(8);
                head.addView(label(nm, 14, false, 0xFF4F5A67), lp2);
            }
        }
        col.addView(head);
        List<String> mates = d != null ? d.mates(day) : new ArrayList<>();
        if (!mates.isEmpty()) col.addView(label("같이 근무: " + android.text.TextUtils.join(", ", mates), 13.5f, false, 0xFF4F5A67), mlp(4));
        String memoTxt = d != null ? d.memo(day) : "";
        if (!memoTxt.isEmpty()) {
            TextView mt = label("📝 " + memoTxt, 14, false, 0xFF161C24);
            GradientDrawable mg = new GradientDrawable(); mg.setColor(0x2EF4AD00); mg.setCornerRadius(px(8));
            mt.setBackground(mg); mt.setPadding(px(10), px(6), px(10), px(6));
            col.addView(mt, mlp(8));
        }

        // 그날 일정
        col.addView(label("일정", 14, true, 0xFF4F5A67), mlp(16));
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        col.addView(list, mlp(4));
        fillEvents(list);

        // 일정 추가
        form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable fb = new GradientDrawable(); fb.setColor(0xFFF2F4F7); fb.setCornerRadius(px(14));
        form.setBackground(fb);
        form.setPadding(px(14), px(12), px(14), px(14));
        col.addView(form, mlp(16));
        buildForm();

        // 아래 버튼
        LinearLayout btns = new LinearLayout(this);
        btns.setOrientation(LinearLayout.HORIZONTAL);
        Button openApp = new Button(this);
        openApp.setText("근무표 열기");
        openApp.setOnClickListener(v -> {
            Store.Cfg wf = Store.cfg(this, wid, "month");
            String go = Store.OPEN_KEYS[Math.max(0, Math.min(Store.OPEN_KEYS.length - 1, wf.open))] + "&d=" + ShiftData.ymd(day);
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://baejeon-shift.web.app/?src=twa&go=" + go))
                    .setClass(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            finish();
        });
        Button close = new Button(this);
        close.setText("닫기");
        close.setOnClickListener(v -> finish());
        btns.addView(openApp, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        btns.addView(close, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        col.addView(btns, mlp(10));

        setContentView(sv);
    }

    void fillEvents(LinearLayout list) {
        list.removeAllViews();
        if (!CalendarReader.allowed(this)) {
            TextView t = label("캘린더 권한을 허용하면 이 날 일정이 보여요 ›", 13.5f, false, 0xFF1D5D8F);
            t.setOnClickListener(v -> requestPermissions(new String[]{Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR}, REQ_READ));
            list.addView(t);
            return;
        }
        Store.Cfg f = Store.cfg(this, wid, "month");
        Map<Integer, List<CalendarReader.Ev>> m = CalendarReader.read(this, day, day, Store.calExcluded(this));
        List<CalendarReader.Ev> evs = m.get(day);
        dayEvents = evs == null ? new ArrayList<>() : evs;
        if (evs == null || evs.isEmpty()) { list.addView(label("일정 없음", 13.5f, false, 0xFF8A94A0)); return; }
        list.addView(label("일정을 누르면 고치거나 지울 수 있어요", 12, false, 0xFF8A94A0));
        for (CalendarReader.Ev e : evs) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, px(6), 0, px(6));
            View dot = new View(this);
            GradientDrawable g = new GradientDrawable(); g.setShape(GradientDrawable.OVAL); g.setColor(e.color != 0 ? (e.color | 0xFF000000) : 0xFF6B4CC0);
            dot.setBackground(g);
            LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(px(9), px(9));
            dl.rightMargin = px(10);
            row.addView(dot, dl);
            String when = e.time.isEmpty() ? "종일" : e.time;
            TextView tw = label(when, 13.5f, false, 0xFF4F5A67);
            row.addView(tw, new LinearLayout.LayoutParams(px(48), ViewGroup.LayoutParams.WRAP_CONTENT));
            row.addView(label((e.who.isEmpty() ? "" : e.who + " · ") + e.title, 15, false, 0xFF161C24), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            final CalendarReader.Ev ev = e;
            row.setOnClickListener(v -> startEdit(ev));
            row.setBackgroundResource(android.R.drawable.list_selector_background);
            TextView pen = label("✎", 15, false, 0xFF8A94A0);
            row.addView(pen);
            list.addView(row);
        }
    }

    void buildForm() {
        form.removeAllViews();
        formTitle = label("＋ 일정 추가", 15, true, 0xFF161C24);
        form.addView(formTitle);

        title = new EditText(this);
        title.setHint("일정 제목 (예: 치과 15:00)");
        title.setSingleLine(true);
        title.setImeOptions(EditorInfo.IME_ACTION_DONE);
        title.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        form.addView(title, mlp(6));

        allDay = new CheckBox(this);
        allDay.setText("하루 종일");
        allDay.setTextColor(0xFF161C24);
        form.addView(allDay, mlp(4));

        LinearLayout times = new LinearLayout(this);
        times.setOrientation(LinearLayout.HORIZONTAL);
        times.setGravity(Gravity.CENTER_VERTICAL);
        startBtn = new Button(this);
        endBtn = new Button(this);
        TextView tilde = label("~", 16, false, 0xFF4F5A67);
        tilde.setPadding(px(8), 0, px(8), 0);
        times.addView(startBtn, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        times.addView(tilde);
        times.addView(endBtn, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        form.addView(times, mlp(2));
        Runnable showT = () -> { startBtn.setText("시작 " + hm(startMin)); endBtn.setText("끝 " + hm(endMin) + (endMin <= startMin ? " (다음날)" : "")); };
        showTimes = showT;
        showT.run();
        startBtn.setOnClickListener(v -> new TimePickerDialog(this, (tp, h, mi) -> {
            int dur = Math.max(30, endMin - startMin);
            startMin = h * 60 + mi; endMin = Math.min(startMin + dur, 24 * 60 - 1); showT.run();
        }, startMin / 60, startMin % 60, false).show());
        endBtn.setOnClickListener(v -> new TimePickerDialog(this, (tp, h, mi) -> { endMin = h * 60 + mi; showT.run(); },
                endMin / 60, endMin % 60, false).show());
        allDay.setOnCheckedChangeListener((bt, on) -> times.setVisibility(on ? View.GONE : View.VISIBLE));
        allDay.setChecked(true);

        memo = new EditText(this);
        memo.setHint("메모 (선택)");
        form.addView(memo, mlp(2));

        calLabel = label("저장할 캘린더", 13, true, 0xFF4F5A67);
        form.addView(calLabel, mlp(8));
        calSpin = new Spinner(this);
        form.addView(calSpin, mlp(2));
        fillCals();

        saveBtn = new Button(this);
        saveBtn.setText("일정 저장");
        saveBtn.setOnClickListener(v -> save());
        form.addView(saveBtn, mlp(8));

        LinearLayout eb = new LinearLayout(this);
        eb.setOrientation(LinearLayout.HORIZONTAL);
        delBtn = new Button(this); delBtn.setText("삭제"); delBtn.setTextColor(0xFFC0261C);
        delBtn.setOnClickListener(v -> confirmDelete());
        cancelEdit = new Button(this); cancelEdit.setText("새 일정 추가로");
        cancelEdit.setOnClickListener(v -> stopEdit());
        eb.addView(delBtn, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        eb.addView(cancelEdit, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        form.addView(eb, mlp(2));
        openCal = new Button(this); openCal.setText("캘린더 앱에서 열기");
        openCal.setOnClickListener(v -> {
            if (editing == null || editing.eventId <= 0) return;
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, editing.eventId)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (Exception ex) { Toast.makeText(this, "캘린더 앱을 열 수 없어요", Toast.LENGTH_SHORT).show(); }
        });
        form.addView(openCal, mlp(0));
        setEditUi(false);
    }

    void setEditUi(boolean on) {
        int vis = on ? View.VISIBLE : View.GONE;
        delBtn.setVisibility(vis); cancelEdit.setVisibility(vis);
        openCal.setVisibility(on && editing != null && editing.eventId > 0 ? View.VISIBLE : View.GONE);
        calSpin.setVisibility(on ? View.GONE : View.VISIBLE);
        calLabel.setVisibility(on ? View.GONE : View.VISIBLE);
        formTitle.setText(on ? "✎ 일정 고치기" + (editing != null && !editing.who.isEmpty() ? " (" + editing.who + "가 만든 일정)" : "") : "＋ 일정 추가");
        saveBtn.setText(on ? "고친 내용 저장" : "일정 저장");
    }

    /** 일정을 눌렀을 때: 아래 칸에 내용을 채워 고치기 */
    void startEdit(CalendarReader.Ev e) {
        if (e.memo) return;
        if (e.gid == null && e.eventId <= 0) { Toast.makeText(this, "이 일정은 여기서 고칠 수 없어요", Toast.LENGTH_SHORT).show(); return; }
        editing = e;
        title.setText(e.title);
        memo.setText(e.desc);
        boolean all = e.startMin < 0;
        allDay.setChecked(all);
        if (!all) { startMin = e.startMin; endMin = e.endMin >= 0 ? e.endMin : Math.min(e.startMin + 60, 1439); }
        if (showTimes != null) showTimes.run();
        setEditUi(true);
        title.requestFocus();
        form.getParent().requestChildFocus(form, form);
    }

    void stopEdit() { editing = null; title.setText(""); memo.setText(""); allDay.setChecked(true); setEditUi(false); }

    void confirmDelete() {
        final CalendarReader.Ev e = editing;
        if (e == null) return;
        new android.app.AlertDialog.Builder(this).setTitle("일정 지우기").setMessage("'" + e.title + "' 일정을 지울까요?" + (e.gid != null ? "\n가족 캘린더에서도 지워져요." : ""))
                .setPositiveButton("지우기", (d, w) -> {
                    delBtn.setEnabled(false);
                    new Thread(() -> {
                        boolean ok;
                        if (e.gid != null) ok = FamApi.delete(this, e.gid);
                        else {
                            if (!CalendarReader.canWrite(this)) ok = false;
                            else { long cal = CalendarReader.calendarOf(this, e.eventId); ok = CalendarReader.delete(this, e.eventId); if (ok && cal >= 0) CalendarReader.syncCalendar(this, cal); }
                        }
                        final boolean done = ok;
                        WidgetUpdater.renderAll(this);
                        if (done) CalUpload.upload(this, true);
                        runOnUiThread(() -> {
                            delBtn.setEnabled(true);
                            if (!done) { Toast.makeText(this, "지우지 못했어요" + (CalendarReader.canWrite(this) ? "" : " (캘린더 권한 필요)"), Toast.LENGTH_LONG).show(); return; }
                            Toast.makeText(this, "지웠어요", Toast.LENGTH_SHORT).show();
                            finish();
                        });
                    }).start();
                }).setNegativeButton("취소", null).show();
    }

    void fillCals() {
        if (!CalendarReader.allowed(this)) {
            cals = new ArrayList<>();
            calSpin.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, new String[]{"저장할 때 캘린더 권한을 물어봐요"}));
            return;
        }
        cals = CalendarReader.writable(this);
        List<String> names = new ArrayList<>();
        for (CalendarReader.Cal c : cals) names.add(c.name + "  (" + c.account + ")");
        if (names.isEmpty()) names.add("일정을 넣을 수 있는 캘린더가 없어요");
        calSpin.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, names));
        long last = Store.p(this).getLong("lastCal", -1);
        for (int i = 0; i < cals.size(); i++) if (cals.get(i).id == last) calSpin.setSelection(i);
    }

    void save() {
        String t = title.getText().toString().trim();
        if (t.isEmpty()) { title.setError("제목을 적어 주세요"); title.requestFocus(); return; }
        if (!CalendarReader.allowed(this) || !CalendarReader.canWrite(this)) {
            requestPermissions(new String[]{Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR}, REQ_WRITE);
            return;
        }
        if (editing != null) { saveEdit(t); return; }
        int i = calSpin.getSelectedItemPosition();
        if (cals.isEmpty() || i < 0 || i >= cals.size()) { Toast.makeText(this, "저장할 캘린더가 없어요", Toast.LENGTH_LONG).show(); return; }
        final long calId = cals.get(i).id;
        final boolean ad = allDay.isChecked();
        final String mm = memo.getText().toString().trim();
        Store.p(this).edit().putLong("lastCal", calId).apply();
        saveBtn.setEnabled(false);
        saveBtn.setText("저장 중…");
        final boolean fam = calId == ShiftSync.localId(this);
        new Thread(() -> {
            // 가족 공유 캘린더는 구글 서버에 바로 넣어요 → 아이폰에 바로 보여요
            boolean ok = fam && FamApi.insert(this, t, day, ad, startMin, endMin, mm);
            boolean viaApi = ok;
            if (!ok) {
                ok = CalendarReader.insert(this, calId, t, day, ad, startMin, endMin, mm);
                if (ok) CalendarReader.syncCalendar(this, calId);   // 구글로 올라가게
            }
            final boolean done = ok;
            WidgetUpdater.renderAll(this);
            runOnUiThread(() -> {
                if (!done) { saveBtn.setEnabled(true); saveBtn.setText("일정 저장"); Toast.makeText(this, "일정을 저장하지 못했어요", Toast.LENGTH_LONG).show(); return; }
                Toast.makeText(this, ShiftData.mdw(day) + " 일정을 추가했어요" + (viaApi ? " (가족에게 바로 보여요)" : ""), Toast.LENGTH_SHORT).show();
                finish();
            });
            if (done) CalUpload.upload(this, false);
        }).start();
    }

    void saveEdit(String t) {
        final CalendarReader.Ev e = editing;
        final boolean ad = allDay.isChecked();
        final String mm = memo.getText().toString().trim();
        saveBtn.setEnabled(false); saveBtn.setText("저장 중…");
        new Thread(() -> {
            boolean ok;
            if (e.gid != null) ok = FamApi.update(this, e.gid, t, day, ad, startMin, endMin, mm);   // 색은 처음 만든 사람 색 그대로
            else { long cal = CalendarReader.calendarOf(this, e.eventId); ok = CalendarReader.update(this, e.eventId, t, day, ad, startMin, endMin, mm); if (ok && cal >= 0) CalendarReader.syncCalendar(this, cal); }
            final boolean done = ok;
            WidgetUpdater.renderAll(this);
            if (done) CalUpload.upload(this, true);
            runOnUiThread(() -> {
                if (!done) { saveBtn.setEnabled(true); saveBtn.setText("고친 내용 저장"); Toast.makeText(this, "고치지 못했어요", Toast.LENGTH_LONG).show(); return; }
                Toast.makeText(this, "일정을 고쳤어요", Toast.LENGTH_SHORT).show();
                finish();
            });
        }).start();
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        super.onRequestPermissionsResult(code, perms, res);
        if (!CalendarReader.allowed(this)) { Toast.makeText(this, "캘린더 권한을 허용해야 일정을 보고 추가할 수 있어요", Toast.LENGTH_LONG).show(); return; }
        // 화면 다시 그리기 (입력한 제목은 유지)
        String keepT = title != null ? title.getText().toString() : "";
        String keepM = memo != null ? memo.getText().toString() : "";
        boolean keepA = allDay == null || allDay.isChecked();
        build();
        title.setText(keepT); memo.setText(keepM); allDay.setChecked(keepA);
        if (code == REQ_WRITE && CalendarReader.canWrite(this)) save();
    }
}
