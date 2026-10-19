package kr.baejeon.shift;

import android.app.Activity;
import android.os.Bundle;

/**
 * 웹앱에서 메모 저장·근무 등록/취소 직후 불러요: 화면 없이 위젯만 바로 새로 받아 그려요.
 * (원래는 30분마다 또는 앱을 열 때만 새로 받았어요)
 */
public class RefreshActivity extends Activity {
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        final android.content.Context c = getApplicationContext();
        new Thread(() -> { try { WidgetUpdater.fetch(c); WidgetUpdater.renderAll(c); } catch (Exception ignored) {} }).start();
        finish();
        overridePendingTransition(0, 0);
    }
}
