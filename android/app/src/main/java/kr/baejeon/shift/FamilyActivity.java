package kr.baejeon.shift;

import android.Manifest;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.util.Patterns;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.auth.api.identity.AuthorizationRequest;
import com.google.android.gms.auth.api.identity.AuthorizationResult;
import com.google.android.gms.auth.api.identity.Identity;
import com.google.android.gms.common.api.Scope;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 가족 공유 캘린더 (구글)
 *  - 내 구글 계정에 "근무표" 캘린더를 만들고, 가족 이메일로 공유(쓰기 권한)
 *  - 내 근무가 그 캘린더에 종일 일정으로 자동으로 들어가요 → 가족 폰(아이폰·안드로이드) 캘린더에 보여요
 *  - 가족이 그 캘린더에 넣은 일정은 내 위젯에 보여요
 */
public class FamilyActivity extends Activity {
    static final int REQ_AUTH = 21, REQ_PERM = 22;
    float dp;
    LinearLayout col, shareBox;
    TextView status;
    String token;
    Runnable afterAuth;
    final ExecutorService bg = Executors.newSingleThreadExecutor();
    final Handler ui = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        dp = getResources().getDisplayMetrics().density;
        build();
    }

    int px(float v) { return Math.round(v * dp); }
    TextView label(String s, float size, boolean bold, int color) {
        TextView t = new TextView(this); t.setText(s); t.setTextSize(size); t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD); t.setLineSpacing(0, 1.15f); return t;
    }
    LinearLayout.LayoutParams mlp(int top) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = px(top); return lp;
    }
    LinearLayout card() {
        LinearLayout c = new LinearLayout(this); c.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable g = new GradientDrawable(); g.setColor(0xFFFFFFFF); g.setCornerRadius(px(14)); c.setBackground(g);
        c.setPadding(px(16), px(14), px(16), px(16)); return c;
    }

    String calId() { return Store.p(this).getString("famCalId", ""); }

    void build() {
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(0xFFF4F6F8);
        col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(px(18), px(20), px(18), px(28));
        sv.addView(col);
        col.addView(label("가족 공유 캘린더", 22, true, 0xFF161C24));
        col.addView(label("내 구글 계정에 '근무표' 캘린더를 만들어 가족과 함께 써요. 내 근무가 자동으로 들어가서 가족 폰(아이폰·안드로이드) 캘린더에 보이고, 가족이 넣은 일정은 내 위젯에 보여요.", 14, false, 0xFF4F5A67), mlp(6));
        status = label("", 13.5f, false, 0xFF1D5D8F);
        col.addView(status, mlp(8));

        if (calId().isEmpty()) {
            LinearLayout c = card();
            c.addView(label("1. 공유 캘린더 만들기", 16, true, 0xFF161C24));
            c.addView(label("구글 계정을 고르고 캘린더 권한을 허용하면 '근무표' 캘린더가 생겨요. 처음 한 번 '확인되지 않은 앱' 안내가 나오면 [고급] → [이동]을 누르세요 (팀에서 직접 만든 앱이라 나오는 안내예요).", 13.5f, false, 0xFF4F5A67), mlp(4));
            Button mk = new Button(this);
            mk.setText("구글 계정으로 공유 캘린더 만들기");
            mk.setOnClickListener(v -> withPerm(() -> withToken(this::create)));
            c.addView(mk, mlp(10));
            col.addView(c, mlp(12));
        } else {
            LinearLayout c = card();
            c.addView(label("근무표 캘린더", 16, true, 0xFF161C24));
            String acc = Store.p(this).getString("famAccount", "");
            c.addView(label((acc.isEmpty() ? "" : acc + "\n") + (ShiftSync.localId(this) >= 0 ? "✓ 이 휴대폰에 동기화됐어요" : "휴대폰에 동기화되는 중이에요 (몇 분 걸릴 수 있어요)"), 13.5f, false, 0xFF4F5A67), mlp(4));
            Switch sw = new Switch(this);
            sw.setText("내 근무 자동으로 넣기 (주간·야간·휴가, 종일 일정)");
            sw.setTextColor(0xFF161C24);
            sw.setChecked(Store.p(this).getBoolean("famShifts", true));
            sw.setOnCheckedChangeListener((bt, on) -> {
                Store.p(this).edit().putBoolean("famShifts", on).apply();
                bg.execute(() -> { if (on) ShiftSync.sync(this); else ShiftSync.clear(this); });
                toast(on ? "앞으로 2달 근무를 넣을게요" : "넣었던 근무 일정을 지웠어요");
            });
            c.addView(sw, mlp(10));
            Button now = new Button(this);
            now.setText("지금 다시 맞추기");
            now.setOnClickListener(v -> withPerm(() -> bg.execute(() -> {
                ShiftSync.requestSync(this);
                FamApi.refresh(this);
                WidgetUpdater.fetch(this);
                ShiftSync.sync(this);
                WidgetUpdater.renderAll(this);
                ui.post(() -> { toast("근무를 다시 맞췄어요"); build(); });
            })));
            c.addView(now, mlp(4));
            col.addView(c, mlp(12));

            LinearLayout s = card();
            s.addView(label("2. 가족에게 공유", 16, true, 0xFF161C24));
            s.addView(label("가족의 구글 계정 이메일을 넣으면 공유 초대 메일이 가요. 가족도 일정을 넣고 고칠 수 있어요(쓰기 권한).", 13.5f, false, 0xFF4F5A67), mlp(4));
            shareBox = new LinearLayout(this); shareBox.setOrientation(LinearLayout.VERTICAL);
            s.addView(shareBox, mlp(6));
            Button load = new Button(this);
            load.setText("공유 중인 사람 보기");
            load.setOnClickListener(v -> withToken(this::loadShares));
            s.addView(load, mlp(2));
            EditText em = new EditText(this);
            em.setHint("가족 이메일 (예: family@gmail.com)");
            em.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
            s.addView(em, mlp(6));
            Button add = new Button(this);
            add.setText("공유하기");
            add.setOnClickListener(v -> {
                String e = em.getText().toString().trim();
                if (!Patterns.EMAIL_ADDRESS.matcher(e).matches()) { em.setError("이메일을 확인해 주세요"); return; }
                withToken(() -> bg.execute(() -> {
                    try { GoogleCal.share(token, calId(), e); ui.post(() -> { em.setText(""); toast(e + " 님에게 공유했어요. 초대 메일을 확인하라고 알려 주세요"); loadShares(); }); }
                    catch (Exception ex) { fail("공유하지 못했어요", ex); }
                }));
            });
            s.addView(add, mlp(2));
            col.addView(s, mlp(12));

            LinearLayout h = card();
            h.addView(label("3. 가족 폰에서 한 번만", 16, true, 0xFF161C24));
            h.addView(label(FAMILY_HOWTO, 13.5f, false, 0xFF4F5A67), mlp(4));
            Button send = new Button(this);
            send.setText("가족에게 설명 보내기");
            send.setOnClickListener(v -> startActivity(Intent.createChooser(new Intent(Intent.ACTION_SEND).setType("text/plain")
                    .putExtra(Intent.EXTRA_TEXT, "근무표 캘린더를 공유했어요. 이렇게 한 번만 설정해 주세요.\n\n" + FAMILY_HOWTO), "설명 보내기")));
            h.addView(send, mlp(8));
            col.addView(h, mlp(12));

            Button forget = new Button(this);
            forget.setText("이 휴대폰에서 연결 끊기 (캘린더는 구글에 남아요)");
            forget.setOnClickListener(v -> {
                bg.execute(() -> ShiftSync.clear(this));
                Store.p(this).edit().remove("famCalId").remove("famAccount").remove("famEvents").apply();
                FamPollWorker.schedule(this);
                toast("연결을 끊었어요");
                build();
            });
            col.addView(forget, mlp(16));
        }
        setContentView(sv);
    }

    static final String FAMILY_HOWTO =
            "• 공통: 구글 계정으로 온 '캘린더 공유' 메일에서 [이 캘린더 추가]를 눌러요.\n"
            + "• 아이폰: 설정 → 캘린더 → 계정 → 계정 추가 → Google 로그인. 근무표 캘린더가 안 보이면 사파리에서 calendar.google.com/calendar/syncselect 에 들어가 '근무표'를 체크해요. 일정을 만들 때 캘린더를 '근무표'로 고르면 같이 보여요.\n"
            + "• 안드로이드: 구글 캘린더 앱 → 메뉴에서 '근무표'를 체크해요.\n"
            + "• 알림은 각자 폰에서 설정해요 (아이폰: 설정 → 캘린더 → 기본 알림 시간).";

    void toast(String s) { ui.post(() -> Toast.makeText(this, s, Toast.LENGTH_LONG).show()); }
    void fail(String what, Exception e) {
        String m = e.getMessage() == null ? "" : e.getMessage();
        if (e instanceof GoogleCal.Err && ((GoogleCal.Err) e).code == 401) token = null;
        if (m.contains("has not been used") || m.contains("disabled")) m = "구글 캘린더 API가 아직 켜지지 않았어요. 잠시 뒤 다시 해 주세요.";
        final String msg = what + ": " + m;
        ui.post(() -> { status.setText(msg); Toast.makeText(this, msg, Toast.LENGTH_LONG).show(); });
    }

    /** 휴대폰 캘린더 읽기·쓰기 권한 */
    void withPerm(Runnable r) {
        if (CalendarReader.allowed(this) && CalendarReader.canWrite(this)) { r.run(); return; }
        afterAuth = r;
        requestPermissions(new String[]{Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR}, REQ_PERM);
    }

    /** 구글 캘린더 권한 받기 (이미 허용했으면 화면 없이 바로) */
    void withToken(Runnable r) {
        if (token != null) { r.run(); return; }
        afterAuth = r;
        List<Scope> scopes = new ArrayList<>();
        for (String s : GoogleCal.SCOPES) scopes.add(new Scope(s));
        AuthorizationRequest req = AuthorizationRequest.builder().setRequestedScopes(scopes).build();
        status.setText("구글 계정 확인 중…");
        Identity.getAuthorizationClient(this).authorize(req)
                .addOnSuccessListener(res -> {
                    if (res.hasResolution()) {
                        PendingIntent pi = res.getPendingIntent();
                        try { startIntentSenderForResult(pi.getIntentSender(), REQ_AUTH, null, 0, 0, 0); }
                        catch (Exception e) { fail("구글 로그인 창을 열지 못했어요", e); }
                    } else gotToken(res);
                })
                .addOnFailureListener(e -> fail("구글 계정 연결 실패", e));
    }

    void gotToken(AuthorizationResult res) {
        token = res.getAccessToken();
        status.setText("");
        if (token == null) { fail("구글 권한을 받지 못했어요", new Exception("")); return; }
        Runnable r = afterAuth; afterAuth = null;
        if (r != null) r.run();
    }

    @Override
    protected void onActivityResult(int code, int result, Intent data) {
        super.onActivityResult(code, result, data);
        if (code != REQ_AUTH) return;
        if (result != RESULT_OK) { status.setText("구글 권한을 허용하지 않았어요"); return; }
        try { gotToken(Identity.getAuthorizationClient(this).getAuthorizationResultFromIntent(data)); }
        catch (Exception e) { fail("구글 권한을 받지 못했어요", e); }
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        super.onRequestPermissionsResult(code, perms, res);
        if (code != REQ_PERM) return;
        if (!(CalendarReader.allowed(this) && CalendarReader.canWrite(this))) { status.setText("휴대폰 캘린더 권한을 허용해야 해요"); return; }
        Runnable r = afterAuth; afterAuth = null;
        if (r != null) r.run();
    }

    void create() {
        status.setText("캘린더를 만드는 중…");
        ShiftData d = ShiftData.parse(Store.data(this));
        String summary = "근무표" + (d != null && !d.name.isEmpty() ? " · " + d.name : "");
        bg.execute(() -> {
            try {
                String id = GoogleCal.createCalendar(token, summary);
                String owner = "";
                try { owner = GoogleCal.ownerOf(token, id); } catch (Exception ignored) {}
                Store.p(this).edit().putString("famCalId", id).putString("famAccount", owner).putBoolean("famShifts", true).apply();
                ShiftSync.requestSync(this);
                FamPollWorker.schedule(this);
                // 휴대폰에 캘린더가 내려오면 근무를 넣고, 일정 추가 기본 캘린더로 정해요
                for (int i = 0; i < 20 && ShiftSync.localId(this) < 0; i++) { Thread.sleep(3000); if (i % 5 == 4) ShiftSync.requestSync(this); }
                long local = ShiftSync.localId(this);
                if (local >= 0) { Store.p(this).edit().putLong("lastCal", local).apply(); ShiftSync.sync(this); WidgetUpdater.renderAll(this); }
                ui.post(() -> { toast("'" + summary + "' 캘린더를 만들었어요. 이제 가족 이메일을 넣어 공유하세요"); build(); });
            } catch (Exception e) { fail("캘린더를 만들지 못했어요", e); }
        });
    }

    void loadShares() {
        bg.execute(() -> {
            try {
                List<GoogleCal.Share> list = GoogleCal.listShares(token, calId());
                ui.post(() -> {
                    shareBox.removeAllViews();
                    if (list.isEmpty()) shareBox.addView(label("아직 공유한 사람이 없어요.", 13.5f, false, 0xFF8A94A0));
                    for (GoogleCal.Share sh : list) {
                        LinearLayout row = new LinearLayout(this);
                        row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(Gravity.CENTER_VERTICAL);
                        row.addView(label(sh.email + ("writer".equals(sh.role) ? "" : " (" + sh.role + ")"), 14.5f, false, 0xFF161C24),
                                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                        Button del = new Button(this);
                        del.setText("공유 끊기");
                        del.setOnClickListener(v -> withToken(() -> bg.execute(() -> {
                            try { GoogleCal.unshare(token, calId(), sh.ruleId); toast(sh.email + " 님 공유를 끊었어요"); ui.post(this::loadShares); }
                            catch (Exception e) { fail("공유를 끊지 못했어요", e); }
                        })));
                        row.addView(del);
                        shareBox.addView(row);
                    }
                });
            } catch (Exception e) { fail("공유 목록을 불러오지 못했어요", e); }
        });
    }
}
