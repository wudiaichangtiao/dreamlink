#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
DreamLink PC Proxy  —  pulls shared links from a Gitee repo and serves them
over a local REST API. Standard library only (no pip installs).

Data flow:
    tablet share  ->  Gitee repo file  ->  THIS proxy (periodic pull)  ->  REST API
The tablet is the only writer; this proxy is read-only mirror + API, so there
is no write conflict even when the two devices are never online at once.

Files it writes next to itself (in --data-dir, default ./state):
    state.json     full merged snapshot keyed by link id
    incoming.log   append-only JSONL, one line per newly seen link (audit trail)

Run:  python dreamlink_proxy.py
      (reads config.json in the same dir; see config.example.json)
"""

import argparse
import base64
import json
import os
import socket
import sys
import threading
import time
import urllib.error
import urllib.parse
import urllib.request
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

GITEE_API = "https://gitee.com/api/v5"
UA = "DreamLink-PC-Proxy/1.0"
HERE = os.path.dirname(os.path.abspath(__file__))


# --------------------------------------------------------------------------- #
# config
# --------------------------------------------------------------------------- #
def load_config(path):
    with open(path, "r", encoding="utf-8") as f:
        cfg = json.load(f)
    need = ("owner", "repo", "file", "token")
    missing = [k for k in need if not cfg.get(k)]
    if missing:
        raise SystemExit("config.json missing keys: " + ", ".join(missing))
    cfg.setdefault("branch", "master")
    cfg.setdefault("poll_seconds", 120)
    cfg.setdefault("api_host", "127.0.0.1")
    cfg.setdefault("api_port", 8756)
    cfg.setdefault("api_token", "")  # empty = localhost-only, no auth header
    return cfg


# --------------------------------------------------------------------------- #
# persistent store (thread-safe)
# --------------------------------------------------------------------------- #
class Store:
    def __init__(self, data_dir):
        self.data_dir = data_dir
        os.makedirs(data_dir, exist_ok=True)
        self.state_path = os.path.join(data_dir, "state.json")
        self.log_path = os.path.join(data_dir, "incoming.log")
        self.lock = threading.RLock()
        self.links = {}          # id(int) -> link dict
        self.last_sync = 0       # epoch seconds of last successful pull
        self.last_error = ""
        self._load()

    def _load(self):
        if os.path.exists(self.state_path):
            try:
                with open(self.state_path, "r", encoding="utf-8") as f:
                    obj = json.load(f)
                self.links = {int(k): v for k, v in obj.get("links", {}).items()}
                self.last_sync = obj.get("last_sync", 0)
            except Exception as e:
                print("[store] state.json unreadable, starting fresh:", e)

    def _save(self):
        obj = {"links": {str(k): v for k, v in self.links.items()},
               "last_sync": self.last_sync}
        tmp = self.state_path + ".tmp"
        with open(tmp, "w", encoding="utf-8") as f:
            json.dump(obj, f, ensure_ascii=False)
        os.replace(tmp, self.state_path)

    def merge(self, items):
        """Merge a freshly pulled list of link dicts. New ids go to incoming.log."""
        new = []
        with self.lock:
            for it in items:
                try:
                    lid = int(it["id"])
                except (KeyError, ValueError, TypeError):
                    continue
                if lid not in self.links:
                    new.append(it)
                    self.links[lid] = it
                    self._append_log(it)
            self.last_sync = int(time.time())
            self.last_error = ""
            self._save()
        return new

    def _append_log(self, it):
        with open(self.log_path, "a", encoding="utf-8") as f:
            f.write(json.dumps(it, ensure_ascii=False) + "\n")

    def list(self, limit=None, since=0, desc=True):
        with self.lock:
            rows = [v for k, v in self.links.items() if k > since]
        rows.sort(key=lambda x: x.get("shared_at", 0), reverse=desc)
        if limit:
            rows = rows[:limit]
        return rows

    def get(self, lid):
        with self.lock:
            return self.links.get(lid)

    def count(self):
        with self.lock:
            return len(self.links)

    def status(self):
        with self.lock:
            return {
                "total": len(self.links),
                "last_sync": self.last_sync,
                "last_error": self.last_error,
                "poll_seconds": getattr(self, "poll_seconds", 120),
                "api_host": getattr(self, "api_host", "127.0.0.1"),
                "api_port": getattr(self, "api_port", 8756),
            }

    def set_error(self, msg):
        with self.lock:
            self.last_error = msg


# --------------------------------------------------------------------------- #
# gitee pull
# --------------------------------------------------------------------------- #
def http_get_json(url, timeout=30):
    req = urllib.request.Request(url, headers={"User-Agent": UA,
                                               "Accept": "application/json"})
    with urllib.request.urlopen(req, timeout=timeout) as resp:
        raw = resp.read()
    return json.loads(raw.decode("utf-8"))


def pull_links(cfg):
    """Fetch the synced file from Gitee and return its decoded JSON array.

    Empty / not-found file -> []. Anything else raises on failure.
    """
    path = urllib.parse.quote(cfg["file"])
    url = (f"{GITEE_API}/repos/{cfg['owner']}/{cfg['repo']}/contents/{path}"
           f"?access_token={urllib.parse.quote(cfg['token'])}"
           f"&ref={urllib.parse.quote(cfg['branch'])}")
    obj = http_get_json(url)

    # directory listings come back as a list; we expect a single file object
    if isinstance(obj, list):
        obj = next((o for o in obj if o.get("type") == "file"), {})
    if not obj or obj.get("encoding") != "base64" or "content" not in obj:
        return []

    content = "".join(obj["content"].split())  # strip newlines in the b64
    text = base64.b64decode(content).decode("utf-8")
    data = json.loads(text) if text.strip() else []
    return data if isinstance(data, list) else []


# --------------------------------------------------------------------------- #
# background poller
# --------------------------------------------------------------------------- #
def poll_loop(cfg, store, stop_event):
    interval = cfg["poll_seconds"]
    store.poll_seconds = interval
    while not stop_event.is_set():
        try:
            items = pull_links(cfg)
            new = store.merge(items)
            if new:
                print(f"[sync] +{len(new)} new link(s), total={store.count()}")
        except urllib.error.HTTPError as e:
            body = ""
            try:
                body = e.read().decode("utf-8", "replace")[:200]
            except Exception:
                pass
            if e.code == 404:
                # file not created yet: totally normal on a fresh repo
                store.last_error = ""
                print("[sync] file not present yet (waiting for first share)")
            else:
                store.set_error(f"HTTP {e.code} {body}")
                print("[sync] error:", store.last_error)
        except Exception as e:
            store.set_error(str(e))
            print("[sync] error:", e)
        stop_event.wait(interval)


# --------------------------------------------------------------------------- #
# REST API
# --------------------------------------------------------------------------- #
class Handler(BaseHTTPRequestHandler):
    store = None
    cfg = None

    def log_message(self, fmt, *args):
        sys.stderr.write("[api] " + (fmt % args) + "\n")

    # ---- helpers ---------------------------------------------------------- #
    def _authorized(self):
        token = self.cfg.get("api_token", "")
        if not token:
            # no shared secret configured: allow loopback clients only
            return self.client_address[0] in ("127.0.0.1", "::1")
        return self.headers.get("X-API-Key", "") == token

    def _send(self, code, obj):
        body = json.dumps(obj, ensure_ascii=False).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(body)

    def _query(self):
        q = urllib.parse.urlparse(self.path).query
        return urllib.parse.parse_qs(q)

    def _path(self):
        return urllib.parse.urlparse(self.path).path

    # ---- endpoints -------------------------------------------------------- #
    def do_GET(self):
        if not self._authorized():
            return self._send(401, {"error": "unauthorized"})

        p = self._path()
        if p == "/health":
            return self._send(200, {"ok": True})
        if p == "/api/status":
            return self._send(200, self.store.status())
        if p == "/api/links":
            q = self._query()
            limit = int(q.get("limit", ["200"])[0])
            since = int(q.get("since", ["0"])[0])
            desc = q.get("desc", ["1"])[0] != "0"
            return self._send(200, {"links": self.store.list(limit, since, desc)})
        if p.startswith("/api/links/"):
            try:
                lid = int(p.rsplit("/", 1)[1])
            except ValueError:
                return self._send(400, {"error": "bad id"})
            row = self.store.get(lid)
            return self._send(200, row) if row else self._send(404, {"error": "not found"})
        return self._send(404, {"error": "no such endpoint",
                                "hint": "GET /api/links  /api/links/<id>  /api/status  /health"})

    def do_POST(self):
        if not self._authorized():
            return self._send(401, {"error": "unauthorized"})
        if self._path() == "/api/sync":
            try:
                items = pull_links(self.cfg)
                new = self.store.merge(items)
                return self._send(200, {"new": len(new), "total": self.store.count()})
            except Exception as e:
                self.store.set_error(str(e))
                return self._send(502, {"error": str(e)})
        return self._send(404, {"error": "no such endpoint",
                                "hint": "POST /api/sync"})


# --------------------------------------------------------------------------- #
# main
# --------------------------------------------------------------------------- #
def main():
    ap = argparse.ArgumentParser(description="DreamLink PC Proxy")
    ap.add_argument("--config", default=os.path.join(HERE, "config.json"))
    ap.add_argument("--data-dir", default=os.path.join(HERE, "state"))
    ap.add_argument("--host", default=None, help="override api_host")
    ap.add_argument("--port", type=int, default=None, help="override api_port")
    args = ap.parse_args()

    cfg = load_config(args.config)
    if args.host:
        cfg["api_host"] = args.host
    if args.port:
        cfg["api_port"] = args.port

    store = Store(args.data_dir)

    # security guard: if bound to a non-loopback address, an api_token is required
    host = cfg["api_host"]
    if host not in ("127.0.0.1", "localhost", "::1") and not cfg.get("api_token"):
        raise SystemExit(
            f"REFUSING to bind {host} without api_token: that exposes your "
            f"links to the LAN. Set api_token in config.json, or bind 127.0.0.1.")

    Handler.store = store
    Handler.cfg = cfg

    stop = threading.Event()
    t = threading.Thread(target=poll_loop, args=(cfg, store, stop), daemon=True)
    t.start()

    httpd = ThreadingHTTPServer((host, cfg["api_port"]), Handler)
    print(f"[boot] API on http://{host}:{cfg['api_port']}  "
          f"(poll every {cfg['poll_seconds']}s, data in {args.data_dir})")
    print("[boot] GET /api/links | /api/links/<id> | /api/status | POST /api/sync")
    try:
        httpd.serve_forever()
    except KeyboardInterrupt:
        stop.set()
        httpd.shutdown()
        print("\n[boot] stopped")


if __name__ == "__main__":
    main()
