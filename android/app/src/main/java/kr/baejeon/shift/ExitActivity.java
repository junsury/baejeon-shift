package kr.baejeon.shift;

import android.app.Activity;
import android.app.ActivityManager;
import android.os.Bundle;

/** 웹앱의 "앱을 종료할까요? → 예"에서 불러요: 근무표 앱 화면을 모두 닫아요 */
public class ExitActivity extends Activity {
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        try {
            ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
            for (ActivityManager.AppTask t : am.getAppTasks()) t.finishAndRemoveTask();
        } catch (Exception ignored) {}
        finishAndRemoveTask();
    }
}
