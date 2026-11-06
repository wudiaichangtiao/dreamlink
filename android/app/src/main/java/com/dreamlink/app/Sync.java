package com.dreamlink.app;

import android.content.Context;

import java.util.List;

/**
 * Orchestrates "push pending links to Gitee". One snapshot per run: upload the
 * whole table, then mark every row synced. Whole-file PUT is idempotent and
 * avoids per-item races, so a partial failure just leaves rows pending.
 */
public class Sync {

    /** Sentinel errors that must NOT be retried on a timer (user action needed). */
    public static final String PERMANENT_DISABLED = "SYNC_DISABLED";
    public static final String PERMANENT_NO_CONFIG = "NOT_CONFIGURED";

    /** @return null on success / no-op, otherwise an error string. */
    public static String pushAll(Context ctx) {
        Cfg g = Cfg.load(ctx);
        if (!g.enabled) return PERMANENT_DISABLED;
        if (!g.ready()) return PERMANENT_NO_CONFIG;

        Db db = new Db(ctx);
        try {
            List<Link> pending = db.pending(5000);
            if (pending.isEmpty()) return null;

            String json = db.snapshotJson().toString();
            Gitee.putContent(g, json, "DreamLink sync (" + pending.size() + " links)");

            for (Link l : pending) db.markSynced(l.id);
            return null;
        } catch (Gitee.AuthException e) {
            // bad/revoked token: retrying won't help until the user fixes settings
            return PERMANENT_NO_CONFIG + ":" + e.getMessage();
        } catch (Exception e) {
            List<Link> pending = db.pending(5000);
            for (Link l : pending) db.markFailed(l.id);
            String m = e.getMessage();
            return m == null ? e.getClass().getSimpleName() : m;
        } finally {
            db.close();
        }
    }

    public static boolean hasPending(Context ctx) {
        Db db = new Db(ctx);
        try {
            return db.pendingCount() > 0;
        } finally {
            db.close();
        }
    }

    /** True = worth retrying later (network / transient HTTP). False = user must act. */
    public static boolean isTransient(String err) {
        if (err == null) return false;
        return !err.startsWith(PERMANENT_DISABLED) && !err.startsWith(PERMANENT_NO_CONFIG);
    }
}
