# DreamLink — 平板分享链接 → Gitee 中转 → 电脑 REST API

把任意 App 里「分享 → 更多 → DreamLink」的链接，自动同步到电脑，并在电脑开一个本地 API 供其它软件读取。平板和电脑**不必同时开机**：链接先落平板本地库并推到 Gitee 私有仓库当中转枢纽，电脑代理开机后定时拉取。

```
任意App分享文本 ──▶ 华为原生分享面板 ──▶ DreamLink(平板)
                                              │  存本地 SQLite
                                              ▼
                                        Gitee 私有仓库 (唯一写入方=平板)
                                              │  定时拉取
                                              ▼
                                     PC 代理(开机自启) ──▶ 本地 REST API / JSON
```

## 它如何「注册进华为自带分享框」

Android 的 `ACTION_SEND` + `mimeType text/plain` intent-filter，是进入 EMUI 原生分享面板「更多」列表的**唯一正规途径**，不需要任何华为 SDK。选中 DreamLink 后是一个**透明、秒退**的界面——不弹任何窗，只把链接落库并起前台上传服务，结果通过**通知栏**告知（“上传链接成功 / 已保存，联网后自动同步”）。

## 为什么「快速启动」

- 纯 Java、**零第三方依赖**（不引 AndroidX/Kotlin/网络库），APK 约 1MB。
- 收分享的界面**无布局、无动画**（`Theme.Translucent.NoTitleBar` + `windowAnimationStyle @null`），秒收秒退。
- 上传在 `startForegroundService` 的工作线程里做，不挡主线程。

## 仓库结构

```
android/          Gradle 工程（进 GitHub 编译 APK）
.github/          Actions 云编译工作流
pc-proxy/         电脑侧常驻代理 + 开机自启脚本（见 pc-proxy/README.md）
```

---

## 一、出 APK（本机无 Android 工具链，走云编译）

1. 在 GitHub 新建一个仓库（私有即可），把本目录**全部内容**（含 `.github`）推上去：
   ```
   git init && git add . && git commit -m "DreamLink" && git remote add origin <你的仓库> && git push -u origin main
   ```
2. 推上去后 Actions 会自动跑 `Build APK`（也可在 Actions 页手动 **Run workflow**）。
3. 跑完在 Actions 任务页下载 artifact **`dreamlink-debug-apk`**，解压得到 `app-debug.apk`。
4. 传到平板安装：设置 → 应用 → 特殊应用权限 → 安装未知应用 → 对文件管理器放行一次 → 装 APK。

> 首次编译若报错，看 Actions 日志（这台设备的云编译配方在记忆里标注为「已验证到可触发、结果待首次跑通」）。

## 二、平板端配置（在 App 里）

打开 DreamLink → 菜单「设置」填：

| 字段 | 值 |
|---|---|
| 仓库所有者 | 你的 **Gitee 用户名** |
| 私有仓库名 | 一个**私有**仓库名（可不存在，App 不会自动建，需先在 Gitee 建好空仓库） |
| 分支 | 一般 `master`（Gitee 默认；若你建仓库选了 main 就填 main） |
| 同步文件路径 | 如 `dreamlink/links.json` |
| 访问令牌 | Gitee **私人令牌**，勾选 `projects` 权限 |

> **令牌只存在平板本地，不写死进 APK。** 「测试连接」按钮会验证令牌+仓库+路径是否可用（文件还不存在也算通过）。

EMUI 后台常驻三件套（否则离线补推可能被冻）：电池优化关闭；手机管家→启动管理→手动管理三项全开；允许通知。

## 三、电脑端配置

见 [`pc-proxy/README.md`](pc-proxy/README.md)。一句话：改好 `config.json` → 跑 `install-autostart.ps1` → 访问 `http://127.0.0.1:8756/api/links`。

## 四、端到端验证

1. 平板任意 App 分享一条链接 → 更多 → DreamLink → 通知栏出现「上传链接成功」。
2. 电脑（代理在跑）：
   ```
   curl http://127.0.0.1:8756/api/links
   ```
   能看到刚分享的那条即全链路通。

---

## 数据与同步语义

- **平板 = 唯一写入方**，每次把整张表快照 PUT 到 Gitee 的同一个文件；电脑只读该文件。写入方单一 → 天然无冲突。
- 每条链接字段：`id / url / title / source / text / shared_at / synced_at`。`source` 记录是哪个 App 分享来的。
- 平板离线时收到分享：落库标“待同步”，网络恢复后由 `JobScheduler`（`NETWORK_AVAILABLE`，指数退避）自动补推；重启后 `BootReceiver` + 持久化 job 继续兜底。

## 安全默认

- Gitee 仓库需**私有**；电脑 API **默认只绑 `127.0.0.1`**。若要绑局域网，代理会**强制要求**在 `config.json` 设 `api_token`，否则拒绝启动。

## 已知边界

- 只收 `text/plain`（链接/文本），不收图片/文件。
- 目标设备 Android 8(API26)，`minSdk=26`；更高版本的运行时通知权限申请未做（8.0 无此限制）。
