package com.dreamlink.app;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

/**
 * Transparent, instant-finish activity. Registered for ACTION_SEND text/plain,
 * so it appears in the EMUI native share sheet under "更多". It does the minimum:
 * read the shared text -> store it -> kick off the foreground sync -> finish().
 * No layout, no animation, no UI => fastest possible "launch".
 */
public class ShareReceiverActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent in = getIntent();
        String text = in != null ? in.getStringExtra(Intent.EXTRA_TEXT) : null;
        String subject = in != null ? in.getStringExtra(Intent.EXTRA_SUBJECT) : null;

        if (text == null || text.trim().isEmpty()) {
            Toast.makeText(this, R.string.err_no_text, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        String source = resolveSource(in);
        String url = extractUrl(text);
        Db db = new Db(this);
        boolean duplicate;
        try {
            duplicate = db.urlExists(url);   // dedupe: same URL saved once only
            if (!duplicate) {
                db.insert(url, subject, source, text, System.currentTimeMillis());
            }
        } finally {
            db.close();
        }
        if (duplicate) {
            Toast.makeText(this, R.string.err_duplicate, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Start the foreground uploader (Android 8 rule: startForegroundService).
        Intent up = new Intent(this, UploadService.class);
        try {
            startForegroundService(up);
        } catch (Exception ignored) {
            // If foreground start is blocked, the JobService will still retry later.
            Retry.schedule(this);
        }

        finish(); // vanish immediately; result is reported by the notification
    }

    /** If the text contains a URL, store just the URL; otherwise store the whole text. */
    private String extractUrl(String text) {
        // crude but dependency-free: first token that looks like http(s)://...
        String[] parts = text.trim().split("\\s+");
        for (String p : parts) {
            if (p.startsWith("http://") || p.startsWith("https://")) return p;
        }
        return text.trim();
    }

    /** Best-effort: which app sent this share (shown in the list, informational). */
    private String resolveSource(Intent in) {
        try {
            Uri ref = getReferrer(); // API 22+; on a share this is "package://<pkg>/"
            if (ref != null && ref.getHost() != null) return ref.getHost();
        } catch (Exception ignored) {
        }
        return "";
    }
}
