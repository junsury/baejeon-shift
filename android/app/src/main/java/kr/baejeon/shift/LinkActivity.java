package kr.baejeon.shift;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.widget.Toast;

/**
 * 웹앱에서 넘어오는 요청
 *  baejeonshift://link?t=토큰  → 위젯 연결
 *  baejeonshift://notify      → 앱 알림 허용 (허용 후 근무표를 다시 열어 웹 알림에 반영)
 */
public class LinkActivity extends Activity {
    boolean wentToSettings = false;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Uri u = getIntent() != null ? getIntent().getData() : null;
        if (u != null && "notify".equals(u.getHost())) { notifyFlow(); return; }
        String t = u != null ? u.getQueryParameter("t") : null;
        if (t == null || !t.matches("[A-Za-z0-9]{12,40}")) {
            Toast.makeText(this, "연결 정보가 올바르지 않아요", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        Store.setToken(this, t);
        Store.setUnlinked(this, false);
        final Handler h = new Handler(Looper.getMainLooper());
        final Activity me = this;
        new Thread(() -> {
            boolean ok = WidgetUpdater.fetch(me);
            WidgetUpdater.renderAll(me);
            WidgetUpdater.scheduleMidnight(me);
            ShiftData d = ShiftData.parse(Store.data(me));
            h.post(() -> {
                Toast.makeText(me, ok && d != null ? "위젯 연결 완료 (" + d.name + ")\n홈 화면을 길게 눌러 위젯을 추가하세요"
                        : "연결 정보를 저장했어요. 인터넷이 되면 위젯이 채워져요", Toast.LENGTH_LONG).show();
                finish();
            });
        }).start();
    }

    boolean granted() {
        return Build.VERSION.SDK_INT < 33 || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
    }

    void notifyFlow() {
        if (granted() && notificationsOn()) { reopen(); return; }
        if (Build.VERSION.SDK_INT >= 33 && !granted() && shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)
                || Build.VERSION.SDK_INT >= 33 && !granted() && !Store.p(this).getBoolean("askedNotify", false)) {
            Store.p(this).edit().putBoolean("askedNotify", true).apply();
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 8);
            return;
        }
        openSettings();
    }

    boolean notificationsOn() {
        android.app.NotificationManager nm = getSystemService(android.app.NotificationManager.class);
        return nm == null || nm.areNotificationsEnabled();
    }

    void openSettings() {
        wentToSettings = true;
        Toast.makeText(this, "알림을 켠 뒤 뒤로 가기를 눌러 주세요", Toast.LENGTH_LONG).show();
        try {
            startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName()));
        } catch (Exception e) {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName())));
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (wentToSettings) { wentToSettings = false; reopen(); }
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        super.onRequestPermissionsResult(code, perms, res);
        if (granted()) reopen();
        else openSettings();
    }

    /** 근무표 화면을 새로 열어 크롬이 바뀐 알림 권한을 읽게 해요 */
    void reopen() {
        if (granted() && notificationsOn())
            Toast.makeText(this, "알림이 허용됐어요. 👤 메뉴에서 [알림 켜기]를 한 번 더 눌러 주세요", Toast.LENGTH_LONG).show();
        Intent i = new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        finish();
    }
}
