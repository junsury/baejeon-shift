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
    ScrollView sv;
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
        // 구글 캘린더 앱에서 지우거나 고친 일정이 남아 보이지 않게 열 때마다 새로 받아요
        new Thread(() -> { if (FamApi.refresh(this)) runOnUiThread(() -> { if (evList != null && editing == null && !isFinishing()) fillEvents(evList); }); }).start();
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

    TextView pill(String s, int fg, int bg) {
        TextView t = label(s, 14, true, fg);
        t.setGravity(Gravity.CENTER);
        t.setPadding(px(12), 0, px(12), 0);
        GradientDrawable g = new GradientDrawable(); g.setColor(bg); g.setCornerRadius(px(18));
        t.setBackground(g);
        t.setClickable(true);
        return t;
    }

    LinearLayout.LayoutParams mlp(int top) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = px(top);
        return lp;
    }

    static String hm(int m) { m = Math.floorMod(m, 1440); return String.format(Locale.US, "%02d:%02d", m / 60, m % 60); }

    void build() {
        sv = new ScrollView(this);
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
        evList = list;
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
            if (!e.memo && (e.gid != null || e.eventId > 0)) {
                LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, px(36));
                bl.leftMargin = px(6);
                TextView eb = pill("수정하기", 0xFF1D5D8F, 0xFFE3EEF7);
                eb.setOnClickListener(v -> startEdit(ev));
                row.addView(eb, bl);
                TextView db = pill("삭제", 0xFFC0261C, 0xFFFBE7E5);
                db.setOnClickListener(v -> confirmDelete(ev));
                row.addView(db, new LinearLayout.LayoutParams(bl));
            }
            list.addView(row);
        }
    }

    // ---- 일정 추가·수정 칸 ----
    ShiftData sd;
    LinearLayout evList;
    int fDay0, fDay1;                         // 고른 날짜 (시작·끝)
    int repeat = EvSpec.NONE, lunarM = 1, lunarD = 1, label = EvSpec.LABEL_NONE, remind = -2;
    boolean lunarLeap = false;
    String tpl = "";
    Spinner repeatSpin, remindSpin, labelSpin;
    CheckBox lunarChk; boolean lunar = false; int colorSel = 0; LinearLayout colorRow; Button remindTimeBtn;
    int remindDay = -2, remindAt = 9 * 60;
    // 사용자 지정 반복
    int rInterval = 1, rDays = 0, endMode = 0, untilD = Integer.MIN_VALUE, rCount = 10;
    LinearLayout customBox, daysRow; Spinner intervalSpin, endSpin; Button untilBtn; EditText countEt; TextView repeatSum;
    Button dateBtn, endDateBtn, lunarBtn, copyBtn;
    TextView shiftInfo;
    LinearLayout tplRow, offRow, repeatBox, lunarRow, labelRow;
    View tplScroll;
    EditText baseYearEt;
    int[] remindVals = EvSpec.ALLDAY_REMIND;
    final List<TextView> tplChips = new ArrayList<>();

    /** 선택 칸 글자가 기기 테마에 따라 안 보이는 일이 없게 글자색·크기를 직접 지정 */
    ArrayAdapter<String> ad(String[] items) { return ad(java.util.Arrays.asList(items)); }
    ArrayAdapter<String> ad(List<String> items) {
        return new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, new ArrayList<>(items)) {
            @Override public View getView(int pos, View cv, ViewGroup parent) { return style(super.getView(pos, cv, parent), false); }
            @Override public View getDropDownView(int pos, View cv, ViewGroup parent) { return style(super.getDropDownView(pos, cv, parent), true); }
            View style(View v, boolean drop) {
                if (v instanceof TextView) {
                    TextView t = (TextView) v;
                    t.setTextColor(0xFF161C24); t.setTextSize(16);
                    t.setPadding(px(12), px(drop ? 12 : 8), px(12), px(drop ? 12 : 8));
                    if (drop) t.setBackgroundColor(0xFFFFFFFF);
                }
                return v;
            }
        };
    }

    TextView chipBtn(String s, boolean on) {
        TextView t = label(s, 14, true, on ? 0xFFFFFFFF : 0xFF1D5D8F);
        t.setGravity(Gravity.CENTER);
        t.setPadding(px(12), px(7), px(12), px(7));
        GradientDrawable g = new GradientDrawable(); g.setCornerRadius(px(16));
        g.setColor(on ? 0xFF1D5D8F : 0xFFE3EEF7);
        t.setBackground(g);
        t.setClickable(true);
        return t;
    }

    View hscroll(LinearLayout inner) {
        android.widget.HorizontalScrollView h = new android.widget.HorizontalScrollView(this);
        h.setHorizontalScrollBarEnabled(false);
        h.addView(inner);
        return h;
    }

    LinearLayout.LayoutParams gap() { LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT); lp.rightMargin = px(6); return lp; }

    void buildForm() {
        form.removeAllViews();
        sd = ShiftData.parse(Store.data(this));
        fDay0 = day; fDay1 = day;
        formTitle = label("＋ 일정 추가", 15, true, 0xFF161C24);
        form.addView(formTitle);

        // 자주 쓰는 일정
        tplRow = new LinearLayout(this); tplRow.setOrientation(LinearLayout.HORIZONTAL);
        tplChips.clear();
        for (String k : new String[]{"생일", "기념일"}) {
            String ic = k.equals("생일") ? "🎂 " : "💍 ";
            TextView t = chipBtn(ic + k, false);
            t.setTag(k);
            t.setOnClickListener(v -> applyTemplate(k));
            tplRow.addView(t, gap()); tplChips.add(t);
        }
        tplScroll = hscroll(tplRow);
        form.addView(tplScroll, mlp(6));

        title = new EditText(this);
        title.setHint("일정 제목 (예: 치과 15:00)");
        title.setSingleLine(true);
        title.setImeOptions(EditorInfo.IME_ACTION_DONE);
        title.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        form.addView(title, mlp(4));

        // 날짜 (시작 ~ 끝)
        LinearLayout dates = new LinearLayout(this); dates.setOrientation(LinearLayout.HORIZONTAL); dates.setGravity(Gravity.CENTER_VERTICAL);
        dateBtn = new Button(this); endDateBtn = new Button(this);
        dates.addView(dateBtn, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.2f));
        TextView tl = label("~", 16, false, 0xFF4F5A67); tl.setPadding(px(6), 0, px(6), 0); dates.addView(tl);
        dates.addView(endDateBtn, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        form.addView(dates, mlp(2));
        dateBtn.setOnClickListener(v -> pickDate(fDay0, n -> { int span = fDay1 - fDay0; fDay0 = n; fDay1 = n + Math.max(0, span); onDateChanged(true); }));
        endDateBtn.setOnClickListener(v -> pickDate(fDay1, n -> { fDay1 = Math.max(fDay0, n); onDateChanged(false); }));

        shiftInfo = label("", 13.5f, true, 0xFF4F5A67);
        shiftInfo.setPadding(px(10), px(6), px(10), px(6));
        form.addView(shiftInfo, mlp(4));

        // 쉬는 날 빨리 고르기
        offRow = new LinearLayout(this); offRow.setOrientation(LinearLayout.HORIZONTAL); offRow.setGravity(Gravity.CENTER_VERTICAL);
        form.addView(hscroll(offRow), mlp(4));
        fillOffDays();

        LinearLayout checks = new LinearLayout(this); checks.setOrientation(LinearLayout.HORIZONTAL); checks.setGravity(Gravity.CENTER_VERTICAL);
        allDay = new CheckBox(this);
        allDay.setText("하루 종일");
        allDay.setTextColor(0xFF161C24);
        checks.addView(allDay, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        lunarChk = new CheckBox(this);
        lunarChk.setText("음력");
        lunarChk.setTextColor(0xFF161C24);
        checks.addView(lunarChk, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        form.addView(checks, mlp(4));
        lunarRow = new LinearLayout(this); lunarRow.setOrientation(LinearLayout.HORIZONTAL); lunarRow.setGravity(Gravity.CENTER_VERTICAL);
        lunarBtn = new Button(this);
        lunarBtn.setOnClickListener(v -> pickLunar());
        lunarRow.addView(lunarBtn, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        form.addView(lunarRow, mlp(0));
        lunarChk.setOnCheckedChangeListener((bt, on) -> setLunar(on));

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
        Runnable showT = () -> {
            startBtn.setText("시작 " + hm(startMin));
            endBtn.setText("끝 " + hm(endMin) + (fDay1 == fDay0 && endMin <= startMin ? " (다음날)" : ""));
            updateShiftInfo();
        };
        showTimes = showT;
        startBtn.setOnClickListener(v -> new TimePickerDialog(this, (tp, h, mi) -> {
            int dur = Math.max(30, endMin - startMin);
            startMin = h * 60 + mi; endMin = Math.min(startMin + dur, 24 * 60 - 1); showT.run();
        }, startMin / 60, startMin % 60, false).show());
        endBtn.setOnClickListener(v -> new TimePickerDialog(this, (tp, h, mi) -> { endMin = h * 60 + mi; showT.run(); },
                endMin / 60, endMin % 60, false).show());
        allDay.setOnCheckedChangeListener((bt, on) -> { times.setVisibility(on ? View.GONE : View.VISIBLE); fillRemind(editing != null); updateShiftInfo(); });

        // 반복
        repeatBox = new LinearLayout(this); repeatBox.setOrientation(LinearLayout.VERTICAL);
        repeatSpin = new Spinner(this);
        repeatSpin.setAdapter(ad(EvSpec.REPEAT_NAMES));
        repeatBox.addView(repeatSpin, mlp(2));
        labelRow = new LinearLayout(this); labelRow.setOrientation(LinearLayout.HORIZONTAL); labelRow.setGravity(Gravity.CENTER_VERTICAL);
        labelSpin = new Spinner(this);
        labelSpin.setAdapter(ad(new String[]{"꼬리표 없음", "몇 주년", "나이(세)"}));
        baseYearEt = new EditText(this);
        baseYearEt.setHint("시작 연도 (예: 2015)");
        baseYearEt.setInputType(InputType.TYPE_CLASS_NUMBER);
        baseYearEt.setSingleLine(true);
        labelRow.addView(labelSpin, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        labelRow.addView(baseYearEt, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        repeatBox.addView(labelRow, mlp(0));
        // 사용자 지정: 간격 · 요일 · 끝
        customBox = new LinearLayout(this); customBox.setOrientation(LinearLayout.VERTICAL);
        intervalSpin = new Spinner(this);
        customBox.addView(intervalSpin, mlp(0));
        daysRow = new LinearLayout(this); daysRow.setOrientation(LinearLayout.HORIZONTAL);
        customBox.addView(hscroll(daysRow), mlp(2));
        LinearLayout endRow = new LinearLayout(this); endRow.setOrientation(LinearLayout.HORIZONTAL); endRow.setGravity(Gravity.CENTER_VERTICAL);
        endSpin = new Spinner(this);
        endSpin.setAdapter(ad(new String[]{"끝: 계속", "끝: 날짜까지", "끝: 횟수만큼"}));
        untilBtn = new Button(this);
        countEt = new EditText(this); countEt.setInputType(InputType.TYPE_CLASS_NUMBER); countEt.setSingleLine(true); countEt.setText("10"); countEt.setHint("횟수");
        endRow.addView(endSpin, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.2f));
        endRow.addView(untilBtn, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        endRow.addView(countEt, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        customBox.addView(endRow, mlp(2));
        repeatSum = label("", 13, true, 0xFF1D5D8F);
        repeatSum.setPadding(px(4), px(2), px(4), px(4));
        customBox.addView(repeatSum, mlp(0));
        repeatBox.addView(customBox, mlp(0));
        intervalSpin.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            public void onItemSelected(android.widget.AdapterView<?> p, View v, int pos, long id) { rInterval = pos + 1; updateRepeatSum(); }
            public void onNothingSelected(android.widget.AdapterView<?> p) {}
        });
        endSpin.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            public void onItemSelected(android.widget.AdapterView<?> p, View v, int pos, long id) {
                endMode = pos;
                if (pos == 1 && untilD == Integer.MIN_VALUE) untilD = fDay0 + 365;
                untilBtn.setVisibility(pos == 1 ? View.VISIBLE : View.GONE);
                countEt.setVisibility(pos == 2 ? View.VISIBLE : View.GONE);
                updateRepeatSum();
            }
            public void onNothingSelected(android.widget.AdapterView<?> p) {}
        });
        untilBtn.setOnClickListener(v -> pickDate(untilD == Integer.MIN_VALUE ? fDay0 + 365 : untilD, n -> { untilD = Math.max(fDay0, n); updateRepeatSum(); }));
        countEt.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence a, int b, int c, int d) {}
            public void onTextChanged(CharSequence a, int b, int c, int d) {}
            public void afterTextChanged(android.text.Editable e) { try { rCount = Math.max(1, Math.min(500, Integer.parseInt(e.toString().trim()))); } catch (Exception x) { rCount = 0; } updateRepeatSum(); }
        });
        untilBtn.setVisibility(View.GONE); countEt.setVisibility(View.GONE);
        form.addView(repeatBox, mlp(2));
        repeatSpin.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            public void onItemSelected(android.widget.AdapterView<?> p, View v, int pos, long id) { setRepeat(pos); }
            public void onNothingSelected(android.widget.AdapterView<?> p) {}
        });
        labelSpin.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            public void onItemSelected(android.widget.AdapterView<?> p, View v, int pos, long id) { label = pos; baseYearEt.setVisibility(pos == 0 ? View.GONE : View.VISIBLE);
                baseYearEt.setHint(pos == 2 ? "태어난 해 (예: 1958)" : "시작 연도 (예: 2015)"); }
            public void onNothingSelected(android.widget.AdapterView<?> p) {}
        });

        // 알림
        LinearLayout rr = new LinearLayout(this); rr.setOrientation(LinearLayout.HORIZONTAL); rr.setGravity(Gravity.CENTER_VERTICAL);
        remindSpin = new Spinner(this);
        remindTimeBtn = new Button(this);
        rr.addView(remindSpin, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.3f));
        rr.addView(remindTimeBtn, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        form.addView(rr, mlp(2));
        remindSpin.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            public void onItemSelected(android.widget.AdapterView<?> p, View v, int pos, long id) { if (pos < remindVals.length) { remindDay = remindVals[pos]; showRemindTime(); } }
            public void onNothingSelected(android.widget.AdapterView<?> p) {}
        });
        remindTimeBtn.setOnClickListener(v -> new TimePickerDialog(this, (tp, h, mi) -> { remindAt = h * 60 + mi; showRemindTime(); }, remindAt / 60, remindAt % 60, false).show());

        // 일정 색 (구글 캘린더 색)
        TextView cl = label("색", 13, true, 0xFF4F5A67);
        form.addView(cl, mlp(6));
        colorRow = new LinearLayout(this); colorRow.setOrientation(LinearLayout.HORIZONTAL); colorRow.setGravity(Gravity.CENTER_VERTICAL);
        form.addView(hscroll(colorRow), mlp(2));
        fillColors(false);

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
        saveBtn.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF1D5D8F));
        saveBtn.setTextColor(0xFFFFFFFF);
        saveBtn.setTypeface(Typeface.DEFAULT_BOLD);
        form.addView(saveBtn, mlp(8));

        LinearLayout eb = new LinearLayout(this);
        eb.setOrientation(LinearLayout.HORIZONTAL);
        delBtn = new Button(this); delBtn.setText("삭제"); delBtn.setTextColor(0xFFC0261C);
        delBtn.setOnClickListener(v -> confirmDelete(editing));
        copyBtn = new Button(this); copyBtn.setText("복사해서 새로");
        copyBtn.setOnClickListener(v -> copyAsNew());
        cancelEdit = new Button(this); cancelEdit.setText("수정 취소");
        cancelEdit.setOnClickListener(v -> stopEdit());
        eb.addView(delBtn, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        eb.addView(copyBtn, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
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

        allDay.setChecked(true);
        times.setVisibility(View.GONE);
        setRepeat(EvSpec.NONE);
        fillRemind(false);
        onDateChanged(false);
        setEditUi(false);
    }

    interface DayPick { void on(int n); }
    void pickDate(int n, DayPick cb) {
        java.util.Calendar c = ShiftData.cal(n);
        new android.app.DatePickerDialog(this, (dp, y, m, d) -> cb.on((int) java.time.LocalDate.of(y, m + 1, d).toEpochDay()),
                c.get(java.util.Calendar.YEAR), c.get(java.util.Calendar.MONTH), c.get(java.util.Calendar.DAY_OF_MONTH)).show();
    }

    void onDateChanged(boolean startMoved) {
        if (fDay1 < fDay0) fDay1 = fDay0;
        dateBtn.setText("📅 " + ShiftData.mdw(fDay0) + (lunar ? " 음" + (lunarLeap ? "윤" : "") + lunarM + "/" + lunarD : ""));
        endDateBtn.setText(fDay1 == fDay0 ? "끝: 같은 날" : "끝 " + ShiftData.mdw(fDay1));
        if (startMoved && lunar) {   // 날짜를 직접 고르면 그날의 음력으로
            int[] lt = Lunar.fromSolar(fDay0);
            if (lt != null) { lunarM = lt[1]; lunarD = lt[2]; lunarLeap = lt[3] == 1; updateLunarBtn(); dateBtn.setText("📅 " + ShiftData.mdw(fDay0) + " 음" + (lunarLeap ? "윤" : "") + lunarM + "/" + lunarD); }
        }
        if (showTimes != null) showTimes.run(); else updateShiftInfo();
        fillOffDays();
    }

    /** 고른 날의 내 근무 + 시간이 겹치면 경고 */
    void updateShiftInfo() {
        if (shiftInfo == null) return;
        String c = sd != null ? sd.code(fDay0) : null;
        if (c == null) { shiftInfo.setVisibility(View.GONE); return; }
        shiftInfo.setVisibility(View.VISIBLE);
        boolean all = allDay == null || allDay.isChecked();
        int s = startMin, e = (fDay1 == fDay0 && endMin <= startMin) ? endMin + 1440 : endMin;
        String txt; boolean warn = false, free = false;
        switch (c) {
            case "주": txt = "이날 주간 근무 (08~18시)"; warn = !all && s < 18 * 60 && e > 8 * 60; break;
            case "야": txt = "이날 야간 근무 (18시~다음날 08시)"; warn = !all && e > 17 * 60; break;
            case "24": txt = "이날 주간+야간 근무 (08시~다음날 08시)"; warn = !all; break;
            case "비": txt = "비번 · 전날 야간 끝나고 08시 퇴근"; warn = !all && s < 9 * 60; free = !warn; break;
            case "휴": txt = "휴무 · 쉬는 날이에요"; free = true; break;
            case "연": txt = "휴가 · 쉬는 날이에요"; free = true; break;
            case "T": case "지원": txt = "이날 근무 (08~18시)"; warn = !all && s < 18 * 60 && e > 8 * 60; break;
            default: txt = "근무: " + ShiftData.longName(c);
        }
        if (warn) txt += "\n⚠ 근무 시간과 겹쳐요";
        else if (all && ("주".equals(c) || "야".equals(c) || "24".equals(c))) txt += " · 근무하는 날이에요";
        shiftInfo.setText(txt);
        shiftInfo.setTextColor(warn ? 0xFFC0261C : free ? 0xFF17824C : 0xFF4F5A67);
        GradientDrawable g = new GradientDrawable(); g.setCornerRadius(px(8));
        g.setColor(warn ? 0xFFFBE7E5 : free ? 0xFFDCF1E5 : 0xFFE9EDF2);
        shiftInfo.setBackground(g);
    }

    /** 앞으로 두 달 안의 쉬는 날(휴무·휴가) */
    void fillOffDays() {
        if (offRow == null) return;
        offRow.removeAllViews();
        if (sd == null) return;
        TextView lb = label("쉬는 날 ", 13, true, 0xFF4F5A67);
        offRow.addView(lb);
        int t0 = Math.max(ShiftData.todayNum(), day - 1), cnt = 0;
        for (int n = t0; n <= ShiftData.todayNum() + 75 && cnt < 10; n++) {
            String c = sd.code(n);
            if (!"휴".equals(c) && !"연".equals(c)) continue;
            final int nn = n;
            TextView t = chipBtn(ShiftData.mdw(n), n == fDay0);
            t.setOnClickListener(v -> { int span = fDay1 - fDay0; fDay0 = nn; fDay1 = nn + Math.max(0, span); onDateChanged(true); });
            offRow.addView(t, gap()); cnt++;
        }
        if (cnt == 0) offRow.addView(label("두 달 안에 없어요", 13, false, 0xFF8A94A0));
    }

    void setRepeat(int r) {
        if (r < 0 || r > EvSpec.YEARLY) r = EvSpec.NONE;
        if (lunar && (r == EvSpec.WEEKLY || r == EvSpec.MONTHLY)) {
            Toast.makeText(this, "음력은 '반복 없음'이나 '매년'만 돼요", Toast.LENGTH_SHORT).show();
            r = EvSpec.YEARLY;
        }
        repeat = r;
        if (repeatSpin.getSelectedItemPosition() != r) repeatSpin.setSelection(r);
        boolean yearly = r == EvSpec.YEARLY;
        labelRow.setVisibility(yearly ? View.VISIBLE : View.GONE);
        if (customBox != null) {
            customBox.setVisibility(r == EvSpec.NONE ? View.GONE : View.VISIBLE);
            String unit = r == EvSpec.WEEKLY ? "주" : r == EvSpec.MONTHLY ? "달" : "년";
            String[] iv = new String[6];
            for (int i = 0; i < 6; i++) iv[i] = i == 0 ? "매" + unit + " (1" + unit + "마다)" : (i + 1) + unit + "마다";
            int keepI = rInterval;
            intervalSpin.setAdapter(ad(iv));
            intervalSpin.setSelection(Math.max(0, Math.min(5, keepI - 1)));
            rInterval = keepI;
            if (r == EvSpec.WEEKLY && rDays == 0) rDays = 1 << ShiftData.dow(fDay0);
            fillDays();
            daysRow.setVisibility(r == EvSpec.WEEKLY ? View.VISIBLE : View.GONE);
            updateRepeatSum();
        }
        if (!yearly) { label = EvSpec.LABEL_NONE; labelSpin.setSelection(0); }
        baseYearEt.setVisibility(label == EvSpec.LABEL_NONE ? View.GONE : View.VISIBLE);
        if (dateBtn != null) onDateChanged(false);
    }

    void fillDays() {
        daysRow.removeAllViews();
        for (int i = 0; i < 7; i++) {
            final int bit = 1 << i;
            TextView t = chipBtn(ShiftData.WD[i], (rDays & bit) != 0);
            if ((rDays & bit) == 0) t.setTextColor(i == 0 ? 0xFFC0261C : i == 6 ? 0xFF2F6FD6 : 0xFF1D5D8F);
            t.setOnClickListener(v -> { int nd = rDays ^ bit; if (nd == 0) return; rDays = nd; fillDays(); updateRepeatSum(); });
            daysRow.addView(t, gap());
        }
    }

    EvSpec repeatPart() {
        EvSpec sp = new EvSpec();
        sp.repeat = repeat; sp.lunar = lunar; sp.allDay = allDay == null || allDay.isChecked();
        sp.interval = rInterval; sp.weekDays = repeat == EvSpec.WEEKLY ? rDays : 0;
        sp.untilDay = endMode == 1 ? untilD : Integer.MIN_VALUE;
        sp.count = endMode == 2 ? rCount : 0;
        return sp;
    }

    void updateRepeatSum() {
        if (repeatSum == null) return;
        if (untilBtn != null && untilD != Integer.MIN_VALUE) untilBtn.setText("~ " + ShiftData.ymd(untilD).replace("-", "."));
        repeatSum.setText(repeat == EvSpec.NONE ? "" : "🔁 " + repeatPart().repeatText());
    }

    /** 음력 체크: 고른 날짜의 음력으로 시작, 날짜 버튼 옆에 음력 표시 */
    void setLunar(boolean on) {
        lunar = on;
        if (lunarChk.isChecked() != on) { lunarChk.setChecked(on); lunarChk.jumpDrawablesToCurrentState(); }
        lunarRow.setVisibility(on ? View.VISIBLE : View.GONE);
        if (on) {
            int[] lt = Lunar.fromSolar(fDay0);
            if (lt != null) { lunarM = lt[1]; lunarD = lt[2]; lunarLeap = lt[3] == 1; }
            updateLunarBtn();
            if (repeat == EvSpec.WEEKLY || repeat == EvSpec.MONTHLY) setRepeat(EvSpec.YEARLY);
        }
        if (dateBtn != null) onDateChanged(false);
    }

    /** 색 고르기: 0 = 자동(내 색) / 수정할 땐 0 = 그대로 */
    void fillColors(boolean editMode) {
        colorRow.removeAllViews();
        for (int i = 0; i <= 11; i++) {
            final int id = i;
            TextView t;
            if (i == 0) { t = chipBtn(editMode ? "그대로" : "자동", colorSel == 0); }
            else {
                t = new TextView(this);
                t.setGravity(Gravity.CENTER);
                t.setText(colorSel == i ? "✓" : "");
                t.setTextColor(0xFFFFFFFF); t.setTextSize(16); t.setTypeface(Typeface.DEFAULT_BOLD);
                GradientDrawable g = new GradientDrawable(); g.setShape(GradientDrawable.OVAL); g.setColor(FamColors.hex(i));
                if (colorSel == i) g.setStroke(px(3), 0xFF161C24);
                t.setBackground(g);
            }
            t.setContentDescription(i == 0 ? "자동" : FamColors.NAMES[i]);
            t.setOnClickListener(v -> { colorSel = id; fillColors(editMode); });
            LinearLayout.LayoutParams lp = i == 0 ? gap() : new LinearLayout.LayoutParams(px(34), px(34));
            if (i > 0) lp.rightMargin = px(6);
            colorRow.addView(t, lp);
        }
    }

    void showRemindTime() {
        if (remindTimeBtn == null) return;
        remindTimeBtn.setVisibility(remindDay >= 0 ? View.VISIBLE : View.GONE);
        remindTimeBtn.setText(hm(remindAt) + " 에");
    }

    void updateLunarBtn() { lunarBtn.setText(Lunar.text(lunarM, lunarD, lunarLeap) + "  ·  바꾸기"); }

    /** 음력 날짜 고르기 → 다가오는 그 음력날로 날짜 맞추기 */
    void pickLunar() {
        LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.HORIZONTAL); box.setGravity(Gravity.CENTER);
        box.setPadding(px(16), px(8), px(16), px(8));
        android.widget.NumberPicker mp = new android.widget.NumberPicker(this), dp = new android.widget.NumberPicker(this);
        mp.setMinValue(1); mp.setMaxValue(12); mp.setValue(lunarM);
        dp.setMinValue(1); dp.setMaxValue(30); dp.setValue(lunarD);
        CheckBox lp = new CheckBox(this); lp.setText("윤달"); lp.setChecked(lunarLeap);
        box.addView(mp); box.addView(label("월", 16, false, 0xFF161C24)); box.addView(dp); box.addView(label("일", 16, false, 0xFF161C24)); box.addView(lp);
        new android.app.AlertDialog.Builder(this).setTitle("음력 날짜").setView(box)
                .setPositiveButton("확인", (d, w) -> {
                    lunarM = mp.getValue(); lunarD = dp.getValue(); lunarLeap = lp.isChecked();
                    updateLunarBtn();
                    int[] lt = Lunar.fromSolar(ShiftData.todayNum());
                    int y = lt != null ? lt[0] : 2026, s = Lunar.MIN;
                    for (int k = 0; k < 2 && (s == Lunar.MIN || s < ShiftData.todayNum()); k++) s = Lunar.yearly(y + k, lunarM, lunarD, lunarLeap);
                    if (s != Lunar.MIN) { int span = fDay1 - fDay0; fDay0 = s; fDay1 = s + Math.max(0, span); }
                    onDateChanged(false);
                }).setNegativeButton("취소", null).show();
    }

    void fillRemind(boolean editMode) {
        int[] base = EvSpec.REMIND_DAYS;
        String[] names = EvSpec.REMIND_DAY_NAMES;
        if (editMode) {
            int[] v = new int[base.length + 1]; String[] nm = new String[names.length + 1];
            v[0] = -3; nm[0] = "🔔 알림: 그대로";
            System.arraycopy(base, 0, v, 1, base.length); System.arraycopy(names, 0, nm, 1, names.length);
            base = v; names = nm;
        }
        int keep = remindDay;
        remindVals = base;
        remindSpin.setAdapter(ad(names));
        int sel = 0; for (int i = 0; i < base.length; i++) if (base[i] == keep) sel = i;
        remindSpin.setSelection(sel);
        remindDay = base[sel];
        showRemindTime();
    }

    void setRemind(int day, int at) { remindDay = day; remindAt = at; for (int i = 0; i < remindVals.length; i++) if (remindVals[i] == day) remindSpin.setSelection(i); showRemindTime(); }

    /** 자주 쓰는 일정: 종일·반복·알림을 알맞게 */
    void applyTemplate(String k) {
        tpl = tpl.equals(k) ? "" : k;
        for (TextView t : tplChips) {
            boolean on = tpl.equals(t.getTag());
            t.setTextColor(on ? 0xFFFFFFFF : 0xFF1D5D8F);
            GradientDrawable g = new GradientDrawable(); g.setCornerRadius(px(16)); g.setColor(on ? 0xFF1D5D8F : 0xFFE3EEF7); t.setBackground(g);
        }
        if (tpl.isEmpty()) { title.setHint("일정 제목 (예: 치과 15:00)"); return; }
        switch (k) {
            case "생일":
                allDay.setChecked(true); setRepeat(EvSpec.YEARLY); labelSpin.setSelection(2); label = 2;
                title.setHint("누구 생일? (예: 장모님 생신)"); break;
            case "기념일":
                allDay.setChecked(true); setRepeat(EvSpec.YEARLY); labelSpin.setSelection(1); label = 1;
                title.setHint("무슨 기념일? (예: 결혼)"); break;
            default:
                allDay.setChecked(true); setRepeat(EvSpec.NONE);
        }
        allDay.jumpDrawablesToCurrentState();
        fillRemind(false);
        setRemind(1, 9 * 60);
        baseYearEt.setVisibility(label == 0 ? View.GONE : View.VISIBLE);
        title.requestFocus();
    }

    void setEditUi(boolean on) {
        int vis = on ? View.VISIBLE : View.GONE;
        delBtn.setVisibility(vis); cancelEdit.setVisibility(vis); copyBtn.setVisibility(vis);
        openCal.setVisibility(on && editing != null && editing.eventId > 0 ? View.VISIBLE : View.GONE);
        calSpin.setVisibility(on ? View.GONE : View.VISIBLE);
        calLabel.setVisibility(on ? View.GONE : View.VISIBLE);
        tplScroll.setVisibility(on ? View.GONE : View.VISIBLE);
        repeatBox.setVisibility(on ? View.GONE : View.VISIBLE);
        String kind = "";
        if (on && editing != null) kind = editing.recurId != null || editing.rrule ? " · 반복 일정" : editing.series != null ? " · 해마다 일정" : "";
        formTitle.setText(on ? "일정 수정하기" + kind + (editing != null && !editing.who.isEmpty() ? " (" + editing.who + "가 만든 일정)" : "") : "＋ 일정 추가");
        saveBtn.setText(on ? "수정 내용 저장" : "일정 저장");
        fillRemind(on);
        colorSel = 0; fillColors(on);
        lunarChk.setVisibility(on ? View.GONE : View.VISIBLE);
        if (on) { lunar = false; lunarRow.setVisibility(View.GONE); }
    }

    /** 일정을 눌렀을 때: 아래 칸에 내용을 채워 고치기 */
    void startEdit(CalendarReader.Ev e) {
        if (e.memo) { Toast.makeText(this, "메모는 근무표 앱 '내 근무'에서 고쳐요", Toast.LENGTH_SHORT).show(); return; }
        if (e.gid == null && e.eventId <= 0) { Toast.makeText(this, "이 일정은 여기서 고칠 수 없어요", Toast.LENGTH_SHORT).show(); return; }
        editing = e;
        title.setText(e.title);
        memo.setText(EvSpec.stripTag(e.desc));
        boolean all = e.startMin < 0;
        allDay.setChecked(all);
        if (!all) { startMin = e.startMin; endMin = e.endMin >= 0 ? e.endMin : Math.min(e.startMin + 60, 1439); }
        fDay0 = e.day0 != Integer.MIN_VALUE ? e.day0 : day;
        fDay1 = e.day1 != Integer.MIN_VALUE && e.day1 >= fDay0 ? e.day1 : fDay0;
        remindDay = -3;
        setEditUi(true);
        onDateChanged(false);
        // 고치는 칸이 화면 아래에 있어도 바로 보이게 스크롤 + 강조
        GradientDrawable fb = new GradientDrawable(); fb.setColor(0xFFE8F1F8); fb.setCornerRadius(px(14)); fb.setStroke(px(2), 0xFF1D5D8F);
        form.setBackground(fb);
        sv.post(() -> sv.smoothScrollTo(0, Math.max(0, form.getTop() - px(8))));
        title.requestFocus();
        title.setSelection(title.getText().length());
        Toast.makeText(this, e.rrule && e.gid == null ? "반복 일정이라 날짜는 캘린더 앱에서 바꿔 주세요" : "날짜·시간·제목을 고친 뒤 [수정 내용 저장]", Toast.LENGTH_SHORT).show();
    }

    void stopEdit() {
        GradientDrawable fb = new GradientDrawable(); fb.setColor(0xFFF2F4F7); fb.setCornerRadius(px(14));
        form.setBackground(fb);
        editing = null; title.setText(""); memo.setText(""); allDay.setChecked(true);
        fDay0 = day; fDay1 = day; remindDay = -2;
        setEditUi(false); onDateChanged(false);
    }

    /** 고치던 일정 내용으로 새 일정 만들기 (다른 날로 복사) */
    void copyAsNew() {
        GradientDrawable fb = new GradientDrawable(); fb.setColor(0xFFF2F4F7); fb.setCornerRadius(px(14));
        form.setBackground(fb);
        title.setText(EvSpec.baseTitle(title.getText().toString()));
        editing = null; remindDay = -2;
        setEditUi(false);
        Toast.makeText(this, "날짜를 고르고 [일정 저장]을 누르면 복사돼요", Toast.LENGTH_LONG).show();
        dateBtn.performClick();
    }

    interface Choice { void on(int which); }
    /** 반복 일정: 이 일정만 / 전부 고르기 */
    void askScope(String what, CalendarReader.Ev e, Choice cb) {
        if (e.recurId != null) {
            new android.app.AlertDialog.Builder(this).setTitle("반복 일정 " + what).setItems(new String[]{"이 일정만", "반복 일정 전체"}, (d, w) -> cb.on(w)).setNegativeButton("취소", null).show();
        } else if (e.series != null) {
            new android.app.AlertDialog.Builder(this).setTitle("해마다 일정 " + what).setItems(new String[]{"이 일정만", "이 날부터 앞으로 전부"}, (d, w) -> cb.on(w)).setNegativeButton("취소", null).show();
        } else cb.on(0);
    }

    void confirmDelete(final CalendarReader.Ev e) {
        if (e == null) return;
        if (e.rrule && e.gid == null) {   // 휴대폰 캘린더의 반복 일정은 통째로
            new android.app.AlertDialog.Builder(this).setTitle("반복 일정 지우기").setMessage("'" + e.title + "' 반복 일정 전체가 지워져요. 하루만 지우려면 캘린더 앱에서 지워 주세요.")
                    .setPositiveButton("전체 지우기", (d, w) -> doDelete(e, 1)).setNegativeButton("취소", null).show();
            return;
        }
        askScope("지우기", e, which -> {
            if (which == 0 && e.recurId == null && e.series == null)
                new android.app.AlertDialog.Builder(this).setTitle("일정 지우기").setMessage("'" + e.title + "' 일정을 지울까요?" + (e.gid != null ? "\n가족 캘린더에서도 지워져요." : ""))
                        .setPositiveButton("지우기", (d, w) -> doDelete(e, 0)).setNegativeButton("취소", null).show();
            else doDelete(e, which);
        });
    }

    /** which 0 = 이 일정만, 1 = 전체(반복) / 앞으로 전부(해마다) */
    void doDelete(final CalendarReader.Ev e, final int which) {
        delBtn.setEnabled(false);
        Toast.makeText(this, "지우는 중…", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            boolean ok;
            if (e.gid != null) {
                if (which == 1 && e.recurId != null) ok = FamApi.delete(this, e.recurId);
                else if (which == 1 && e.series != null) {
                    ok = true; int from = e.day0;
                    for (String[] x : FamApi.series(this, e.series)) if (Integer.parseInt(x[2]) >= from) ok &= FamApi.deleteNoRefresh(this, x[0]);
                    FamApi.refresh(this);
                } else ok = FamApi.delete(this, e.gid);
            } else if (!CalendarReader.canWrite(this)) ok = false;
            else {
                long cal = CalendarReader.calendarOf(this, e.eventId);
                if (which == 1 && e.series != null) {
                    ok = true;
                    for (String[] x : CalendarReader.series(this, e.series)) if (Integer.parseInt(x[2]) >= e.day0) ok &= CalendarReader.delete(this, Long.parseLong(x[0]));
                } else ok = CalendarReader.delete(this, e.eventId);
                if (ok && cal >= 0) CalendarReader.syncCalendar(this, cal);
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
    }

    void fillCals() {
        if (!CalendarReader.allowed(this)) {
            cals = new ArrayList<>();
            calSpin.setAdapter(ad(new String[]{"저장할 때 캘린더 권한을 물어봐요"}));
            return;
        }
        cals = CalendarReader.writable(this);
        List<String> names = new ArrayList<>();
        for (CalendarReader.Cal c : cals) names.add(c.name + "  (" + c.account + ")");
        if (names.isEmpty()) names.add("일정을 넣을 수 있는 캘린더가 없어요");
        calSpin.setAdapter(ad(names));
        long last = Store.p(this).getLong("lastCal", -1);
        for (int i = 0; i < cals.size(); i++) if (cals.get(i).id == last) calSpin.setSelection(i);
    }

    EvSpec specFromForm(String t) {
        EvSpec sp = new EvSpec();
        sp.title = t; sp.memo = memo.getText().toString().trim();
        sp.day0 = fDay0; sp.day1 = Math.max(fDay0, fDay1);
        sp.allDay = allDay.isChecked(); sp.startMin = startMin; sp.endMin = endMin;
        sp.repeat = repeat; sp.lunarM = lunarM; sp.lunarD = lunarD; sp.lunarLeap = lunarLeap;
        sp.remind = remindDay == -3 ? -3 : EvSpec.remindMinutes(remindDay, remindAt, allDay.isChecked(), startMin);
        sp.lunar = lunar; sp.color = colorSel;
        EvSpec rp = repeatPart();
        sp.interval = rp.interval; sp.weekDays = rp.weekDays; sp.untilDay = rp.untilDay; sp.count = rp.count;
        sp.alignStart();
        int by = 0; try { by = Integer.parseInt(baseYearEt.getText().toString().trim()); } catch (Exception ignored) {}
        sp.label = repeat == EvSpec.YEARLY && by >= 1900 && by <= 2100 ? label : EvSpec.LABEL_NONE;
        sp.baseYear = by;
        return sp;
    }

    void save() {
        String t = title.getText().toString().trim();
        if (t.isEmpty()) { title.setError("제목을 적어 주세요"); title.requestFocus(); return; }
        if (!tpl.isEmpty()) {   // 템플릿 이름이 제목에 없으면 붙여요 (예: 장모님 → 장모님 생일)
            if ("생일".equals(tpl) && !t.contains("생일") && !t.contains("생신")) t = t + " 생일";
        }
        if (!CalendarReader.allowed(this) || !CalendarReader.canWrite(this)) {
            requestPermissions(new String[]{Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR}, REQ_WRITE);
            return;
        }
        if (editing != null) { saveEdit(t); return; }
        int i = calSpin.getSelectedItemPosition();
        if (cals.isEmpty() || i < 0 || i >= cals.size()) { Toast.makeText(this, "저장할 캘린더가 없어요", Toast.LENGTH_LONG).show(); return; }
        if ((label != EvSpec.LABEL_NONE) && repeat == EvSpec.YEARLY && baseYearEt.getText().toString().trim().isEmpty()) {
            baseYearEt.setError(label == EvSpec.LABEL_AGE ? "태어난 해를 적어 주세요" : "시작 연도를 적어 주세요"); baseYearEt.requestFocus(); return;
        }
        final EvSpec sp = specFromForm(t);
        if (sp.repeat != EvSpec.NONE && endMode == 1 && untilD < sp.day0) { Toast.makeText(this, "끝 날짜가 시작보다 빨라요", Toast.LENGTH_LONG).show(); return; }
        if (sp.repeat != EvSpec.NONE && endMode == 2 && rCount <= 0) { countEt.setError("횟수를 적어 주세요"); return; }
        if (sp.remind == -9) { Toast.makeText(this, "알림 시각이 일정 시작보다 늦어요. 날짜나 시각을 바꿔 주세요", Toast.LENGTH_LONG).show(); return; }
        final int count = sp.occurrences().size();
        if (count == 0) { Toast.makeText(this, "음력 날짜를 확인해 주세요 (2049년까지 계산돼요)", Toast.LENGTH_LONG).show(); return; }
        final long calId = cals.get(i).id;
        Store.p(this).edit().putLong("lastCal", calId).apply();
        saveBtn.setEnabled(false);
        saveBtn.setText(count > 1 ? "저장 중… (" + count + "개)" : "저장 중…");
        final boolean fam = calId == ShiftSync.localId(this);
        new Thread(() -> {
            // 가족 공유 캘린더는 구글 서버에 바로 넣어요 → 아이폰에 바로 보여요
            int n = fam ? FamApi.insertSpec(this, sp) : -1;
            boolean viaApi = n > 0;
            if (n <= 0) {
                n = CalendarReader.insertSpec(this, calId, sp);
                if (n > 0) CalendarReader.syncCalendar(this, calId);   // 구글로 올라가게
            }
            final int done = n;
            WidgetUpdater.renderAll(this);
            runOnUiThread(() -> {
                if (done <= 0) { saveBtn.setEnabled(true); saveBtn.setText("일정 저장"); Toast.makeText(this, "일정을 저장하지 못했어요", Toast.LENGTH_LONG).show(); return; }
 if (done < count) { Toast.makeText(this, count + "개 중 " + done + "개만 저장됐어요 (구글이 잠시 막음). 나머지는 잠시 뒤 다시 넣어 주세요", Toast.LENGTH_LONG).show(); finish(); return; }
                String what = sp.lunar && sp.repeat == EvSpec.YEARLY ? "음력 매년 " + done + "년 치" : sp.expands() ? "매년 " + done + "년 치" :  sp.rrule() != null ? sp.repeatText() + " 반복" : ShiftData.mdw(sp.day0);
                Toast.makeText(this, what + " 일정을 추가했어요" + (viaApi ? " (가족에게 바로 보여요)" : ""), Toast.LENGTH_LONG).show();
                finish();
            });
            if (done > 0) CalUpload.upload(this, false);
        }).start();
    }

    void saveEdit(String t) {
        final CalendarReader.Ev e = editing;
        if (e.rrule && e.gid == null) {   // 휴대폰 캘린더 반복 일정은 캘린더 앱에서
            Toast.makeText(this, "휴대폰 캘린더의 반복 일정은 캘린더 앱에서 고쳐 주세요", Toast.LENGTH_LONG).show();
            openCal.performClick();
            return;
        }
        askScope("고치기", e, which -> doSaveEdit(e, t, which));
    }

    void doSaveEdit(final CalendarReader.Ev e, final String t, final int which) {
        final boolean ad = allDay.isChecked();
        final String mm = memo.getText().toString().trim();
        final int d0 = fDay0, d1 = Math.max(fDay0, fDay1), sm = startMin, em = endMin;
        final int rm = remindDay == -3 ? -3 : EvSpec.remindMinutes(remindDay, remindAt, ad, startMin);
        if (rm == -9) { Toast.makeText(this, "알림 시각이 일정 시작보다 늦어요", Toast.LENGTH_LONG).show(); return; }
        FamApi.editColor = colorSel; CalendarReader.editColor = colorSel;
        saveBtn.setEnabled(false); saveBtn.setText("저장 중…");
        new Thread(() -> {
            boolean ok;
            if (e.gid != null) {   // 색은 처음 만든 사람 색 그대로
                if (which == 1 && e.recurId != null) ok = FamApi.updateMaster(this, e.recurId, t, ad, sm, em, mm, rm);
                else if (which == 1 && e.series != null) {
                    ok = true; String base = EvSpec.baseTitle(t); int span = d1 - d0;
                    for (String[] x : FamApi.series(this, e.series)) {
                        int xd = Integer.parseInt(x[2]); if (xd < e.day0) continue;
                        ok &= FamApi.update2(this, x[0], base + EvSpec.suffixOf(x[1]), xd, xd + span, ad, sm, em, mm, rm, false);
                    }
                    FamApi.refresh(this);
                } else ok = FamApi.update2(this, e.gid, t, d0, d1, ad, sm, em, mm, rm, true);
            } else {
                long cal = CalendarReader.calendarOf(this, e.eventId);
                if (which == 1 && e.series != null) {
                    ok = true; String base = EvSpec.baseTitle(t); int span = d1 - d0;
                    String tagged = (mm.isEmpty() ? "" : mm) + "\n\n" + EvSpec.TAG + e.series;
                    for (String[] x : CalendarReader.series(this, e.series)) {
                        int xd = Integer.parseInt(x[2]); if (xd < e.day0) continue;
                        ok &= CalendarReader.update2(this, Long.parseLong(x[0]), base + EvSpec.suffixOf(x[1]), xd, xd + span, ad, sm, em, tagged, rm);
                    }
                } else ok = CalendarReader.update2(this, e.eventId, t, d0, d1, ad, sm, em,
                        e.series != null ? (mm.isEmpty() ? "" : mm) + "\n\n" + EvSpec.TAG + e.series : mm, rm);
                if (ok && cal >= 0) CalendarReader.syncCalendar(this, cal);
            }
            final boolean done = ok;
            WidgetUpdater.renderAll(this);
            if (done) CalUpload.upload(this, true);
            runOnUiThread(() -> {
                if (!done) { saveBtn.setEnabled(true); saveBtn.setText("수정 내용 저장"); Toast.makeText(this, "수정하지 못했어요", Toast.LENGTH_LONG).show(); return; }
                Toast.makeText(this, which == 1 ? "반복 일정을 모두 수정했어요" : "일정을 수정했어요", Toast.LENGTH_SHORT).show();
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
        int k0 = fDay0, k1 = fDay1, kr = repeat, km = lunarM, kd = lunarD, kl = label, krd = remindDay, kra = remindAt, kc = colorSel; boolean klp = lunarLeap, kln = lunar;
        String kby = baseYearEt != null ? baseYearEt.getText().toString() : "", ktpl = tpl;
        build();
        title.setText(keepT); memo.setText(keepM); allDay.setChecked(keepA);
        lunarM = km; lunarD = kd; lunarLeap = klp; fDay0 = k0; fDay1 = k1; tpl = ktpl;
        if (kln) setLunar(true);
        lunarM = km; lunarD = kd; lunarLeap = klp; fDay0 = k0; fDay1 = k1;
        setRepeat(kr); label = kl; labelSpin.setSelection(kl); baseYearEt.setText(kby);
        fillRemind(false); setRemind(krd, kra);
        colorSel = kc; fillColors(false);
        if (kln) updateLunarBtn();
        onDateChanged(false);
        if (code == REQ_WRITE && CalendarReader.canWrite(this)) save();
    }
}
