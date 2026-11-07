package com.dreamlink.app;

import android.net.Uri;
import android.util.Base64;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.nio.charset.Charset;
import java.net.URL;

/**
 * Minimal Gitee Contents API client — zero third-party deps, framework only
 * (HttpURLConnection + org.json + android.util.Base64).
 *
 * Flow proven on this device (MemWatch):
 *   GET  /repos/{o}/{r}/contents/{path}?access_token=&ref=  -> 200 has "sha", 404 = file absent
 *   PUT  /repos/{o}/{r}/contents/{path}  body {access_token, branch, content(base64,UTF-8), message[, sha]}
 */
public class Gitee {
    static final String API = "https://gitee.com/api/v5";
    static final String UA = "DreamLink/1.0 (Android)";
    static final Charset UTF8 = Charset.forName("UTF-8");

    /** Result of one raw HTTP exchange. */
    public static class Res {
        public int code;          // HTTP status; 0 = network exception
        public String body;       // response text (may be empty)
        public String error;      // exception message when code == 0

        boolean is2xx() { return code >= 200 && code < 300; }
        boolean isAuthFail() { return code == 401 || code == 403; }
        boolean isNotFound() { return code == 404; }
    }

    static String encodedPath(String path) {
        String[] segs = path.split("/");
        StringBuilder sb = new StringBuilder();
        for (String s : segs) {
            if (s.length() == 0) continue;
            if (sb.length() > 0) sb.append('/');
            sb.append(Uri.encode(s));
        }
        return sb.toString();
    }

    static String contentsUrl(Cfg g) {
        return API + "/repos/" + Uri.encode(g.owner) + "/" + Uri.encode(g.repo)
                + "/contents/" + encodedPath(g.file);
    }

    /** sha of the file right now, or null if it does not exist yet. Throws AuthException on bad token. */
    static String shaOrNull(Cfg g) throws AuthException, java.io.IOException {
        String url = contentsUrl(g)
                + "?access_token=" + Uri.encode(g.token)
                + "&ref=" + Uri.encode(g.branchOrMaster());
        Res r = http("GET", url, null);
        if (r.isNotFound()) return null;
        if (r.isAuthFail()) throw new AuthException("Gitee " + r.code + "：令牌无效或无仓库权限");
        if (!r.is2xx()) throw new java.io.IOException("读取失败 HTTP " + r.code);
        String body = r.body == null ? "" : r.body.trim();
        // Gitee returns HTTP 200 + an empty JSON ARRAY (e.g. "[]") for a path
        // that does not exist yet (a not-yet-created file, or a directory).
        // Treat any array response as "file absent" => null sha => PUT will create it.
        if (body.startsWith("[")) return null;
        try {
            JSONObject o = new JSONObject(body);
            return o.optString("sha", null);
        } catch (org.json.JSONException e) {
            throw new java.io.IOException("响应解析失败: " + body.substring(0, Math.min(120, body.length())));
        }
    }

    /** Overwrite the file with json (whole snapshot). */
    static void putContent(Cfg g, String json, String commitMessage) throws AuthException, java.io.IOException {
        String sha = shaOrNull(g);
        try {
            JSONObject body = new JSONObject();
            body.put("access_token", g.token);
            body.put("branch", g.branchOrMaster());
            body.put("content", Base64.encodeToString(json.getBytes(UTF8), Base64.NO_WRAP));
            body.put("message", commitMessage);
            if (sha != null) body.put("sha", sha);
            Res r = http("PUT", contentsUrl(g), body.toString());
            if (r.isAuthFail()) throw new AuthException("Gitee " + r.code + "：令牌无效或无写入权限");
            if (!r.is2xx()) {
                String snip = r.body == null ? "" : r.body.substring(0, Math.min(200, r.body.length()));
                throw new java.io.IOException("上传失败 HTTP " + r.code + " " + snip);
            }
        } catch (org.json.JSONException e) {
            throw new java.io.IOException("构造请求失败");
        }
    }

    static class AuthException extends Exception {
        AuthException(String m) { super(m); }
    }

    /** Small HTTP helper. bodyJson != null -> PUT/POST with JSON payload. */
    static Res http(String method, String url, String bodyJson) {
        Res out = new Res();
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(15000);
            c.setReadTimeout(30000);
            c.setUseCaches(false);
            c.setRequestProperty("User-Agent", UA);
            c.setRequestProperty("Accept", "application/json");
            if (bodyJson != null) {
                c.setRequestMethod(method);
                c.setDoOutput(true);
                c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                byte[] data = bodyJson.getBytes(UTF8);
                c.setFixedLengthStreamingMode(data.length);
                OutputStream os = c.getOutputStream();
                os.write(data);
                os.flush();
                os.close();
            } else {
                c.setRequestMethod(method);
            }
            out.code = c.getResponseCode();
            InputStream is = out.code >= 400 ? c.getErrorStream() : c.getInputStream();
            out.body = readAll(is);
        } catch (Exception e) {
            out.code = 0;
            out.error = e.getClass().getSimpleName() + ": " + e.getMessage();
        } finally {
            if (c != null) c.disconnect();
        }
        return out;
    }

    static String readAll(InputStream is) throws java.io.IOException {
        if (is == null) return "";
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = is.read(buf)) > 0) bo.write(buf, 0, n);
        is.close();
        return new String(bo.toByteArray(), UTF8);
    }
}
