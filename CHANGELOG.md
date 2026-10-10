# Changelog / 更新日志

All notable changes to this project are documented here.
本文件记录项目的所有重要改动。

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/).

---

## [1.1.0] — 2026-10-10

Community fork maintained by [0xtarotter](https://github.com/0xtarotter), based on
[Haowei Cao](https://chwjh.com)'s [HedwigSMS](https://github.com/hcao10/HedwigSMS).
由 [0xtarotter](https://github.com/0xtarotter) 维护的社区 fork，基于原作者的
[HedwigSMS](https://github.com/hcao10/HedwigSMS)。

### Added / 新增

- **SMTP email channel (app direct mode).** A new “📮 SMTP Email” card lets the
  app send alerts through your own SMTP server (QQ / 163 / Gmail / self-hosted),
  supporting **SSL (465)**, **STARTTLS (587)** and **plain (25)** — no Resend
  account needed. Files: `DirectDispatcher.kt`, `ChannelConfig.kt`,
  `ChannelsScreen.kt`, `strings.xml`, `libs.versions.toml`, `build.gradle.kts`.
  - 新增「📮 SMTP 邮件」渠道：手机直连模式可用自备 SMTP 服务器发信，支持
    SSL(465) / STARTTLS(587) / 明文(25)。
- **SMTP channel for the local relay.** New `relay/smtp.mjs` (uses `nodemailer`,
  installed via `npm install`), registered as a relay-only channel in
  `relay/server.mjs` and documented in `.env.example` / `package.json`.
  - 本地 relay 新增 SMTP 渠道（`relay/smtp.mjs`，依赖 nodemailer），仅 relay 使用，
    不进 Cloudflare Worker 打包。
- **Keep-alive watchdog.** New `KeepAliveScheduler.kt` fires a `KEEPALIVE`
  broadcast every **15 minutes** (`KEEPALIVE_INTERVAL_MS`); the receiver restarts
  the guard service if the ROM killed it and re-arms the next check. Combined with
  `START_STICKY`, boot auto-start and `onTaskRemoved`, this maximizes 24/7 uptime.
  - 新增保活看门狗：每 15 分钟触发 `KEEPALIVE` 广播，服务被杀自动重启并重排下一次检查。
- **Re-launch after swipe-away.** `HedwigForwardService.onTaskRemoved` starts the
  service again when the user swipes the app from recents and the guard is on.
  - 划掉最近任务后自动拉起守护服务。
- **Quick-boot support.** Boot receiver now also handles
  `android.intent.action.QUICKBOOT_POWERON` / `com.htc.intent.action.QUICKBOOT_POWERON`.
  - 开机自启新增 `QUICKBOOT_POWERON` 广播。
- **Power-notify toggle.** New Settings switch to mute charge / outage events
  (`powerNotifyEnabled`).
  - 新增「电源状态通知」开关，可关闭充电 / 断电事件。
- **`body` field for Bark.** The generic webhook payload now also carries `body`
  (Bark reads `body`, not `text`) in both `worker/` (modular + single-file) and the
  app's direct mode.
  - 通用 Webhook 负载新增 `body` 字段以兼容 Bark（Worker 与 App 直连同步）。

### Changed / 变更

- Bumped `versionCode` to **2** and `versionName` to **1.1.0`.
- `Constants.GITHUB_REPO` set to `0xtarotter/HedwigSMS` for this fork's
  in-app update checks.
- `SMSAndStatusReceiver.startService` now wraps the call in `runCatching{}` to
  avoid crashes from foreground-service start restrictions.
- `keepAliveEnabled` defaults to `true`; `powerNotifyEnabled` defaults to `true`.
- README (EN / zh-CN) and `THIRD_PARTY_NOTICES.md` updated.

### Fixed / 修复

- Added `META-INF/NOTICE.md` and `META-INF/LICENSE.md` packaging excludes so the
  JavaMail jars don't break the Android build with duplicate resources.

### Build / 构建

- Added dependencies `com.sun.mail:android-mail` and `com.sun.mail:android-activation`
  (`javamail = 1.6.7`).
- Prebuilt debug APK committed at the repo root as `HedwigSMS-debug.apk`.

---

## [1.0.0] — 2026-06-28

Initial release by [Haowei Cao](https://chwjh.com).
原作者的首次发布。

- Dual working modes (relay / direct), multi-channel dispatch (Telegram, Feishu,
  Discord, Slack, Email via Resend, generic webhook), 5 event types, multi-SIM
  awareness, widget, Quick Settings tile, QR scan-to-configure, in-app update check.

[1.1.0]: https://github.com/0xtarotter/HedwigSMS/compare/v1.0.0...v1.1.0
[1.0.0]: https://github.com/hcao10/HedwigSMS/releases/tag/v1.0.0
