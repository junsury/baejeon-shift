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

/**
 * 앱 아이콘을 누르면 크롬 엔진으로 근무표 웹앱을 앱처럼 열어요 (구글 로그인·알림 그대로).
 * 처음 한 번은 알림 허용을 먼저 묻고, 답을 받은 뒤에 근무표를 열어요.
 * (갤럭시 앱에서는 웹 알림이 이 앱의 알림 권한을 따라가요.)
 */
public class MainActivity extends com.google.androidbrowserhelper.trusted.LauncherActivity {
    static final int REQ = 7;
    private boolean asking;

    @Override
    protected boolean shouldLaunchImmediately() {
        asking = needAsk(this, false);
        return !asking;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WidgetUpdater.refreshAsync(this, null);
        if (asking) {
            Store.p(this).edit().putBoolean("askedNotify", true).apply();
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ);
        }
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        super.onRequestPermissionsResult(code, perms, res);
        if (code == REQ && asking) { asking = false; launchTwa(); }
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
