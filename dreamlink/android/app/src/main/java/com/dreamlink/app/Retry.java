package com.dreamlink.app;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.os.PersistableBundle;

/**
 * Schedules a single network-available retry job. The tablet may be offline when
 * a link arrives; this is how it eventually reaches Gitee without the user
 * touching anything. One job at a time (id fixed), exponential-ish backoff handled
 * by the platform via backoffCriteria.
 */
public class Retry {
    static final int JOB_ID = 42;

    public static void schedule(Context ctx) {
        JobScheduler js = (JobScheduler) ctx.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (js == null) return;

        PersistableBundle extras = new PersistableBundle();
        ComponentName svc = new ComponentName(ctx, DreamLinkJobService.class);
        JobInfo job = new JobInfo.Builder(JOB_ID, svc)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPersisted(true)              // survive reboot (needs RECEIVE_BOOT_COMPLETED)
                .setBackoffCriteria(JobInfo.BACKOFF_POLICY_EXPONENTIAL, 30_000L)
                .setMinimumLatency(2_000L)
                .setOverrideDeadline(15 * 60_000L)
                .build();
        try {
            js.schedule(job);
        } catch (Exception ignored) {
        }
    }
}
