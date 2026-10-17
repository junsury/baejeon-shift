package kr.baejeon.shift;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** 자정 알람 → 새로 받고 다시 그리기 */
public class RefreshReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        final PendingResult pr = goAsync();
        WidgetUpdater.renderAll(c);
        WidgetUpdater.refreshAsync(c, pr::finish);
    }
}
