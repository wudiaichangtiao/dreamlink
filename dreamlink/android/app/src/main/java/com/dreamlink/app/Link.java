package com.dreamlink.app;

/**
 * One shared link entry. Field names mirror the JSON keys written to Gitee
 * and served by the PC REST API so the whole chain uses the same shape.
 */
public class Link {
    public long   id;         // local auto-increment; stable across syncs
    public String url;        // the raw shared text (usually a URL)
    public String title;      // EXTRA_SUBJECT (app-supplied title, often empty)
    public String source;     // package name of the app that sent the share
    public String text;       // EXTRA_TEXT, may contain URLs inline
    public long   sharedAt;   // epoch millis when the share arrived
    public long   syncedAt;   // 0 = never synced; otherwise epoch millis of last sync
    public int    syncStatus; // 0=pending 1=synced 2=failed

    public org.json.JSONObject toJson() throws org.json.JSONException {
        org.json.JSONObject o = new org.json.JSONObject();
        o.put("id",         id);
        o.put("url",        url        != null ? url        : "");
        o.put("title",      title      != null ? title      : "");
        o.put("source",     source     != null ? source     : "");
        o.put("text",       text       != null ? text       : "");
        o.put("shared_at",  sharedAt);
        o.put("synced_at",  syncedAt);
        return o;
    }

    public static Link fromJson(org.json.JSONObject o) throws org.json.JSONException {
        Link l = new Link();
        l.id        = o.optLong("id",        0);
        l.url       = o.optString("url",     "");
        l.title     = o.optString("title",   "");
        l.source    = o.optString("source",  "");
        l.text      = o.optString("text",    "");
        l.sharedAt  = o.optLong("shared_at", 0);
        l.syncedAt  = o.optLong("synced_at", 0);
        // Items read back from Gitee are always considered synced
        l.syncStatus = 1;
        return l;
    }
}
