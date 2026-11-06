package com.dreamlink.app;

import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

/**
 * Foreground uploader. Started right after a share arrives (and by the "resync"
 * menu). Android 8 hard rule: startForeground() within 5s of
 * startForegroundService(), so onStartCommand posts the ongoing notification
 * first, then pushes on a worker thread and replaces the SAME notification id
 * in place with the result (setOnlyAlertOnce -> silent, non-intrusive).
 */
public class UploadService extends Service {

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Notif.ensureChannel(this);
        startForegroundInternal(Notif.build(this, getString(R.string.notif_uploading), true));

        new Thread(() -> {
            String err = null;
            try {
                err = Sync.pushAll(UploadService.this);
            } catch (Exception e) {
                err = e.getMessage();
            }
            Notif.result(this, message(err));

            // transient failure + rows still pending -> arm network retry
            if (Sync.isTransient(err) && Sync.hasPending(this)) {
                Retry.schedule(this);
            }
            stopSelf(startId);
        }, "dreamlink-upload").start();

        return START_NOT_STICKY; // DB + JobScheduler retry are the durable state
    }

    private String message(String failure) {
        if (failure == null) return getString(R.string.notif_success);
        // Sync returns sentinel-prefixed strings for the cases that need the
        // user, not a retry: sync turned off, or bad/absent config/token.
        if (failure.startsWith(Sync.PERMANENT_DISABLED))
            return getString(R.string.notif_disabled);
        if (failure.startsWith(Sync.PERMANENT_NO_CONFIG))
            return getString(R.string.notif_config_error);
        return getString(R.string.notif_failed);
    }

    private void startForegroundInternal(android.app.Notification n) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(Notif.ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(Notif.ID, n);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
