# 🦉 HedwigSMS Worker · 后台控制台版

> [`index.admin.js`](index.admin.js) 是在原中转 Worker 基础上**内置网页控制台**的版本（v2）。
> 部署后打开 `https://<你的域名>/<ADMIN_PATH>`，用密码登录即可在线管理所有设置，**不必再为改一个开关去动环境变量或重新部署**。

- 中转功能与原版完全一致：`POST /?token=<TOKEN>`，把 App 上报的事件排版后分发到各渠道。
- 额外提供：登录后台、渠道在线配置、定时播报（Cron）、Token 重新生成、推送日志、暂停全部等。

> 只想用最简单的单文件中转、不要后台？用 [`index.single.js`](index.single.js)。
> 想要模块化工程（多文件 `src/`）？用 [`src/`](src/)。三者互不冲突，按需选一个部署即可。

---

## ✨ 后台版功能

| 功能 | 说明 |
| --- | --- |
| 🔐 密码登录 | 后台用密码登录，会话 Cookie 用 HMAC 签名，7 天有效 |
| 👀 实时 Token | 后台直接查看当前 Token（环境变量或后台生成），支持**一键重新生成** |
| 🔀 事件开关 | 7 类事件（短信 / 低电量 / 断电 / 插电 / 平安报告 / 守护开启 / 守护停止）单独开关 |
| 📡 渠道开关 | Telegram / 飞书 / Discord / Slack / 通用 Webhook / 邮件 各自启用或停用 |
| ✏️ 渠道配置在线编辑 | 直接在后台填写或修改各渠道密钥/地址，保存即写入 KV，**可覆盖环境变量** |
| ⏰ 定时播报 | 「平安报告」「守护开启」可设每天多个时间点自动推送（北京时间 UTC+8，由 Cron 触发，自带去重） |
| ⏸️ 暂停全部推送 | 维护模式，一键停掉所有推送（含定时报告） |
| 📨 测试推送 | 选择任意事件 + 任意渠道，立即发一条测试消息 |
| 🧾 推送日志 | 保留最近 20 条推送结果（时间 / 类型 / 来源 / 各渠道成功失败） |
| 🔑 修改后台密码 | 后台内改密码（存 SHA-256，不落明文） |
| 🚦 最小推送间隔 | 两条推送之间至少间隔 N 秒，防刷屏 |
| 🏷️ 设备名称覆盖 | 统一改写上报消息里的设备名 |

---

## ⏱️ 部署教程

### 前置条件

- 一个 Cloudflare 账号（免费版即可）。
- 本机 Node.js 18+。
- 使用 `wrangler`：推荐 `npm i -g wrangler`（或直接用 `npx wrangler`）。

```bash
wrangler --version        # 确认已安装
wrangler login            # 浏览器授权登录 Cloudflare
```

### 1. 创建 KV 命名空间

后台的设置与日志都存在 KV 里，需要先建一个：

```bash
wrangler kv namespace create HEDWIG_CONFIG
```

命令会输出类似：

```
{ binding = "HEDWIG_CONFIG", id = "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx" }
```

记下这个 **id**，下一步要用。

> 旧版 wrangler 命令是 `wrangler kv:namespace create HEDWIG_CONFIG`，两者等效。

### 2. 填写 `wrangler.admin.toml`

打开本目录下的 [`wrangler.admin.toml`](wrangler.admin.toml)，把 KV 的 id 换成上一步得到的值：

```toml
name = "hedwigsms"
main = "index.admin.js"
compatibility_date = "2024-09-23"

[vars]
# 后台路径，建议改成只有你知道的路径（不要用默认值）
ADMIN_PATH = "/admin"

[[kv_namespaces]]
binding = "HEDWIG_CONFIG"
id = "REPLACE_WITH_YOUR_KV_NAMESPACE_ID"   # ← 换成你自己的

[triggers]
crons = ["*/5 * * * *"]                     # 定时播报用，每 5 分钟检查一次
```

- `name`：Worker 名称，可自定义。
- `ADMIN_PATH`：后台入口路径，**务必改成别人猜不到的**（例如 `/h7x2k`）。
- `crons`：定时任务频率，保持 `*/5 * * * *` 即可（每 5 分钟检查一次是否命中你设定的时间）。

### 3. 设置密钥（Secrets）

```bash
# 必填
wrangler secret put AUTH_TOKEN          # App 里填的访问令牌，随意长随机串
wrangler secret put ADMIN_PASSWORD      # 后台登录密码（务必用强密码）

# 可选：渠道密钥。也可只在后台页面里填，二选一
wrangler secret put TELEGRAM_BOT_TOKEN
wrangler secret put TELEGRAM_CHAT_ID
wrangler secret put FEISHU_WEBHOOK_URL
wrangler secret put FEISHU_SIGN_SECRET      # 飞书签名（可选）
wrangler secret put DISCORD_WEBHOOK_URL
wrangler secret put SLACK_WEBHOOK_URL
wrangler secret put CUSTOM_WEBHOOK_URL      # Bark / Server酱 / 企业微信 / 钉钉 …
wrangler secret put RESEND_API_KEY          # 邮件（Resend）
wrangler secret put EMAIL_FROM
wrangler secret put EMAIL_TO
```

> 至少在后台里或通过环境变量配置**一个**渠道，否则没有渠道可推送。
> ⚠️ 密钥请用 `wrangler secret put`，**不要把明文写进 toml 再提交到 Git**。

### 4. 部署

```bash
wrangler deploy --config wrangler.admin.toml
```

看到 `Uploaded hedwigsms` 和 `schedule: */5 * * * *` 即表示 Worker 与 Cron 都已上线。

### 5. （可选）绑定自定义域名

在 Cloudflare 控制台 → Workers & Pages → 选中该 Worker → **Settings → Domains & Routes → Add custom domain**，绑定你自己的域名。
若服务器在国内或被墙，`*.workers.dev` 可能访问不了，**建议用自定义域名**。

### 6. 打开后台

浏览器访问：

```
https://<你的域名或 workers.dev 地址><ADMIN_PATH>
```

例如 `ADMIN_PATH="/admin"` → `https://xxx.workers.dev/admin`，输入第 3 步的 `ADMIN_PASSWORD` 登录。

### 7. 在 App 中填写

打开 HedwigSMS App →「中转模式」→ 填入：

- **Worker URL**：`https://<你的域名>/`
- **Token**：与 `AUTH_TOKEN` 一致（或用后台「重新生成 Token」后复制）

发送测试消息 → 授权 → 启动守护。

---

## 🔧 环境变量 / 密钥一览

| 变量 | 位置 | 必填 | 说明 |
| --- | --- | :--: | --- |
| `ADMIN_PATH` | `[vars]` / 后台 | ⭕ | 后台入口路径，默认 `/admin`，**建议自定义** |
| `AUTH_TOKEN` | Secret | ✅ | 访问令牌；需与 App 中 Token 一致。后台可覆盖/重生成 |
| `ADMIN_PASSWORD` | Secret | ✅ | 后台登录密码（也可登录后在后台内修改） |
| `TELEGRAM_BOT_TOKEN` / `TELEGRAM_CHAT_ID` | Secret / 后台 | ⭕ | Telegram |
| `FEISHU_WEBHOOK_URL`（+ `FEISHU_SIGN_SECRET`） | Secret / 后台 | ⭕ | 飞书自定义机器人 |
| `DISCORD_WEBHOOK_URL` | Secret / 后台 | ⭕ | Discord Webhook |
| `SLACK_WEBHOOK_URL` | Secret / 后台 | ⭕ | Slack Incoming Webhook |
| `CUSTOM_WEBHOOK_URL` | Secret / 后台 | ⭕ | 通用：Bark / Server酱 / 企业微信 / 钉钉 / n8n |
| `RESEND_API_KEY` / `EMAIL_FROM` / `EMAIL_TO` | Secret / 后台 | ⭕ | 邮件（via Resend） |

⭕ = 至少配置一个渠道。后台里填写的渠道配置会写入 KV，**优先级高于同名环境变量**。

---

## 📮 中转接口

```
POST /?token=<AUTH_TOKEN>
Content-Type: application/json

{ "type": "sms", "device_name": "备用机", "data": { ... } }
```

`type` 取值：`sms` · `battery_low` · `power_connected` · `power_disconnected` · `heartbeat` · `service_on` · `service_off`。

返回示例：

```json
{ "ok": true, "type": "sms", "results": [ { "channel": "telegram", "ok": true } ] }
```

- Token 错误 → `401`
- 未知 `type` → `400`
- 命中「暂停全部 / 事件关闭 / 限流」→ `ok: true` 且带 `skipped` 字段

---

## ⏰ 定时播报说明

- 时间使用**北京时间（UTC+8）**，每个时间点每天各推一次。
- 由 `[triggers] crons` 驱动（默认每 5 分钟检查一次），命中设定时间才推送。
- 每条定时推送都用 KV 记录去重键（保留 2 天），避免 Cron 重试导致重复发送。
- 若某事件在「推送事件开关」里被关闭，对应的定时播报也会停止。
- 常见用法：平安报告设 `08:00` 与 `20:00`，即早晚各一次。

---

## 🔒 安全建议

- `ADMIN_PASSWORD` 用强密码，并在后台「修改后台密码」定期更换。
- `ADMIN_PATH` 改成别人猜不到的路径，降低被扫描到的概率。
- 后台页面带 `noindex,nofollow`，不参与搜索引擎收录。
- 永远不要把 `.dev.vars`、密钥或 KV id 提交到 Git。
- 中转仅校验 Token，请妥善保管，泄露后可在后台「重新生成 Token」。

---

## 🧪 本地开发

```bash
cp .dev.vars.example .dev.vars     # 填写 AUTH_TOKEN / ADMIN_PASSWORD 及渠道
wrangler dev --config wrangler.admin.toml
```

打开 `http://localhost:8787/admin` 即可调试后台。`.dev.vars` 已在 `.gitignore` 中，不会被提交。

---

## 📁 本目录文件

```
worker/
├── index.admin.js        # ★ 带后台控制台版（本文档对象）
├── wrangler.admin.toml   # ★ 对应的部署配置（含 Cron）
├── index.single.js       # 无后台的单文件中转版
├── wrangler.toml         # 单文件版配置
├── src/                  # 模块化工程版（channels/、format.js 等）
└── .dev.vars.example     # 本地开发变量示例
```

---

## 📜 许可

MIT，见仓库根目录 [LICENSE](../LICENSE)。多渠道分发与 payload 形态改写自 **OpenClaw**（MIT）。
