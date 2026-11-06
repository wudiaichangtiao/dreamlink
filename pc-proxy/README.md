# DreamLink PC 代理

电脑侧常驻进程：**只用 Python 标准库**（无需 pip 装任何东西）。定时从 Gitee 拉平板同步上来的链接，落本地文件，并在本机开一个 REST API 供其它软件取用。开机自启。

## 前置

- Windows 上装 **Python 3**（安装时勾选 *Add python.exe to PATH*）。本工作区已有 Python 3.13 可直接用。
- 平板端「设置」里用的 owner/repo/file/token，和这里 `config.json` 保持一致（同一份信息填两处）。

## 配置

1. 复制 `config.example.json` → **`config.json`**，填好：
   ```json
   {
     "owner": "Gitee用户名",
     "repo": "私有仓库名",
     "branch": "master",
     "file": "dreamlink/links.json",
     "token": "私人令牌(projects权限)",
     "poll_seconds": 120,
     "api_host": "127.0.0.1",
     "api_port": 8756,
     "api_token": ""
   }
   ```
   - `api_token` 留空 = **只允许本机(回环)访问、无需鉴权**（默认，最安全）。
   - 若要局域网/远程访问：把 `api_host` 改成 `0.0.0.0` 或具体网卡 IP，**此时 `api_token` 必填**，否则代理拒绝启动；客户端请求带 `X-API-Key: <token>`。

## 启动

- **开机自启（推荐）**：在本目录 PowerShell 里跑一次
  ```
  pwsh -File install-autostart.ps1
  ```
  它注册一个「登录时启动」的计划任务（每用户，**不需要管理员**），用 `pythonw.exe` 后台无窗运行，并立即启动。撤销：`pwsh -File uninstall-autostart.ps1`。
- **临时前台看日志**：双击 `run-proxy.cmd`，或
  ```
  python dreamlink_proxy.py
  ```
  可选覆盖：`--host 0.0.0.0 --port 8756 --config 路径 --data-dir 路径`。

## 本地落盘（代理进程所在目录的 `state/`）

- `state.json`  合并后的全量快照（按 id）
- `incoming.log` 追加式 JSONL，每新到一条链接写一行（审计/溯源）

## REST API（供其它软件获取链接）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/links` | 全部链接。`?limit=200` 条数上限；`?since=<id>` 取 id 大于该值的**增量**；`?desc=0` 改升序 |
| GET | `/api/links/<id>` | 单条 |
| GET | `/api/status` | 总数 / 上次同步时间 / 最近错误 |
| POST | `/api/sync` | 立即拉一次（跳过定时器） |
| GET | `/health` | 存活探针 |

单条对象字段：`id / url / title / source / text / shared_at / synced_at`。

### curl 示例

```bash
# 取最近链接
curl http://127.0.0.1:8756/api/links

# 增量拉取（记下上次最大 id，下次 ?since= 只要新的）
curl "http://127.0.0.1:8756/api/links?since=128&limit=500"

# 拉取某一条
curl http://127.0.0.1:8756/api/links/129

# 手动触发一次同步
curl -X POST http://127.0.0.1:8756/api/sync

# 若设了 api_token（远程访问场景）
curl -H "X-API-Key: 你的token" http://127.0.0.1:8756/api/links
```

## 与其它软件集成（增量轮询范式）

```
last_id = 0
loop:
    GET /api/links?since=<last_id>&desc=0
    for each link: 处理之;  last_id = max(last_id, link.id)
    sleep 30
```
`since` 用的是平板本地的自增 id，稳定且单调，适合做游标增量。

## 故障排查

- **拿不到新链接**：先看平板通知栏是不是“上传链接成功”；再 `POST /api/sync` 看返回；最后看 `state/` 旁控制台日志（Gitee 404 = 平板还没分享过任何链接，属正常）。
- **Gitee 401/403**：令牌无效或没勾 `projects` 权限。
- **端口被占**：改 `api_port` 或 `--port`。
- **防火墙**：默认只绑 127.0.0.1，无需放行；只有改成局域网访问才需要，并确保设了 `api_token`。
