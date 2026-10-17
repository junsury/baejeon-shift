package kr.baejeon.shift;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;

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

    /** 알림 권한을 물어야 하는지 (force = 이미 물었어도 다시) */
    static boolean needAsk(Activity a, boolean force) {
        if (Build.VERSION.SDK_INT < 33) return false;
        if (a.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return false;
        return force || !Store.p(a).getBoolean("askedNotify", false);
    }
}
