package com.dreamlink.app;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Local store of shared links. Gitee mirrors it, but this DB is the source of
 * truth for the tablet; sync state lives here per row.
 */
public class Db extends SQLiteOpenHelper {
    private static final String NAME = "dreamlink.db";
    private static final int VER = 1;

    public static final String T = "links";
    public static final String C_ID = "_id";
    public static final String C_URL = "url";
    public static final String C_TITLE = "title";
    public static final String C_SOURCE = "source";
    public static final String C_TEXT = "text";
    public static final String C_SHARED = "shared_at";
    public static final String C_SYNCED = "synced_at";
    public static final String C_STATUS = "sync_status";

    Db(Context c) {
        super(c, NAME, null, VER);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + T + " ("
                + C_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + C_URL + " TEXT, "
                + C_TITLE + " TEXT, "
                + C_SOURCE + " TEXT, "
                + C_TEXT + " TEXT, "
                + C_SHARED + " INTEGER, "
                + C_SYNCED + " INTEGER DEFAULT 0, "
                + C_STATUS + " INTEGER DEFAULT 0)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int o, int n) {
        // single table so far; nothing to migrate yet
    }

    public long insert(String url, String title, String source, String text, long sharedAt) {
        ContentValues v = new ContentValues();
        v.put(C_URL, url);
        v.put(C_TITLE, title);
        v.put(C_SOURCE, source);
        v.put(C_TEXT, text);
        v.put(C_SHARED, sharedAt);
        v.put(C_SYNCED, 0);
        v.put(C_STATUS, 0); // pending
        return getWritableDatabase().insert(T, null, v);
    }

    /** Dedupe guard: true if this exact URL was already saved once. */
    public boolean urlExists(String url) {
        if (url == null) return false;
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT 1 FROM " + T + " WHERE " + C_URL + "=? LIMIT 1",
                new String[]{url});
        try {
            return c.moveToFirst();
        } finally {
            c.close();
        }
    }

    public List<Link> all(int limit) {
        List<Link> out = new ArrayList<>();
        Cursor c = getReadableDatabase().query(T, null, null, null, null, null,
                C_SHARED + " DESC", String.valueOf(limit));
        try {
            while (c.moveToNext()) out.add(read(c));
        } finally {
            c.close();
        }
        return out;
    }

    /** Oldest pending first, so retries keep chronological order. */
    public List<Link> pending(int limit) {
        List<Link> out = new ArrayList<>();
        Cursor c = getReadableDatabase().query(T, null,
                C_STATUS + "=0 OR " + C_STATUS + "=2", null, null, null,
                C_SHARED + " ASC", String.valueOf(limit));
        try {
            while (c.moveToNext()) out.add(read(c));
        } finally {
            c.close();
        }
        return out;
    }

    public Link byId(long id) {
        Cursor c = getReadableDatabase().query(T, null, C_ID + "=?",
                new String[]{String.valueOf(id)}, null, null, null);
        try {
            if (c.moveToFirst()) return read(c);
            return null;
        } finally {
            c.close();
        }
    }

    public int pendingCount() {
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM " + T + " WHERE " + C_STATUS + "=0 OR " + C_STATUS + "=2",
                null);
        try {
            return c.moveToFirst() ? c.getInt(0) : 0;
        } finally {
            c.close();
        }
    }

    public void markSynced(long id) {
        ContentValues v = new ContentValues();
        v.put(C_STATUS, 1);
        v.put(C_SYNCED, System.currentTimeMillis());
        getWritableDatabase().update(T, v, C_ID + "=?", new String[]{String.valueOf(id)});
    }

    public void markFailed(long id) {
        ContentValues v = new ContentValues();
        v.put(C_STATUS, 2);
        getWritableDatabase().update(T, v, C_ID + "=?", new String[]{String.valueOf(id)});
    }

    public void markAllPending() {
        ContentValues v = new ContentValues();
        v.put(C_STATUS, 0);
        getWritableDatabase().update(T, v, null, null);
    }

    public void delete(long id) {
        getWritableDatabase().delete(T, C_ID + "=?", new String[]{String.valueOf(id)});
    }

    /** Whole-table snapshot as a JSON array, newest last — what Gitee stores. */
    public JSONArray snapshotJson() {
        JSONArray a = new JSONArray();
        List<Link> list = all(100000);
        // reverse so the file reads oldest->newest
        for (int i = list.size() - 1; i >= 0; i--) {
            try {
                a.put(list.get(i).toJson());
            } catch (Exception ignored) {
            }
        }
        return a;
    }

    private Link read(Cursor c) {
        Link l = new Link();
        l.id = c.getLong(c.getColumnIndexOrThrow(C_ID));
        l.url = c.getString(c.getColumnIndexOrThrow(C_URL));
        l.title = c.getString(c.getColumnIndexOrThrow(C_TITLE));
        l.source = c.getString(c.getColumnIndexOrThrow(C_SOURCE));
        l.text = c.getString(c.getColumnIndexOrThrow(C_TEXT));
        l.sharedAt = c.getLong(c.getColumnIndexOrThrow(C_SHARED));
        l.syncedAt = c.getLong(c.getColumnIndexOrThrow(C_SYNCED));
        l.syncStatus = c.getInt(c.getColumnIndexOrThrow(C_STATUS));
        return l;
    }

    /** Insert preserving an explicit id (for rows pulled from the repo). */
    public void insertWithId(long id, String url, String title, String source,
                             String text, long sharedAt, long syncedAt) {
        ContentValues v = new ContentValues();
        v.put(C_ID, id);
        v.put(C_URL, url);
        v.put(C_TITLE, title);
        v.put(C_SOURCE, source);
        v.put(C_TEXT, text);
        v.put(C_SHARED, sharedAt);
        v.put(C_SYNCED, syncedAt);
        v.put(C_STATUS, 1); // came from repo => already synced
        getWritableDatabase().insert(T, null, v);
    }

    /**
     * Reconcile local DB against the repo snapshot (repo = source of truth).
     *  - delete local rows that are already-synced but no longer in the repo
     *    (the PC dedup pass removed them);
     *  - keep local pending/failed rows not in the repo (not uploaded yet);
     *  - insert repo rows missing locally (added by another path/device).
     * @return number of local rows deleted.
     */
    public int mergeFromRemote(JSONArray remote) {
        SQLiteDatabase db = getWritableDatabase();
        java.util.HashSet<Long> remoteIds = new java.util.HashSet<>();
        for (int i = 0; i < remote.length(); i++) {
            try {
                remoteIds.add(remote.getJSONObject(i).getLong("id"));
            } catch (Exception ignored) {
            }
        }

        int deleted = 0;
        java.util.HashSet<Long> localIds = new java.util.HashSet<>();
        java.util.List<Link> locals = all(100000);
        for (Link l : locals) {
            localIds.add(l.id);
            boolean synced = l.syncStatus == 1;
            if (synced && !remoteIds.contains(l.id)) {
                db.delete(T, C_ID + "=?", new String[]{String.valueOf(l.id)});
                deleted++;
            }
        }

        for (int i = 0; i < remote.length(); i++) {
            try {
                org.json.JSONObject o = remote.getJSONObject(i);
                long id = o.getLong("id");
                if (localIds.contains(id)) continue;
                insertWithId(id,
                        o.optString("url", ""),
                        o.optString("title", ""),
                        o.optString("source", ""),
                        o.optString("text", ""),
                        o.optLong("shared_at", 0),
                        o.optLong("synced_at", 0));
            } catch (Exception ignored) {
            }
        }
        return deleted;
    }

    // convenience for the service layer
    public SQLiteDatabase writable() {
        return getWritableDatabase();
    }

    public JSONObject empty() {
        return new JSONObject();
    }
}
