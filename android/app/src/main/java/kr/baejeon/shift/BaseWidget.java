package kr.baejeon.shift;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

/** 네 가지 위젯의 공통 동작 */
public abstract class BaseWidget extends AppWidgetProvider {
    static final String ACT_PREV = "kr.baejeon.shift.PREV";
    static final String ACT_NEXT = "kr.baejeon.shift.NEXT";

    @Override
    public void onUpdate(Context c, AppWidgetManager m, int[] ids) {
        for (int id : ids) WidgetUpdater.render(c, m, id);
        final PendingResult pr = goAsync();
        WidgetUpdater.refreshAsync(c, pr::finish);
    }

    @Override
    public void onAppWidgetOptionsChanged(Context c, AppWidgetManager m, int id, Bundle o) {
        WidgetUpdater.render(c, m, id);
    }

    @Override
    public void onDeleted(Context c, int[] ids) {
        for (int id : ids) Store.remove(c, id);
    }

    @Override
    public void onReceive(Context c, Intent i) {
        String a = i.getAction();
        if (ACT_PREV.equals(a) || ACT_NEXT.equals(a)) {
            int id = i.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 0);
            Store.Cfg f = Store.cfg(c, id, "month");
            int off = f.monthOffset + (ACT_PREV.equals(a) ? -1 : 1);
            off = Math.max(-12, Math.min(12, off));   // 앞뒤로 1년
            Store.setOffset(c, id, off);
            WidgetUpdater.render(c, AppWidgetManager.getInstance(c), id);
            return;
        }
        super.onReceive(c, i);
    }
}
