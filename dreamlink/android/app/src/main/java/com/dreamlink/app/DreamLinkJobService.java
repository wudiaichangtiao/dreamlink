package com.dreamlink.app;

import android.app.job.JobParameters;
import android.app.job.JobService;

/**
 * Network-available retry job. When the tablet comes back online after being
 * offline at share time, the platform runs this. Permanent conditions (sync
 * off / not configured) must NOT reschedule forever; only transient errors do.
 */
public class DreamLinkJobService extends JobService {

    @Override
    public boolean onStartJob(JobParameters params) {
        new Thread(() -> {
            String err = null;
            try {
                err = Sync.pushAll(DreamLinkJobService.this);
            } catch (Exception e) {
                err = e.getMessage();
            }
            if (err == null) {
                Notif.result(this, getString(R.string.notif_success));
                jobFinished(params, false);
            } else if (Sync.isTransient(err) && Sync.hasPending(this)) {
                jobFinished(params, true);  // let the platform re-run with backoff
            } else {
                jobFinished(params, false); // disabled / unconfigured: wait for user
            }
        }, "dreamlink-job").start();
        return true; // work continues off the main thread
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return true; // retry if the system cancels mid-run
    }
}
