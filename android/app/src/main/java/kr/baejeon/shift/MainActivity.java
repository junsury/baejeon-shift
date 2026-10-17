package kr.baejeon.shift;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;

/**
 * 앱 아이콘을 누르면: (처음 한 번) 알림 허용을 묻고 → 근무표 화면을 열어요.
 * 갤럭시 앱에서는 웹 알림이 앱 알림 권한을 따라가서, 이 권한이 있어야 알림 켜기가 돼요.
 */
public class MainActivity extends Activity {
    static final int REQ = 7;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        WidgetUpdater.refreshAsync(this, null);
        if (needAsk(this, false)) {
            Store.p(this).edit().putBoolean("askedNotify", true).apply();
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ);
        } else open();
    }

    /** 알림 권한을 물어야 하는지 (force = 이미 물었어도 다시) */
    static boolean needAsk(Activity a, boolean force) {
        if (Build.VERSION.SDK_INT < 33) return false;
        if (a.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return false;
        return force || !Store.p(a).getBoolean("askedNotify", false);
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        super.onRequestPermissionsResult(code, perms, res);
        open();
    }

    void open() {
        Intent i = new Intent(this, TwaActivity.class);
        startActivity(i);
        finish();
        overridePendingTransition(0, 0);
    }
}
