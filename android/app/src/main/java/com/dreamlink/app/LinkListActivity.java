package com.dreamlink.app;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Launcher entry: shows stored links + status, lets you re-sync or open settings.
 * Intentionally plain framework widgets (ListView/ArrayAdapter) so there is zero
 * AndroidX dependency -> minimal APK, instant start.
 */
public class LinkListActivity extends Activity {

    private Db db;
    private ArrayAdapter<String> adapter;
    private final List<Link> rows = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ListView lv = new ListView(this);
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, new ArrayList<String>());
        lv.setAdapter(adapter);
        lv.setOnItemClickListener((p, v, pos, id) -> openLink(pos));
        lv.setOnItemLongClickListener((p, v, pos, id) -> { deleteLink(pos); return true; });

        TextView empty = new TextView(this);
        empty.setText(R.string.list_empty);
        empty.setPadding(48, 96, 48, 16);

        // setEmptyView needs the empty view attached in the same tree, so host
        // both inside a FrameLayout (ListView draws over the centered hint).
        android.widget.FrameLayout root = new android.widget.FrameLayout(this);
        root.addView(empty);
        root.addView(lv);
        lv.setEmptyView(empty);

        setContentView(root);

        db = new Db(this);
        getActionBar().setSubtitle(getString(R.string.app_name));
        getActionBar().setDisplayShowTitleEnabled(true);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    @Override
    protected void onDestroy() {
        if (db != null) db.close();
        super.onDestroy();
    }

    private void refresh() {
        rows.clear();
        rows.addAll(db.all(2000));
        adapter.clear();
        SimpleDateFormat fmt = new SimpleDateFormat("MM-dd HH:mm", Locale.US);
        for (Link l : rows) {
            adapter.add(line(l, fmt));
        }
        adapter.notifyDataSetChanged();
    }

    private String line(Link l, SimpleDateFormat fmt) {
        String head = (l.title != null && !l.title.isEmpty()) ? l.title : l.url;
        String status = l.syncStatus == 1 ? "已同步" : (l.syncStatus == 2 ? "待重试" : "待同步");
        String src = (l.source == null || l.source.isEmpty()) ? "" : "  [" + l.source + "]";
        return head + "\n" + fmt.format(new Date(l.sharedAt)) + "  " + status + src;
    }

    private void openLink(int pos) {
        if (pos < 0 || pos >= rows.size()) return;
        String url = rows.get(pos).url;
        if (url == null || url.isEmpty()) return;
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)));
        } catch (Exception e) {
            Toast.makeText(this, "无法打开：" + url, Toast.LENGTH_SHORT).show();
        }
    }

    private void deleteLink(int pos) {
        if (pos < 0 || pos >= rows.size()) return;
        db.delete(rows.get(pos).id);
        refresh();
    }

    /** Menu: "全部重新同步" + "设置" via the hardware/overflow menu. */
    @Override
    public boolean onCreateOptionsMenu(android.view.Menu menu) {
        menu.add(0, 1, 0, R.string.btn_resync);
        menu.add(0, 2, 1, R.string.btn_settings);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(android.view.MenuItem item) {
        if (item.getItemId() == 1) {
            db.markAllPending();
            startForegroundService(new Intent(this, UploadService.class));
            Toast.makeText(this, "已加入重新同步", Toast.LENGTH_SHORT).show();
            refresh();
            return true;
        }
        if (item.getItemId() == 2) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
