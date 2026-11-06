package com.dreamlink.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** After boot, if anything is still pending, arm a network retry. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent != null ? intent.getAction() : null)
                && Sync.hasPending(ctx)) {
            Retry.schedule(ctx);
        }
    }
}
