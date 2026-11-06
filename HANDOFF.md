# HANDOFF — 断点接续状态（关机后从这里继续）

> 本文件是 DreamLink 工程的中断-接续快照。若构建被打断，下次直接照「下一步」继续，无需重新决策。
> 目标（持久）：注册进华为原生分享框 → 链接经 Gitee 中转同步到电脑 → 电脑侧 REST API。

## 用户已确认的决定点（不要再问）

| 项 | 选择 |
|---|---|
| App 名 | **DreamLink**（包名 `com.dreamlink.app`） |
| 同步链路 | **Gitee 私有仓库**（平板=唯一写入方，电脑=只读镜像+API） |
| 电脑侧形态 | **常驻代理**（Python 标准库，开机自启，定时拉取 + 本地 REST） |
| API 暴露位置 | **只电脑**（平板不暴露 ContentProvider/HTTP） |
| 收到分享的交互 | **通知栏提示"上传链接成功"**（透明 Activity 秒退，不弹界面） |
| 接收范围 | **只收链接/文本**（intent-filter 仅 text/plain） |
| 出包方式 | **GitHub Actions 云编译**（本机无 JDK/Android SDK，已实测该配方） |
| 设备 | MatePad 10.8 / Android 8.0(API26) / EMUI；minSdk=26 |

## 已完成（已落盘）

**Android 工程 `dreamlink/android/`**
- [x] Gradle 骨架：`settings.gradle` / `build.gradle`(AGP 8.1.4) / `gradle.properties` / `app/build.gradle`（零依赖，纯 Java）
- [x] `AndroidManifest.xml`：SEND/text/plain 进分享框、透明 Activity、前台服务(dataSync)、JobService、BootReceiver
- [x] 资源：strings / themes(含 Transparent) / colors / 单色通知图标 / adaptive launcher 图标
- [x] Java：`Link` `Db` `Cfg` `Gitee`(contents API GET/PUT，Base64 UTF-8) `Sync` `Notif` `UploadService` `DreamLinkJobService` `Retry` `BootReceiver` `ShareReceiverActivity` `LinkListActivity` `SettingsActivity`

**编译/打包/PC 侧**
- [x] `.github/workflows/build-apk.yml`（temurin17 + setup-android + gradle 8.2 assembleDebug + upload-artifact）
- [x] `.gitignore`
- [x] `pc-proxy/dreamlink_proxy.py`（标准库；pull→merge→state.json/incoming.log→REST；非回环绑定强制要求 api_token）
- [x] `pc-proxy/config.example.json` / `install-autostart.ps1` / `uninstall-autostart.ps1` / `run-proxy.cmd` / `README.md`
- [x] 顶层 `README.md`（全链路：出包→平板配置→电脑配置→端到端验证→数据/安全语义）
- [x] **最终 zip 已生成**：`E:\DSH综合工作区\dreamlink-upload.zip`（34 条，正斜杠条目，无 `__pycache__`/`state`，可直接推 GitHub）

## 离线静态校验（本机无 Android 工具链，能做的都做了）

- [x] Python 代理**实跑自检通过**：`_scratch/dreamlink_selftest.py`（合并去重 / base64 往返 / 5 个 REST 端点全 200）+ `py_compile` OK
- [x] 资源交叉校验：Java 引用的 22 个 `R.string.*` + drawable + 透明主题 + 启动图标**全部命中**，无悬空引用
- [x] `startForeground` 3 参按 API29 门槛分支；结果通知走独立 ID，不被 `stopForeground` 抹掉
- [x] 修缺陷：令牌失效返回 `NOT_CONFIGURED:<detail>`，`UploadService.message()` 改用 `startsWith` 判定，提示区分“检查设置 / 同步已关闭 / 上传失败可重试”

## 下一步（均为用户侧动作，工程离线部分已尽）

> 说明：能在本机离线完成的部分**已全部完成并静态校验**；下列需要你在场（GitHub/真机）才能推进，关机不影响已落盘成品。

1. [ ] 触发首次云编译：新建 GitHub repo，把 `dreamlink-upload.zip` 解压后全部文件（含 `.github`）推上去 / 上传 → Actions 自动跑 `Build APK` → 下载 artifact `dreamlink-debug-apk` → `app-debug.apk`
2. [ ] 平板装 APK（设置→特殊应用权限→安装未知应用放行一次）→ DreamLink 设置里填 Gitee owner/repo/file/token → 「测试连接」
3. [ ] 电脑：改 `pc-proxy/config.json` → `pwsh -File install-autostart.ps1`
4. [ ] 端到端验证：分享一条链接→通知栏“上传链接成功”→`curl http://127.0.0.1:8756/api/links` 见数据
5. [ ] 云编译若有报错，看 Actions 日志（记忆标注该配方“结果待首次跑通”）；首次跑通后补一条交付物记忆到 `memory/deliverables/`

## 风险

- **云编译结果尚未首次跑通**（记忆：当前只到「可触发」）；Java 侧已逐文件静态复核，资源引用 100% 命中。
- Android 13+ 需运行时申请 POST_NOTIFICATIONS（目标设备 8.0/API26，暂无影响）。

## 校验命令（本地，无需 Android 工具链）

```
# 检查 Python 代理语法（本机有 Python 3.13）
python -c "import py_compile; py_compile.compile(r'E:\DSH综合工作区\dreamlink\pc-proxy\dreamlink_proxy.py', doraise=True); print('py OK')"
```
Android 侧只能靠云编译验证，本地无法编译。
