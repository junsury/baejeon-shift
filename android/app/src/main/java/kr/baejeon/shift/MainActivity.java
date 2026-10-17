package kr.baejeon.shift;

import android.os.Bundle;

/** 앱 아이콘을 누르면 크롬 엔진으로 근무표 웹앱을 앱처럼 열어요 (구글 로그인·알림 그대로). */
public class MainActivity extends com.google.androidbrowserhelper.trusted.LauncherActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WidgetUpdater.refreshAsync(this, null);
    }
}
