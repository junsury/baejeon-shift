package kr.baejeon.shift;

import android.app.Activity;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

/** 웹앱의 [갤럭시 위젯 연결] 버튼 → baejeonshift://link?t=토큰 */
public class LinkActivity extends Activity {
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Uri u = getIntent() != null ? getIntent().getData() : null;
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
}
