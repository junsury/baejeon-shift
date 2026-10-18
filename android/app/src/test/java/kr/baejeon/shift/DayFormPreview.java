package kr.baejeon.shift;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.view.View;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.io.File;
import java.io.FileOutputStream;

/** 위젯 날짜 창(일정 추가 칸) 미리보기 PNG — 예시 데이터 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, qualifiers = "w400dp-h2000dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class DayFormPreview {
    void shot(DayActivity a, String file) throws Exception {
        View root = a.sv;
        int w = 1100;
        root.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        View inner = a.col;
        inner.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        inner.layout(0, 0, w, inner.getMeasuredHeight());
        Bitmap b = Bitmap.createBitmap(w, Math.max(1, inner.getMeasuredHeight()), Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b); c.drawColor(Color.WHITE); inner.draw(c);
        File dir = new File("build/previews"); dir.mkdirs();
        try (FileOutputStream o = new FileOutputStream(new File(dir, file))) { b.compress(Bitmap.CompressFormat.PNG, 100, o); }
    }

    @Test public void form() throws Exception {
        Store.setData(RuntimeEnvironment.getApplication(), RenderTest.sample());
        DayActivity a = Robolectric.buildActivity(DayActivity.class, new Intent().putExtra("day", ShiftData.todayNum() + 3)).setup().get();
        shot(a, "day_form.png");
        a.applyTemplate("생일");
        a.setLunar(true);
        a.lunarM = 9; a.lunarD = 8; a.updateLunarBtn(); a.onDateChanged(false);
        a.colorSel = 3; a.fillColors(false);
        a.title.setText("장모님");
        a.baseYearEt.setText("1960");
        shot(a, "day_form_birthday.png");
        a.applyTemplate("생일");
        a.setLunar(false); a.allDay.setChecked(false); a.allDay.jumpDrawablesToCurrentState();
        a.startMin = 15 * 60; a.endMin = 16 * 60; a.showTimes.run();
        a.setRepeat(EvSpec.NONE); a.setRemind(0, 14 * 60);
        shot(a, "day_form_hospital.png");
        a.title.setText("아이 학원");
        a.setRepeat(EvSpec.WEEKLY); a.rDays = (1 << 2) | (1 << 4); a.fillDays();
        a.intervalSpin.setSelection(1); a.rInterval = 2;
        a.endSpin.setSelection(1); a.endMode = 1; a.untilD = (int) java.time.LocalDate.parse("2027-06-30").toEpochDay();
        a.untilBtn.setVisibility(android.view.View.VISIBLE); a.updateRepeatSum();
        shot(a, "day_form_repeat.png");
    }
}
