<p align="center">
  <img src="Banner.png" alt="HedwigSMS" width="100%" />
</p>

<p align="center"><a href="README.md">English</a> · <b>简体中文</b></p>

# 🦉 HedwigSMS · 海德薇短信转发器

> 把国内备用机的短信验证码、低电量与停电告警，实时转发到你海外常用的 IM（Telegram / 飞书 / Discord / Slack / 邮件 / 任意 Webhook）。

**全本地运行 · 零数据收集 · 纯净无广告 · 开源（MIT）**

专为长期在海外、依赖国内备用机接收验证码并监控其状态的用户设计。新手友好、部署快、状态可视化反馈强。

---

## ✨ 功能特性

- **两种工作模式（App 内切换）：**
  - **中转模式** —— 手机把事件 POST 到 _你自己的_ Cloudflare Worker（或本地 relay），由它排版并分发。Telegram/Discord 等国内被墙平台必须用此模式。
  - **手机直连模式** —— 手机直接发往各 IM，**完全不需要服务器**。适合国内可直达的渠道（飞书、邮件、Bark、Server酱、企业微信…）。
- **多渠道：** Telegram、飞书、Discord、Slack、邮件（Resend 或自建 SMTP），以及对接 Bark / Server酱 / 企业微信 / 钉钉 / n8n 的通用 Webhook。
- **5 类事件：** 📩 短信 · 🪫 低电量 · ⚡️ 断电 · 🔌 插电 · 💚 每日平安报告。
- **多卡识别：** 转发消息会显示**是哪张卡**收到的（卡槽 · 运营商 · 脱敏号码）。
- **状态可视化：** 渐变 Hero 状态卡、常驻通知、**桌面小插件**、**下拉快捷开关磁贴**，都显示守护状态/电量/累计转发。
- **配置简单：** 扫码导入配置（统一扫码自动识别类型）、App 内渠道向导（**Telegram chat_id 自动获取** + 每渠道**测试发送**）、以及一条命令起本地 relay 并自动获得 Cloudflare 公网地址。
- **强保活（v1.1.0）：** 前台服务 + `START_STICKY`、开机自启、**15 分钟保活看门狗**（被 ROM 杀掉自动重启），以及被划掉时 `onTaskRemoved` 自动拉起。
- **SMTP 邮件渠道（v1.1.0）：** App 内可直接用自备 SMTP 服务器发信（QQ/163/Gmail/自建），本地 relay 亦支持——SSL 465 / STARTTLS 587 / 明文 25，无需 Resend 账号。
- **电源通知开关（v1.1.0）：** 可在设置中关闭充电 / 断电事件。
- **应用内检查更新**（基于 GitHub Releases）。
- **隐私：** 无统计、无广告；密钥用 `EncryptedSharedPreferences`（AES-256）加密存储。

---

## 🧭 架构

```
①备用机（国内）           ②中转（云端或本地）                  ③你的 IM（海外）
HedwigSMS App      →      Cloudflare Worker / 本地 relay   →   Telegram / 飞书 / …
· 监听短信/电源           · 鉴权 + 排版 + 多路分发              （IM 自己推送到你
· 前台保活服务                                                  登录的所有设备）
                         直连模式跳过 ②：手机直接发到 ③。
```

「接收端」就是你平时已经在用的那个 IM —— 不用再额外装任何 App。

---

## ⏱️ 快速上手

### 方式 A —— 手机直连模式（无服务器、无账号）

1. 在备用机安装 APK。
2. 打开 App →「运行模式 → 手机直连」→「⚙️ 配置 IM 渠道」。
3. 配一个国内可直达的渠道（如飞书 Webhook，或 Resend 邮件）→「测试发送」→「保存」。
4. 授予「接收短信 / 通知」、关闭电池优化 →「启动守护」。

### 方式 B —— 中转模式 + Cloudflare Worker（7×24、地址稳定）

1. Cloudflare → Workers & Pages → 创建 Worker → 粘贴 [`worker/index.single.js`](worker/index.single.js) → Deploy。
2. 在 **Settings → Variables** 配置 `AUTH_TOKEN` 和至少一个渠道（见下表）。
3. 复制 Worker 网址。
4. App 里：「中转模式」→ 填 Worker 网址 + Token（或用 [`tools/setup-qr.html`](tools/setup-qr.html) 生成二维码**扫码导入**）→「发送测试消息」→ 授权 →「启动守护」。

### 方式 C —— 本地 relay 自动公网地址（BlueBubbles 式，零账号）

```bash
cd relay && npm install            # 安装 nodemailer（用 SMTP 渠道时需要）
cd relay && cp .env.example .env   # 填好至少一个渠道；Token 留空会自动生成
node start.mjs                      # 需要 Node 18+ 与 cloudflared
```

会自动生成 Token、开启 `*.trycloudflare.com` 公网地址，并提供配对面板（`/pair`）+ IM 配置向导（`/setup`）。手机扫码即用。_（临时地址、需电脑常开；长期请用方式 B。）_

---

## 🔧 环境变量（Worker / relay）

| 变量                                           | 必填 | 说明                                          |
| ---------------------------------------------- | :--: | --------------------------------------------- |
| `AUTH_TOKEN`                                   |  ✅  | 访问令牌；需与 App 中 Token 一致              |
| `TELEGRAM_BOT_TOKEN` / `TELEGRAM_CHAT_ID`      |  ⭕  | Telegram 机器人                               |
| `FEISHU_WEBHOOK_URL`（+ `FEISHU_SIGN_SECRET`） |  ⭕  | 飞书自定义机器人（可选签名）                  |
| `DISCORD_WEBHOOK_URL`                          |  ⭕  | Discord 频道 Webhook                          |
| `SLACK_WEBHOOK_URL`                            |  ⭕  | Slack Incoming Webhook                        |
| `CUSTOM_WEBHOOK_URL`                           |  ⭕  | 通用：Bark / Server酱 / 企业微信 / 钉钉 / n8n |
| `RESEND_API_KEY` / `EMAIL_FROM` / `EMAIL_TO`   |  ⭕  | 邮件，via [Resend](https://resend.com)        |
| `SMTP_HOST` / `SMTP_FROM` / `SMTP_TO` (+ 可选 `SMTP_PORT` / `SMTP_USER` / `SMTP_PASS` / `SMTP_SECURITY`) | ⭕ | 邮件，经自建 SMTP 服务器（**仅 relay**；需先 `npm install`） |

⭕ = **至少配置一个**渠道。

> 💡 **SMTP 邮件：** 手机直连模式在 App 内「📮 SMTP 邮件」卡片直接配置；本地 relay 用上表 `SMTP_*` 变量（在 relay 目录先执行 `npm install` 安装 nodemailer）。Cloudflare Worker 无法直连 SMTP，请用 Resend。

---

## 🛠️ 构建

```bash
cd android
./gradlew :app:assembleDebug      # 产物：app/build/outputs/apk/debug/app-debug.apk
```

或用 **Android Studio**（Ladybug+）打开 `android/`，JDK 17–21、Android SDK 36。仓库已内置 Gradle Wrapper。

### 发布签名

仅当存在 `android/keystore.properties`（已被 gitignore）时，release 包才会签名。

```bash
keytool -genkeypair -v -keystore release.jks -keyalias hedwig \
  -keyalg RSA -keysize 2048 -validity 10000
```

新建 `android/keystore.properties`：

```properties
storeFile=/绝对路径/release.jks
storePassword=********
keyAlias=hedwig
keyPassword=********
```

然后 `./gradlew :app:assembleRelease`（APK）或 `:app:bundleRelease`（AAB）。
**无需付费证书**，keystore 自行生成；请**妥善备份**，丢失将无法以同一身份发布更新。

> 在 [`Constants.kt`](android/app/src/main/java/com/hedwig/sms/core/Constants.kt) 里把 `GITHUB_REPO` 改成你的「用户名/仓库」，即可启用基于 GitHub Releases 的应用内更新检查。

---

## 📁 目录结构

```
HedwigSMS/
├── android/        # Android 客户端（Compose）—— 双模式、小插件、磁贴、扫码
├── worker/         # Cloudflare Worker（单文件 + 工程化）
├── relay/          # 本地中转：自动 cloudflared 隧道 + 配对面板 + 配置向导
├── tools/          # 离线配置二维码生成器（setup-qr.html）
├── assets/         # 透明 logo
├── README.md / README.zh-CN.md
├── LICENSE / THIRD_PARTY_NOTICES.md
```

---

## 📋 版本改动

完整历史见 [CHANGELOG.md](CHANGELOG.md)。本 fork **v1.1.0** 改动摘要：

- ➕ **SMTP 邮件渠道** —— App 直连模式（「📮 SMTP 邮件」卡片，支持 SSL/STARTTLS/明文）+ 本地 relay 的 `relay/smtp.mjs` 渠道（依赖 `nodemailer`）。原有 **Resend** 邮件渠道保持不变。
- ➕ **保活看门狗** —— 新增 `KeepAliveScheduler`，每 15 分钟触发 `KEEPALIVE` 广播；服务被 ROM 杀掉时由接收器自动重启；划掉 App 时 `onTaskRemoved` 自动拉起；开机广播新增 `QUICKBOOT_POWERON`。
- ➕ **电源通知开关** —— 设置内可关闭充电 / 断电事件。
- 🔧 **Bark 兼容** —— 通用 Webhook 负载新增 `body` 字段（Bark 只读 `body`，不读 `text`），Worker 与 App 直连均已同步。
- 🔧 **构建** —— 引入 Android 版 JavaMail（`com.sun.mail:android-mail` / `android-activation`）并排除重复 `META-INF`；版本号升至 `versionCode 2` / `versionName 1.1.0`。

## 🔒 隐私

无统计、无广告、不向作者发送任何数据。短信仅发往**你自己**的 Worker/relay（中转模式）或直接发往**你自己**的 IM（直连模式）。Worker/relay 代码完全开源可审计。网址与令牌通过 `EncryptedSharedPreferences`（AES-256）加密存于本机。

## 📜 许可与致谢

MIT © 2026 [Haowei Cao](https://chwjh.com) —— 原项目；见 [LICENSE](LICENSE)。

本仓库是由 [**0xtarotter**](https://github.com/0xtarotter) 维护的社区 fork（v1.1.0 新增：SMTP 渠道、保活看门狗、电源通知开关、Bark `body` 兼容）。保留原作者版权与 MIT 许可。

- 多渠道分发与 payload 形态改写自 **[OpenClaw](https://github.com/)**（MIT）。
- 扫码配对 / 自动公网地址 思路参考 **[BlueBubbles](https://github.com/BlueBubblesApp)**（Apache-2.0）。
- 二维码用 **qrcode.js**（MIT）与 **ZXing**（Apache-2.0）。
- SMTP 依赖 **JavaMail for Android**（`com.sun.mail`，CDDL/GPL-2.0-CPE）与 **Nodemailer**（MIT）。

详见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。
