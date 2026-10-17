package kr.baejeon.shift;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.util.concurrent.TimeUnit;

/** 15분마다: 가족 공유 캘린더 새로 받기 → 위젯 다시 그리기 */
public class FamPollWorker extends Worker {
    public FamPollWorker(@NonNull Context c, @NonNull WorkerParameters p) { super(c, p); }

    static void schedule(Context c) {
        try {
            WorkManager wm = WorkManager.getInstance(c.getApplicationContext());
            if (FamApi.calId(c).isEmpty()) { wm.cancelUniqueWork("fampoll"); return; }
            PeriodicWorkRequest w = new PeriodicWorkRequest.Builder(FamPollWorker.class, 15, TimeUnit.MINUTES)
                    .setConstraints(new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build();
            wm.enqueueUniquePeriodicWork("fampoll", ExistingPeriodicWorkPolicy.KEEP, w);
        } catch (Exception ignored) {}
    }

    @NonNull
    @Override
    public Result doWork() {
        Context c = getApplicationContext();
        if (FamApi.refresh(c)) {
            try { WidgetUpdater.renderAll(c); } catch (Exception ignored) {}
            try { CalUpload.upload(c, false); } catch (Exception ignored) {}
        }
        return Result.success();
    }
}
