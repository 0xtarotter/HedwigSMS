<p align="center">
  <img src="Banner.png" alt="HedwigSMS" width="100%" />
</p>

<p align="center"><b>English</b> · <a href="README.zh-CN.md">简体中文</a></p>

# 🦉 HedwigSMS

> Forward SMS verification codes, low-battery and power-outage alerts from a spare Android phone (e.g. one left in China) to whatever IM you actually use (Telegram / Feishu / Discord / Slack / Email / any webhook).

**Fully local · Zero data collection · No ads · Open-source (MIT)**

Designed for people living abroad who rely on a domestic backup phone to receive SMS codes and to monitor that phone's status. Beginner-friendly, quick to deploy, with strong at-a-glance status feedback.

---

## ✨ Features

- **Two working modes (switch in-app):**
  - **Relay mode** — the phone POSTs events to _your_ Cloudflare Worker (or a local relay), which formats and fans them out. Required for China-blocked IMs like Telegram/Discord.
  - **Direct mode** — the phone sends straight to each IM, **no server at all**. Best for China-reachable channels (Feishu, Email, Bark, ServerChan, WeCom…).
- **Many channels:** Telegram, Feishu/Lark, Discord, Slack, Email (Resend or your own SMTP), and a generic webhook for Bark / ServerChan / WeCom / DingTalk / n8n.
- **5 event types:** 📩 SMS · 🪫 low battery · ⚡️ power lost · 🔌 power restored · 💚 daily health report.
- **Multi-SIM aware:** the forwarded message shows _which SIM_ received the code (slot · carrier · masked number).
- **Strong status UX:** gradient hero status card, an ongoing notification, a **home-screen widget**, and a **Quick Settings tile** — all showing guard status, battery and forwarded count.
- **Easy setup:** scan-to-configure QR (unified scanner auto-detects config type), in-app channel wizard with **Telegram chat_id auto-discovery** and per-channel **test send**, plus a one-command local relay with an auto Cloudflare quick-tunnel URL.
- **Hard to kill (v1.1.0):** a foreground service with `START_STICKY`, boot auto-start, a **15-min keep-alive watchdog** that restarts the guard if the ROM kills it, and `onTaskRemoved` re-launch when the app is swiped away.
- **SMTP email channel (v1.1.0):** send via your own SMTP server (QQ/163/Gmail/self-hosted) in the app, or through the local relay — SSL 465 / STARTTLS 587 / plain 25, no Resend account required.
- **Power-notify toggle (v1.1.0):** optionally mute charge / outage events from Settings.
- **In-app update check** via GitHub Releases.
- **Privacy:** no analytics, no ads; secrets stored with `EncryptedSharedPreferences` (AES-256).

---

## 🧭 Architecture

```
①Spare phone (China)        ②Router (cloud or local)            ③Your IM (abroad)
HedwigSMS app          →     Cloudflare Worker / local relay  →  Telegram / Feishu / …
· listens SMS/power          · auth + format + fan-out            (IM pushes to all your
· foreground service                                               logged-in devices)
                            Direct mode skips ②: the phone sends to ③ itself.
```

The "receiver" is simply the IM app you already use — there is nothing extra to install on your everyday phone.

---

## ⏱️ Quick start

### Option A — Direct mode (no server, no account)

1. Install the APK on the spare phone.
2. Open the app → **Run mode → Direct** → **Configure IM channels**.
3. Add a China-reachable channel (e.g. Feishu webhook, or Email via Resend) → **Test send** → **Save**.
4. Grant **Receive SMS / Notifications**, disable battery optimization → **Start guard**.

### Option B — Relay mode with Cloudflare Worker (24/7, stable URL)

1. Cloudflare → Workers & Pages → Create Worker → paste [`worker/index.single.js`](worker/index.single.js) → Deploy.
2. In **Settings → Variables** set `AUTH_TOKEN` and at least one channel (see table below).
3. Copy the Worker URL.
4. In the app: **Relay mode** → fill Worker URL + Token (or **scan** the QR from [`tools/setup-qr.html`](tools/setup-qr.html)) → **Send test message** → grant permissions → **Start guard**.

> 💡 Want a **web admin console** built into the Worker (edit channels online, regenerate Token, push logs, pause-all)? Use [`worker/index.admin.js`](worker/index.admin.js) — see [`worker/README.md`](worker/README.md).

### Option C — Local relay with auto public URL (BlueBubbles-style, zero account)

```bash
cd relay && npm install            # installs nodemailer (needed for the SMTP channel)
cd relay && cp .env.example .env   # fill ≥1 channel; token auto-generates if blank
node start.mjs                      # needs Node 18+ and cloudflared
```

It auto-generates a token, opens a `*.trycloudflare.com` URL, and serves a pairing dashboard (`/pair`) + an IM config wizard (`/setup`). Scan the QR with the app. _(Ephemeral URL; computer must stay on — for permanent use, prefer Option B.)_

---

## 🔧 Environment variables (Worker / relay)

| Variable                                      | Required | Description                                         |
| --------------------------------------------- | :------: | --------------------------------------------------- |
| `AUTH_TOKEN`                                  |    ✅    | Shared secret; must match the Token in the app      |
| `TELEGRAM_BOT_TOKEN` / `TELEGRAM_CHAT_ID`     |    ⭕    | Telegram bot                                        |
| `FEISHU_WEBHOOK_URL` (+ `FEISHU_SIGN_SECRET`) |    ⭕    | Feishu custom bot (optional signing)                |
| `DISCORD_WEBHOOK_URL`                         |    ⭕    | Discord channel webhook                             |
| `SLACK_WEBHOOK_URL`                           |    ⭕    | Slack Incoming Webhook                              |
| `CUSTOM_WEBHOOK_URL`                          |    ⭕    | Generic: Bark / ServerChan / WeCom / DingTalk / n8n |
| `RESEND_API_KEY` / `EMAIL_FROM` / `EMAIL_TO`  |    ⭕    | Email via [Resend](https://resend.com)              |
| `SMTP_HOST` / `SMTP_FROM` / `SMTP_TO` (+ optional `SMTP_PORT` / `SMTP_USER` / `SMTP_PASS` / `SMTP_SECURITY`) | ⭕ | Email via your own SMTP server (**relay only**; run `npm install` first) |

⭕ = configure **at least one** channel.

> 💡 **SMTP email:** in direct mode configure it right inside the app's “📮 SMTP Email” card; for the local relay use the `SMTP_*` vars above (run `npm install` in `relay/` to install nodemailer). Cloudflare Workers cannot do raw SMTP — use Resend there.

---

## 🛠️ Build

```bash
cd android
./gradlew :app:assembleDebug      # output: app/build/outputs/apk/debug/app-debug.apk
```

Or open `android/` in **Android Studio** (Ladybug+), JDK 17–21, Android SDK 36. The Gradle wrapper is included.

### Release signing

Release builds are signed only when `android/keystore.properties` exists (it is git-ignored).

```bash
keytool -genkeypair -v -keystore release.jks -keyalias hedwig \
  -keyalg RSA -keysize 2048 -validity 10000
```

Create `android/keystore.properties`:

```properties
storeFile=/absolute/path/to/release.jks
storePassword=********
keyAlias=hedwig
keyPassword=********
```

Then `./gradlew :app:assembleRelease` (APK) or `:app:bundleRelease` (AAB).
There is no paid certificate — the keystore is self-generated. **Back it up**; losing it means you can't ship updates under the same identity.

> Set your repo in [`Constants.kt`](android/app/src/main/java/com/hedwig/sms/core/Constants.kt) (`GITHUB_REPO`) to enable in-app update checks against your GitHub Releases.

---

## 📁 Layout

```
HedwigSMS/
├── android/        # Android client (Jetpack Compose) — dual-mode, widget, QS tile, scanner
├── worker/         # Cloudflare Worker (single-file + modular)
├── relay/          # Local relay: auto cloudflared tunnel + pairing dashboard + setup wizard
├── tools/          # Offline config-QR generator (setup-qr.html)
├── assets/         # Transparent logos
├── README.md / README.zh-CN.md
├── LICENSE / THIRD_PARTY_NOTICES.md
```

---

## 📋 Changelog

See [CHANGELOG.md](CHANGELOG.md) for the full history. Summary of **v1.1.0** in this fork:

- ➕ **SMTP email channel** — app direct mode (`📮 SMTP Email` card, SSL/STARTTLS/plain) and a `relay/smtp.mjs` channel for the local relay (via `nodemailer`). Existing **Resend** email channel is unchanged.
- ➕ **Keep-alive watchdog** — new `KeepAliveScheduler` fires a `KEEPALIVE` broadcast every 15 min; the receiver restarts the guard service if the ROM killed it; `onTaskRemoved` re-launches after the app is swiped away; boot now also handles `QUICKBOOT_POWERON`.
- ➕ **Power-notify toggle** — Settings switch to mute charge / outage events.
- 🔧 **Bark compatibility** — the generic webhook payload now also carries `body` (Bark reads `body`, not `text`) in both the Worker and the app's direct mode.
- 🔧 **Build** — added JavaMail for Android (`com.sun.mail:android-mail` / `android-activation`) and a `META-INF` packaging exclude; bumped to `versionCode 2` / `versionName 1.1.0`.

## 🔒 Privacy

No analytics, no ads, nothing sent to the author. SMS goes only to **your own** Worker/relay (relay mode) or directly to **your own** IM (direct mode). The Worker/relay code is fully open and auditable. URL & tokens are stored on-device via `EncryptedSharedPreferences` (AES-256).

## 📜 License & credits

MIT © 2026 [Haowei Cao](https://chwjh.com) — original project; see [LICENSE](LICENSE).

This repository is a community fork maintained by [**0xtarotter**](https://github.com/0xtarotter) (v1.1.0 additions: SMTP channel, keep-alive watchdog, power-notify toggle, Bark `body` compatibility). The original copyright and MIT license are retained.

- Multi-channel dispatch & payload shapes adapted from **[OpenClaw](https://github.com/)** (MIT).
- Scan-to-pair / auto public-URL ideas inspired by **[BlueBubbles](https://github.com/BlueBubblesApp)** (Apache-2.0).
- QR via **qrcode.js** (MIT) and **ZXing** (Apache-2.0).
- SMTP via **JavaMail for Android** (`com.sun.mail`, CDDL/GPL-2.0-CPE) and **Nodemailer** (MIT).

See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
