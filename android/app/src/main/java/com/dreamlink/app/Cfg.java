package com.dreamlink.app;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Gitee target + sync switch, kept in app-private prefs.
 * First run defaults come from BuildConfig (owner/repo/file baked in; token
 * injected at cloud build from the GITEE_TOKEN secret, never committed).
 * Anything the user edits in Settings overrides these and persists locally.
 */
public class Cfg {
    private static final String P = "cfg";
    private static final String K_OWNER = "owner";
    private static final String K_REPO = "repo";
    private static final String K_BRANCH = "branch";
    private static final String K_FILE = "file";
    private static final String K_TOKEN = "token";
    private static final String K_ENABLED = "enabled";

    public String owner, repo, branch, file, token;
    public boolean enabled;

    public static Cfg load(Context c) {
        SharedPreferences p = c.getSharedPreferences(P, Context.MODE_PRIVATE);
        Cfg g = new Cfg();
        g.owner = p.getString(K_OWNER, BuildConfig.DREAMLINK_OWNER);
        g.repo = p.getString(K_REPO, BuildConfig.DREAMLINK_REPO);
        g.branch = p.getString(K_BRANCH, "master");
        g.file = p.getString(K_FILE, BuildConfig.DREAMLINK_FILE);
        g.token = p.getString(K_TOKEN, BuildConfig.DREAMLINK_TOKEN);
        g.enabled = p.getBoolean(K_ENABLED, true);
        return g;
    }

    public void save(Context c) {
        c.getSharedPreferences(P, Context.MODE_PRIVATE).edit()
                .putString(K_OWNER, owner)
                .putString(K_REPO, repo)
                .putString(K_BRANCH, branch)
                .putString(K_FILE, file)
                .putString(K_TOKEN, token)
                .putBoolean(K_ENABLED, enabled)
                .apply();
    }

    /** A target is usable only when owner/repo/file/token are all present. */
    public boolean ready() {
        return notEmpty(owner) && notEmpty(repo) && notEmpty(file) && notEmpty(token);
    }

    public String branchOrMaster() {
        return notEmpty(branch) ? branch : "master";
    }

    private static boolean notEmpty(String s) {
        return s != null && s.trim().length() > 0;
    }
}
