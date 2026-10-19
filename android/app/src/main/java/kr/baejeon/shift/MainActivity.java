package kr.baejeon.shift;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;

import androidx.browser.customtabs.CustomTabsService;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import com.google.androidbrowserhelper.trusted.TwaLauncher;
import java.util.List;

import android.app.AlertDialog;
import android.net.Uri;
import android.provider.Settings;
import android.widget.Toast;
import androidx.core.content.FileProvider;
import org.json.JSONObject;

/**
 * 앱 아이콘을 누르면 크롬 엔진으로 근무표 웹앱을 앱처럼 열어요 (구글 로그인·알림 그대로).
 * 처음 한 번은 알림 허용을 먼저 묻고, 답을 받은 뒤에 근무표를 열어요.
 * (갤럭시 앱에서는 웹 알림이 이 앱의 알림 권한을 따라가요.)
 */
public class MainActivity extends com.google.androidbrowserhelper.trusted.LauncherActivity {
    static final int REQ = 7, REQ_SRC = 9;
    private boolean asking, launched;
    private String news;          // 업데이트 후 '이번에 바뀐 점'
    private JSONObject offer;     // 물어볼 새 버전

    @Override
    protected boolean shouldLaunchImmediately() {
        asking = needAsk(this, false);
        news = Updater.whatsNew(this);
        Updater.checkBriefly(this, 1500);
        offer = Updater.offer(this);
        return !asking && news == null && offer == null;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WidgetUpdater.refreshAsync(this, null);
        Updater.checkAsync(this);
        if (asking) {
            Store.p(this).edit().putBoolean("askedNotify", true).apply();
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ);
        } else if (news != null || offer != null) {
            next();
        }
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        super.onRequestPermissionsResult(code, perms, res);
        if (code == REQ && asking) { asking = false; next(); }
    }

    /** 알림 허용 → 이번에 바뀐 점 → 새 버전 물어보기 → 근무표 순서로 */
    void next() {
        if (isFinishing()) return;
        if (news != null) {
            String t = news; news = null;
            AlertDialog d = dlg().setTitle("업데이트 완료! 이렇게 바뀌었어요")
                    .setMessage(t)
                    .setPositiveButton("확인", null)
                    .create();
            d.setOnDismissListener(x -> { Updater.markSeen(this); next(); });
            d.show();
            return;
        }
        if (offer != null) {
            JSONObject j = offer; offer = null;
            int code = j.optInt("code");
            String body = Updater.offerText(this, j);
            boolean[] go = {false};
            AlertDialog d = dlg().setTitle("새 버전이 있어요 (" + j.optString("name", "1.0." + code) + ")")
                    .setMessage((body.isEmpty() ? "" : body + "\n\n") + "지금 업데이트할까요?")
                    .setPositiveButton("업데이트", (x, w) -> go[0] = true)
                    .setNegativeButton("나중에", null)
                    .create();
            d.setOnDismissListener(x -> {
                if (go[0]) startUpdate(code);
                else { Updater.later(this, code); next(); }
            });
            d.show();
            return;
        }
        go();
    }

    void go() { if (!launched && !isFinishing()) { launched = true; launchTwa(); } }

    AlertDialog.Builder dlg() {
        return new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Light_Dialog_Alert);
    }

    private int upCode;

    /** 새 APK 받기 → (처음 한 번) 설치 허용 → 설치 화면 */
    void startUpdate(int code) {
        upCode = code;
        AlertDialog pd = dlg().setTitle("업데이트 받는 중").setMessage("잠시만요… 0%").setCancelable(false).create();
        pd.show();
        new Thread(() -> {
            boolean ok = Updater.download(this, code,
                    pct -> runOnUiThread(() -> pd.setMessage("잠시만요… " + pct + "%")));
            runOnUiThread(() -> {
                if (isFinishing()) return;
                pd.dismiss();
                if (ok) install();
                else { Toast.makeText(this, "새 버전을 받지 못했어요. 인터넷을 확인하고 다음에 다시 해 주세요.", Toast.LENGTH_LONG).show(); go(); }
            });
        }).start();
    }

    void install() {
        if (!getPackageManager().canRequestPackageInstalls()) {
            AlertDialog d = dlg().setTitle("처음 한 번만 설정해 주세요")
                    .setMessage("다음 화면에서 '이 출처 허용'(또는 '출처를 알 수 없는 앱 설치')을 켜고, 뒤로가기를 누르면 바로 업데이트가 이어져요.")
                    .setPositiveButton("설정 열기", (x, w) -> {
                        try {
                            startActivityForResult(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                    Uri.parse("package:" + getPackageName())), REQ_SRC);
                        } catch (Exception e) { go(); }
                    })
                    .setNegativeButton("취소", (x, w) -> go())
                    .setCancelable(false)
                    .create();
            d.show();
            return;
        }
        try {
            Uri u = FileProvider.getUriForFile(this, getPackageName() + ".share", Updater.apkFile(this));
            Intent i = new Intent(Intent.ACTION_VIEW)
                    .setDataAndType(u, "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
            finish();   // 설치 화면에서 [업데이트]를 누르면 새 버전으로 바뀌어요
        } catch (Exception e) {
            Toast.makeText(this, "설치 화면을 열지 못했어요.", Toast.LENGTH_LONG).show();
            go();
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != REQ_SRC) return;
        if (getPackageManager().canRequestPackageInstalls() && Updater.ready(this, upCode)) install();
        else { Toast.makeText(this, "설치 허용이 꺼져 있어 업데이트를 못 했어요. 다음에 앱을 열면 다시 물어볼게요.", Toast.LENGTH_LONG).show(); go(); }
    }

    /** 웹앱에 앱 버전을 알려줘요 (?appv=…): 이 값이 있어야 웹이 '위젯 바로 새로 고침'을 불러요 (옛 앱은 그 기능이 없어서) */
    @Override
    protected Uri getLaunchingUrl() {
        Uri u = super.getLaunchingUrl();
        try { return u.buildUpon().appendQueryParameter("appv", String.valueOf(Updater.myCode(this))).build(); }
        catch (Exception e) { return u; }
    }

    /**
     * 기본 브라우저가 삼성 인터넷이어도 크롬으로 열어요.
     * 크롬이어야 알림이 '근무표' 앱 아이콘으로 오고, 알림을 누르면 이 앱으로 열려요.
     * (크롬이 없거나 꺼져 있으면 원래대로 기본 브라우저)
     */
    @Override
    protected TwaLauncher createTwaLauncher() {
        String chrome = pickChrome();
        return chrome != null ? new TwaLauncher(this, chrome) : super.createTwaLauncher();
    }

    String pickChrome() {
        String[] prefs = {"com.android.chrome", "com.chrome.beta"};
        PackageManager pm = getPackageManager();
        Intent svc = new Intent(CustomTabsService.ACTION_CUSTOM_TABS_CONNECTION);
        for (String p : prefs) {
            svc.setPackage(p);
            List<ResolveInfo> r = pm.queryIntentServices(svc, 0);
            if (r != null && !r.isEmpty()) return p;
        }
        return null;
    }

    /** 알림 권한을 물어야 하는지 (force = 이미 물었어도 다시) */
    static boolean needAsk(Activity a, boolean force) {
        if (Build.VERSION.SDK_INT < 33) return false;
        if (a.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return false;
        return force || !Store.p(a).getBoolean("askedNotify", false);
    }
}
