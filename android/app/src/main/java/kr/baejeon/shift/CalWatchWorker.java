package kr.baejeon.shift;

import android.content.Context;
import android.provider.CalendarContract;

import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.util.concurrent.TimeUnit;

/** 휴대폰 캘린더가 바뀌면(가족이 일정 추가 등) 위젯을 바로 다시 그려요. */
public class CalWatchWorker extends Worker {
    static final String NAME = "calwatch";

    public CalWatchWorker(@NonNull Context c, @NonNull WorkerParameters p) { super(c, p); }

    static void watch(Context c) {
        try {
            Constraints cons = new Constraints.Builder()
                    .addContentUriTrigger(CalendarContract.CONTENT_URI, true)
                    .setTriggerContentUpdateDelay(3, TimeUnit.SECONDS)
                    .setTriggerContentMaxDelay(15, TimeUnit.SECONDS)
                    .build();
            OneTimeWorkRequest w = new OneTimeWorkRequest.Builder(CalWatchWorker.class).setConstraints(cons).build();
            WorkManager.getInstance(c.getApplicationContext()).enqueueUniqueWork(NAME, ExistingWorkPolicy.KEEP, w);
        } catch (Exception ignored) {}
    }

    @NonNull
    @Override
    public Result doWork() {
        Context c = getApplicationContext();
        try { FamApi.refresh(c); } catch (Exception ignored) {}   // 구글 캘린더 앱에서 지운·고친 일정도 바로
        try { WidgetUpdater.renderAll(c); } catch (Exception ignored) {}
        try { CalUpload.upload(c, false); } catch (Exception ignored) {}
        // 다음 변경도 계속 지켜보기 (지금 작업이 끝난 뒤 이어서)
        try {
            Constraints cons = new Constraints.Builder()
                    .addContentUriTrigger(CalendarContract.CONTENT_URI, true)
                    .setTriggerContentUpdateDelay(3, TimeUnit.SECONDS)
                    .setTriggerContentMaxDelay(15, TimeUnit.SECONDS)
                    .build();
            OneTimeWorkRequest w = new OneTimeWorkRequest.Builder(CalWatchWorker.class).setConstraints(cons).build();
            WorkManager.getInstance(c).enqueueUniqueWork(NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, w);
        } catch (Exception ignored) {}
        return Result.success();
    }
}
