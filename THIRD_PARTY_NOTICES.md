# Third-Party Notices / 第三方声明

HedwigSMS includes code and design adapted from the following open-source projects.
本项目参考并改写了以下开源项目的代码与设计。

---

## OpenClaw

The multi-channel message dispatch design and the per-platform payload shapes used
by the Cloudflare Worker (`worker/`) — specifically the Telegram, Feishu/Lark, Discord,
and Slack request formats — were **adapted from OpenClaw**. The original project uses
vendor SDKs (Grammy, `@larksuiteoapi/node-sdk`, `@slack/web-api`); HedwigSMS reimplements
the same payload shapes using lightweight `fetch` calls suitable for the Cloudflare
Workers runtime.

Cloudflare Worker（`worker/`）的多渠道分发设计，以及 Telegram、飞书、Discord、Slack
各平台的请求负载形态，**改写自 OpenClaw**。原项目使用各平台官方 SDK，本项目改用适合
Cloudflare Workers 运行时的轻量 `fetch` 实现，仅借鉴其 payload 结构与分发抽象。

```
OpenClaw
Copyright (c) 2026 OpenClaw Foundation
Licensed under the MIT License.
```

---

## BlueBubbles

The **scan-a-QR-to-configure** flow (encoding the server address + token into a QR code that
the mobile client scans to auto-fill its settings) was **inspired by BlueBubbles**, whose
desktop server shows a QR encoding `[password, address]` for its mobile clients. HedwigSMS uses
the same idea with its own `hedwig://config?u=…&t=…&d=…` payload.

「扫码即配置」的交互（把服务端地址 + 令牌编码进二维码，手机扫码自动填好）**参考自 BlueBubbles**。

```
BlueBubbles
Copyright (c) BlueBubblesApp (https://github.com/BlueBubblesApp)
Licensed under the Apache License 2.0.
```

## qrcode.js

The offline config-QR generator page (`tools/setup-qr.html`) bundles **qrcode.js**.

```
qrcode.js — Copyright (c) davidshimjs
Licensed under the MIT License. https://github.com/davidshimjs/qrcodejs
```

## ZXing (zxing-android-embedded)

In-app QR scanning & generation use **ZXing** via `com.journeyapps:zxing-android-embedded`.

```
ZXing — Copyright (c) ZXing authors / Apache-2.0
zxing-android-embedded — Copyright (c) JourneyApps / Apache-2.0
```

## Android client dependencies / Android 客户端依赖

- AndroidX (Apache-2.0)
- Jetpack Compose (Apache-2.0)
- Room (Apache-2.0)
- AndroidX Security (EncryptedSharedPreferences) (Apache-2.0)
- OkHttp — Square, Inc. (Apache-2.0)
- kotlinx.serialization / kotlinx.coroutines — JetBrains (Apache-2.0)

## JavaMail for Android (SMTP email)

The in-app SMTP email channel (direct mode) uses **JavaMail for Android**.

```
com.sun.mail:android-mail / com.sun.mail:android-activation
Copyright (c) Oracle and/or its affiliates.
Licensed under the CDDL 1.0 / GPL 2.0 with Classpath Exception (via the Jakarta Mail project).
```

App 内的 SMTP 邮件渠道（直连模式）使用 **JavaMail for Android**。

## Node / Worker dependencies

- **Nodemailer** — the local relay's SMTP channel (`relay/smtp.mjs`) uses Nodemailer.
  本地 relay 的 SMTP 渠道使用 Nodemailer。

```
Nodemailer — Copyright (c) Andris Reinman
Licensed under the MIT License. https://github.com/nodemailer/nodemailer
```
