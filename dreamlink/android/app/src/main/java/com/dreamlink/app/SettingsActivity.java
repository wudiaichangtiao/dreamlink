package com.dreamlink.app;

import android.app.Activity;
import android.os.Bundle;
import android.text.InputType;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Plain framework form (zero AndroidX). Stores Gitee target + token in
 * app-private prefs and offers a one-tap connectivity test.
 */
public class SettingsActivity extends Activity {

    EditText owner, repo, branch, file, token;
    CheckBox enabled;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setTitle(R.string.settings_title);
        Cfg g = Cfg.load(this);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(32, 32, 32, 32);

        owner = field(col, getString(R.string.cfg_owner), g.owner, false);
        repo = field(col, getString(R.string.cfg_repo), g.repo, false);
        branch = field(col, getString(R.string.cfg_branch), g.branch, false);
        file = field(col, getString(R.string.cfg_file), g.file, false);
        token = field(col, getString(R.string.cfg_token), g.token, true);

        enabled = new CheckBox(this);
        enabled.setText(R.string.cfg_enabled);
        enabled.setChecked(g.enabled);
        col.addView(enabled);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        Button save = new Button(this);
        save.setText(R.string.btn_save);
        Button test = new Button(this);
        test.setText(R.string.btn_test);
        row.addView(save);
        row.addView(test);
        col.addView(row);

        ScrollView sv = new ScrollView(this);
        sv.addView(col);
        setContentView(sv);

        save.setOnClickListener(v -> {
            collect().save(this);
            Toast.makeText(this, R.string.toast_saved, Toast.LENGTH_SHORT).show();
            finish();
        });
        test.setOnClickListener(v -> test());
    }

    private EditText field(LinearLayout parent, String label, String value, boolean password) {
        TextView t = new TextView(this);
        t.setText(label);
        parent.addView(t);
        EditText e = new EditText(this);
        e.setText(value);
        e.setTextSize(16f);
        if (password) {
            e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        }
        parent.addView(e);
        return e;
    }

    private Cfg collect() {
        Cfg g = new Cfg();
        g.owner = owner.getText().toString().trim();
        g.repo = repo.getText().toString().trim();
        g.branch = branch.getText().toString().trim();
        g.file = file.getText().toString().trim();
        g.token = token.getText().toString().trim();
        g.enabled = enabled.isChecked();
        return g;
    }

    private void test() {
        final Cfg g = collect();
        Toast.makeText(this, "测试中…", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            String err = null;
            try {
                // A successful GET (even 404 = file absent yet) proves
                // token + repo + path are all usable.
                Gitee.shaOrNull(g);
            } catch (Gitee.AuthException e) {
                err = e.getMessage();
            } catch (Exception e) {
                err = e.getMessage() == null ? e.toString() : e.getMessage();
            }
            final String failure = err;
            runOnUiThread(() -> Toast.makeText(this,
                    failure == null ? getString(R.string.toast_test_ok)
                            : getString(R.string.toast_test_fail, failure),
                    Toast.LENGTH_LONG).show());
        }, "dreamlink-test").start();
    }
}
