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

    // convenience for the service layer
    public SQLiteDatabase writable() {
        return getWritableDatabase();
    }

    public JSONObject empty() {
        return new JSONObject();
    }
}
