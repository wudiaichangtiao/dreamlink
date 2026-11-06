package com.dreamlink.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;

/**
 * Shared notification constants + helpers. Framework-only (no androidx) to keep
 * the APK tiny and launch instant. minSdk 26 => channels are always available,
 * so a silent IMPORTANCE_LOW channel is safe without version guards.
 *
 * Two ids on purpose:
 *   ID        = the ongoing holder while the foreground service uploads
 *   ID_RESULT = the success/fail notification, posted AFTER the service stops so
 *               stopForeground()/stopSelf() can't wipe it along with the holder.
 */
public class Notif {
    public static final String CHANNEL_ID = "dreamlink_sync";
    public static final int ID = 1001;
    public static final int ID_RESULT = 1002;

    static void ensureChannel(Context ctx) {
        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null && nm.getNotificationChannel(CHANNEL_ID) == null) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID,
                    ctx.getString(R.string.notif_channel_name),
                    NotificationManager.IMPORTANCE_LOW); // silent: no sound, no vibration
            nm.createNotificationChannel(ch);
        }
    }

    /** Build a notification. ongoing=true for the foreground-service holder; false for a result. */
    static Notification build(Context ctx, String text, boolean ongoing) {
        Notification.Builder b = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                ? new Notification.Builder(ctx, CHANNEL_ID)
                : new Notification.Builder(ctx);
        b.setSmallIcon(R.drawable.ic_stat_link)
                .setContentTitle(ctx.getString(R.string.app_name))
                .setContentText(text)
                .setOnlyAlertOnce(true)
                .setOngoing(ongoing)
                .setAutoCancel(!ongoing);
        return b.build();
    }

    /** Post the final result as a normal, dismissible notification under its own id. */
    static void result(Context ctx, String text) {
        ensureChannel(ctx);
        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(ID_RESULT, build(ctx, text, false));
    }
}
