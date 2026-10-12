/**
 * HedwigSMS Cloudflare Worker — 带后台控制台版 / with admin console (v2)
 * ======================================================================
 * - 中转入口:  POST /?token=<TOKEN>
 * - 后台入口:  GET/POST <ADMIN_PATH>   （默认 /admin）
 * - 定时播报由 App 端负责（AlarmManager），本 Worker 只做实时中转，不再自带定时任务。
 *
 * 环境变量 / ENV (可作为初始值，后台里可覆盖):
 *   AUTH_TOKEN           App 与转发请求共用的访问令牌（后台可重新生成覆盖）
 *   ADMIN_PASSWORD       后台登录密码
 *   ADMIN_PATH           后台路径，默认 "/admin"
 *   TELEGRAM_BOT_TOKEN / TELEGRAM_CHAT_ID
 *   FEISHU_WEBHOOK_URL / FEISHU_SIGN_SECRET
 *   DISCORD_WEBHOOK_URL / SLACK_WEBHOOK_URL / CUSTOM_WEBHOOK_URL
 *   RESEND_API_KEY / EMAIL_FROM / EMAIL_TO
 * 绑定 / Bindings:
 *   HEDWIG_CONFIG        KV 命名空间，存储设置 / 日志
 *
 * Message dispatch payload shapes adapted from OpenClaw (MIT).
 */

const VALID_TYPES = new Set([
  "sms",
  "battery_low",
  "power_connected",
  "power_disconnected",
  "heartbeat",
  "service_on",
  "service_off",
]);

const EVENT_META = [
  ["sms", "📩 短信"],
  ["battery_low", "🪫 低电量"],
  ["power_connected", "🔌 电源接通"],
  ["power_disconnected", "⚡️ 电源断开"],
  ["heartbeat", "💚 平安报告"],
  ["service_on", "✅ 守护开启"],
  ["service_off", "🛑 守护停止"],
];

const CHANNEL_META = [
  ["telegram", "Telegram"],
  ["feishu", "飞书"],
  ["discord", "Discord"],
  ["slack", "Slack"],
  ["webhook", "通用 Webhook / Bark"],
  ["email", "邮件 (Resend)"],
];

// 字段 -> 环境变量名映射 / channel field -> env var mapping
const CHANNEL_FIELDS = {
  telegram: [
    ["botToken", "Bot Token", "TELEGRAM_BOT_TOKEN"],
    ["chatId", "Chat ID", "TELEGRAM_CHAT_ID"],
  ],
  feishu: [
    ["webhookUrl", "Webhook 地址", "FEISHU_WEBHOOK_URL"],
    ["signSecret", "签名密钥（可选）", "FEISHU_SIGN_SECRET"],
  ],
  discord: [["webhookUrl", "Webhook 地址", "DISCORD_WEBHOOK_URL"]],
  slack: [["webhookUrl", "Webhook 地址", "SLACK_WEBHOOK_URL"]],
  webhook: [["webhookUrl", "Webhook 地址（Bark/Server酱/企业微信/钉钉…）", "CUSTOM_WEBHOOK_URL"]],
  email: [
    ["apiKey", "Resend API Key", "RESEND_API_KEY"],
    ["from", "发件人 Email From", "EMAIL_FROM"],
    ["to", "收件人 Email To（逗号分隔）", "EMAIL_TO"],
  ],
};

const DEFAULT_CONFIG = {
  version: 2,
  authToken: "",
  minIntervalSec: 0,
  deviceName: "",
  passwordHash: "",
  pauseAll: false,
  updatedAt: "",
  events: {
    sms: true,
    battery_low: true,
    power_connected: true,
    power_disconnected: true,
    heartbeat: true,
    service_on: true,
    service_off: true,
  },
  channels: {
    telegram: true,
    feishu: true,
    discord: true,
    slack: true,
    webhook: true,
    email: true,
  },
  channelConfig: {},
};

const COOKIE_NAME = "hedwig_sess";
const SESSION_MS = 7 * 24 * 60 * 60 * 1000;

/* ============================ 工具 / utils ============================ */

function safeEqual(a, b) {
  if (typeof a !== "string" || typeof b !== "string") return false;
  if (a.length !== b.length) return false;
  let diff = 0;
  for (let i = 0; i < a.length; i++) diff |= a.charCodeAt(i) ^ b.charCodeAt(i);
  return diff === 0;
}

function toHex(buf) {
  return [...new Uint8Array(buf)].map((b) => b.toString(16).padStart(2, "0")).join("");
}

async function sha256hex(s) {
  return toHex(await crypto.subtle.digest("SHA-256", new TextEncoder().encode(s)));
}

async function hmacHex(key, msg) {
  const k = await crypto.subtle.importKey(
    "raw",
    new TextEncoder().encode(key),
    { name: "HMAC", hash: "SHA-256" },
    false,
    ["sign"],
  );
  return toHex(await crypto.subtle.sign("HMAC", k, new TextEncoder().encode(msg)));
}

function randomToken() {
  return crypto.randomUUID().replace(/-/g, "") + crypto.randomUUID().slice(0, 8);
}

function json(data, status = 200) {
  return new Response(JSON.stringify(data, null, 2), {
    status,
    headers: { "Content-Type": "application/json; charset=utf-8" },
  });
}

function html(body, status = 200, extraHeaders = {}) {
  return new Response(body, {
    status,
    headers: { "Content-Type": "text/html; charset=utf-8", ...extraHeaders },
  });
}

function redirect(location, setCookie) {
  const headers = { Location: location };
  if (setCookie) headers["Set-Cookie"] = setCookie;
  return new Response(null, { status: 302, headers });
}

function esc(s) {
  return String(s)
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;");
}

function pad2(n) {
  return String(n).padStart(2, "0");
}

function beijingDate(ts) {
  const base = typeof ts === "number" && ts > 0 ? ts : Date.now();
  return new Date(base + 8 * 60 * 60 * 1000);
}

function formatTime(ts) {
  const d = beijingDate(ts);
  return `${d.getUTCFullYear()}-${pad2(d.getUTCMonth() + 1)}-${pad2(d.getUTCDate())} ${pad2(d.getUTCHours())}:${pad2(d.getUTCMinutes())}:${pad2(d.getUTCSeconds())}`;
}

/* ===================== 消息格式化 / formatting ===================== */

function formatMessage(payload) {
  const device = payload.device_name || "未知设备 / Unknown device";
  const time = formatTime(payload.timestamp);
  const data = payload.data || {};
  const sep = "———————————————";

  switch (payload.type) {
    case "sms": {
      const sender = data.sender || "未知号码 / Unknown";
      const body = data.body || "(空内容 / empty)";
      const simLine = data.sim ? `📲 接收卡 SIM：${data.sim}\n` : "";
      return {
        title: "📩 新短信 New SMS",
        text:
          `📩 新短信 | New SMS\n${sep}\n` +
          `📱 设备 Device：${device}\n${simLine}👤 发件人 From：${sender}\n🕒 时间 Time：${time}\n${sep}\n${body}`,
      };
    }
    case "battery_low": {
      const level = data.battery_level ?? "?";
      return {
        title: "🪫 低电量预警 Low Battery",
        text:
          `🪫 低电量预警 | Low Battery Warning\n${sep}\n` +
          `📱 设备 Device：${device}\n🔋 当前电量 Level：${level}%\n🕒 时间 Time：${time}\n${sep}\n` +
          `⚠️ 请尽快为备用机充电 / Please charge the backup phone soon.`,
      };
    }
    case "power_disconnected": {
      const level = data.battery_level ?? "?";
      return {
        title: "⚡️ 电源已断开 Power Lost",
        text:
          `⚡️ 电源已断开（疑似停电！）| Power Disconnected (possible outage!)\n${sep}\n` +
          `📱 设备 Device：${device}\n🔋 当前电量 Level：${level}%\n🕒 时间 Time：${time}\n${sep}\n` +
          `🏠 请留意家中电力情况 / Please check the power at home.`,
      };
    }
    case "power_connected": {
      const level = data.battery_level ?? "?";
      return {
        title: "🔌 电源已接通 Power Restored",
        text:
          `🔌 电源已接通 | Power Connected\n${sep}\n` +
          `📱 设备 Device：${device}\n🔋 当前电量 Level：${level}%\n🕒 时间 Time：${time}\n${sep}\n` +
          `✅ 充电中，一切正常 / Charging, all good.`,
      };
    }
    case "heartbeat": {
      const level = data.battery_level ?? "?";
      return {
        title: "💚 设备平安报告 Daily Report",
        text:
          `💚 设备平安报告 | Device Health Report\n${sep}\n` +
          `📱 设备 Device：${device}\n🔋 当前电量 Level：${level}%\n🌐 网络状态 Network：正常 OK\n🕒 时间 Time：${time}\n${sep}\n` +
          `🛡️ Hedwig 正在守护中 / Hedwig is on guard.`,
      };
    }
    case "service_on": {
      const level = data.battery_level ?? "?";
      return {
        title: "✅ 守护已开启 Guard Started",
        text:
          `✅ 守护已开启 | Guard Started\n${sep}\n` +
          `📱 设备 Device：${device}\n🔋 当前电量 Level：${level}%\n🕒 时间 Time：${time}\n${sep}\n` +
          `🛡️ Hedwig 开始守护，短信将实时转发。`,
      };
    }
    case "service_off": {
      const level = data.battery_level ?? "?";
      return {
        title: "🛑 守护已停止 Guard Stopped",
        text:
          `🛑 守护已停止 | Guard Stopped\n${sep}\n` +
          `📱 设备 Device：${device}\n🔋 当前电量 Level：${level}%\n🕒 时间 Time：${time}\n${sep}\n` +
          `⚠️ 短信将不再转发，请留意！`,
      };
    }
    default:
      return {
        title: "📨 Hedwig 通知 Notification",
        text:
          `📨 Hedwig 通知 | Notification\n📱 设备 Device：${device}\n🏷️ 类型 Type：${payload.type}\n` +
          `🕒 时间 Time：${time}\n📦 数据 Data：${JSON.stringify(data)}`,
      };
  }
}

/* ======================= 渠道 / channels ======================= */

const telegram = {
  id: "telegram",
  isConfigured: (env) => Boolean(env.TELEGRAM_BOT_TOKEN && env.TELEGRAM_CHAT_ID),
  async send(env, message) {
    const url = `https://api.telegram.org/bot${env.TELEGRAM_BOT_TOKEN}/sendMessage`;
    const res = await fetch(url, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        chat_id: env.TELEGRAM_CHAT_ID,
        text: esc(message.text),
        parse_mode: "HTML",
        disable_web_page_preview: true,
      }),
    });
    if (!res.ok) throw new Error(`Telegram HTTP ${res.status}: ${await res.text()}`);
    return true;
  },
};

async function feishuSign(secret, timestamp) {
  const stringToSign = `${timestamp}\n${secret}`;
  const key = await crypto.subtle.importKey(
    "raw",
    new TextEncoder().encode(stringToSign),
    { name: "HMAC", hash: "SHA-256" },
    false,
    ["sign"],
  );
  const sig = await crypto.subtle.sign("HMAC", key, new Uint8Array(0));
  return btoa(String.fromCharCode(...new Uint8Array(sig)));
}

const feishu = {
  id: "feishu",
  isConfigured: (env) => Boolean(env.FEISHU_WEBHOOK_URL),
  async send(env, message) {
    const payload = { msg_type: "text", content: { text: message.text } };
    if (env.FEISHU_SIGN_SECRET) {
      const ts = Math.floor(Date.now() / 1000);
      payload.timestamp = String(ts);
      payload.sign = await feishuSign(env.FEISHU_SIGN_SECRET, ts);
    }
    const res = await fetch(env.FEISHU_WEBHOOK_URL, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload),
    });
    const body = await res.json().catch(() => ({}));
    if (!res.ok || (typeof body.code === "number" && body.code !== 0)) {
      throw new Error(`Feishu error: ${JSON.stringify(body)}`);
    }
    return true;
  },
};

const discord = {
  id: "discord",
  isConfigured: (env) => Boolean(env.DISCORD_WEBHOOK_URL),
  async send(env, message) {
    const res = await fetch(env.DISCORD_WEBHOOK_URL, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ username: "HedwigSMS", content: message.text.slice(0, 1990) }),
    });
    if (!res.ok) throw new Error(`Discord HTTP ${res.status}: ${await res.text()}`);
    return true;
  },
};

const slack = {
  id: "slack",
  isConfigured: (env) => Boolean(env.SLACK_WEBHOOK_URL),
  async send(env, message) {
    const res = await fetch(env.SLACK_WEBHOOK_URL, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ text: message.text }),
    });
    if (!res.ok) throw new Error(`Slack HTTP ${res.status}: ${await res.text()}`);
    return true;
  },
};

const webhook = {
  id: "webhook",
  isConfigured: (env) => Boolean(env.CUSTOM_WEBHOOK_URL),
  async send(env, message, payload) {
    const res = await fetch(env.CUSTOM_WEBHOOK_URL, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        title: message.title,
        body: message.text,
        text: message.text,
        type: payload.type,
        device_name: payload.device_name,
        timestamp: payload.timestamp,
        data: payload.data || {},
      }),
    });
    if (!res.ok) throw new Error(`Custom webhook HTTP ${res.status}: ${await res.text()}`);
    return true;
  },
};

const email = {
  id: "email",
  isConfigured: (env) => Boolean(env.RESEND_API_KEY && env.EMAIL_FROM && env.EMAIL_TO),
  async send(env, message) {
    const to = env.EMAIL_TO.split(",").map((s) => s.trim()).filter(Boolean);
    const res = await fetch("https://api.resend.com/emails", {
      method: "POST",
      headers: {
        Authorization: `Bearer ${env.RESEND_API_KEY}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        from: env.EMAIL_FROM,
        to,
        subject: `[HedwigSMS] ${message.title}`,
        html:
          `<div style="margin:0;padding:24px 12px;background:#0A0D12;font-family:-apple-system,'PingFang SC','Segoe UI',Roboto,Helvetica,Arial,sans-serif;">` +
          `<div style="max-width:480px;margin:0 auto;background:#ffffff;border-radius:18px;overflow:hidden;box-shadow:0 10px 36px rgba(0,0,0,.35);">` +
          `<div style="background:linear-gradient(135deg,#0e2a20 0%,#0a0d12 100%);padding:20px 24px;">` +
          `<div style="color:#ffffff;font-size:18px;font-weight:700;">🦉 HedwigSMS</div>` +
          `<div style="color:#34D399;font-size:13px;margin-top:3px;">${message.title}</div></div>` +
          `<div style="padding:22px 24px;color:#1b1f24;font-size:15px;line-height:1.75;white-space:pre-wrap;word-break:break-word;">${esc(message.text)}</div>` +
          `<div style="padding:14px 24px;border-top:1px solid #eef0f2;color:#9aa6b2;font-size:12px;">本邮件由 HedwigSMS 自动转发 · 全本地运行 · 零数据收集</div>` +
          `</div></div>`,
        text: message.text,
      }),
    });
    if (!res.ok) throw new Error(`Resend HTTP ${res.status}: ${await res.text()}`);
    return true;
  },
};

const channels = [telegram, feishu, discord, slack, webhook, email];

/* ================== 渠道环境变量解析 / resolve channel env ================== */

function kvPick(kvVal, envVal) {
  return kvVal !== undefined && kvVal !== null ? kvVal : envVal;
}

/** 把 KV 中的渠道配置覆盖到 env（KV 有该字段就用 KV，否则用 env 初始值）。 */
function resolveEnv(env, cfg) {
  const cc = cfg.channelConfig || {};
  const out = Object.assign({}, env);
  for (const [ch, fields] of Object.entries(CHANNEL_FIELDS)) {
    const stored = cc[ch] || {};
    for (const [field, , envName] of fields) {
      out[envName] = kvPick(stored[field], env[envName]);
    }
  }
  return out;
}

function effectiveToken(env, cfg) {
  return cfg.authToken || env.AUTH_TOKEN || "";
}

/* ======================= 设置存储 / config store ======================= */

function mergeConfig(stored) {
  const s = stored && typeof stored === "object" ? stored : {};
  const d = DEFAULT_CONFIG;
  return {
    version: 2,
    authToken: typeof s.authToken === "string" ? s.authToken : "",
    minIntervalSec:
      Number.isFinite(Number(s.minIntervalSec)) && Number(s.minIntervalSec) > 0
        ? Math.floor(Number(s.minIntervalSec))
        : 0,
    deviceName: typeof s.deviceName === "string" ? s.deviceName : "",
    passwordHash: typeof s.passwordHash === "string" ? s.passwordHash : "",
    pauseAll: !!s.pauseAll,
    updatedAt: typeof s.updatedAt === "string" ? s.updatedAt : "",
    events: { ...d.events, ...(s.events || {}) },
    channels: { ...d.channels, ...(s.channels || {}) },
    channelConfig: s.channelConfig && typeof s.channelConfig === "object" ? s.channelConfig : {},
  };
}

async function loadConfig(env) {
  if (!env.HEDWIG_CONFIG) return mergeConfig({});
  try {
    return mergeConfig(await env.HEDWIG_CONFIG.get("config", "json"));
  } catch {
    return mergeConfig({});
  }
}

async function saveConfig(env, cfg) {
  if (!env.HEDWIG_CONFIG) throw new Error("HEDWIG_CONFIG 未绑定");
  cfg.updatedAt = new Date().toISOString();
  await env.HEDWIG_CONFIG.put("config", JSON.stringify(cfg));
}

async function appendLog(env, entry) {
  if (!env.HEDWIG_CONFIG) return;
  try {
    let arr = (await env.HEDWIG_CONFIG.get("log", "json")) || [];
    if (!Array.isArray(arr)) arr = [];
    arr.unshift(entry);
    if (arr.length > 20) arr = arr.slice(0, 20);
    await env.HEDWIG_CONFIG.put("log", JSON.stringify(arr));
  } catch {}
}

async function loadLog(env) {
  if (!env.HEDWIG_CONFIG) return [];
  try {
    const arr = await env.HEDWIG_CONFIG.get("log", "json");
    return Array.isArray(arr) ? arr : [];
  } catch {
    return [];
  }
}

/* ======================= 会话 / session ======================= */

function sessionKey(env) {
  return env.ADMIN_PASSWORD || "hedwig-session-key";
}

async function makeSession(env) {
  const exp = Date.now() + SESSION_MS;
  const sig = await hmacHex(sessionKey(env), "hedwig:" + exp);
  return exp + "." + sig;
}

async function isAuthed(env, request) {
  const cookie = request.headers.get("Cookie") || "";
  const m = cookie.match(new RegExp("(?:^|;\\s*)" + COOKIE_NAME + "=([^;]+)"));
  if (!m) return false;
  const val = decodeURIComponent(m[1]);
  const i = val.lastIndexOf(".");
  if (i < 0) return false;
  const exp = val.slice(0, i);
  const sig = val.slice(i + 1);
  if (!exp || !Number.isFinite(Number(exp)) || Date.now() > Number(exp)) return false;
  return safeEqual(sig, await hmacHex(sessionKey(env), "hedwig:" + exp));
}

async function checkPassword(env, cfg, input) {
  if (cfg.passwordHash) return safeEqual(await sha256hex(input), cfg.passwordHash);
  return safeEqual(input, env.ADMIN_PASSWORD || "");
}

/* ============================ 页面 / pages ============================ */

function theme() {
  return `:root{--bg:#0A0D12;--card:#141a22;--card2:#1b232d;--line:#26303c;--fg:#e8eef5;--mut:#8b98a7;--acc:#34D399;--acc2:#0e2a20}
*{box-sizing:border-box}
body{margin:0;background:var(--bg);color:var(--fg);font-family:-apple-system,'PingFang SC','Segoe UI',Roboto,Helvetica,Arial,sans-serif;line-height:1.6}
a{color:var(--acc)}
.wrap{max-width:860px;margin:0 auto;padding:22px 16px 60px}
.hero{background:linear-gradient(135deg,#0e2a20 0%,#0a0d12 100%);border:1px solid var(--line);border-radius:18px;padding:20px 22px;margin-bottom:18px;display:flex;justify-content:space-between;gap:14px;align-items:center;flex-wrap:wrap}
.hero h1{margin:0;font-size:21px}
.hero p{margin:6px 0 0;color:var(--mut);font-size:13px}
.card{background:var(--card);border:1px solid var(--line);border-radius:16px;padding:18px 20px;margin-bottom:16px}
.card h2{margin:0 0 6px;font-size:16px}
.card .sub{color:var(--mut);font-size:12px;margin:0 0 14px}
.grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(200px,1fr));gap:10px}
.sw{display:flex;align-items:center;gap:10px;background:var(--card2);border:1px solid var(--line);border-radius:11px;padding:10px 12px;cursor:pointer;user-select:none;font-size:14px}
.sw input{width:18px;height:18px;accent-color:var(--acc)}
.badge{display:inline-block;font-size:12px;padding:1px 8px;border-radius:999px;border:1px solid var(--line);color:var(--mut);margin-left:6px}
.badge.on{color:#062;background:var(--acc);border-color:var(--acc);font-weight:600}
label.f{display:block;font-size:13px;color:var(--mut);margin:10px 0 4px}
input[type=text],input[type=password],input[type=number],select,textarea{width:100%;background:#0d1218;border:1px solid var(--line);color:var(--fg);border-radius:10px;padding:10px 12px;font-size:14px;font-family:inherit}
textarea{min-height:64px;resize:vertical}
.btn{display:inline-block;border:0;border-radius:11px;padding:10px 16px;font-size:14px;font-weight:600;cursor:pointer;background:var(--acc);color:#06221a;white-space:nowrap}
.btn.ghost{background:transparent;color:var(--fg);border:1px solid var(--line)}
.btn.warn{background:#f87171;color:#2a0a0a}
.row{display:flex;gap:10px;flex-wrap:wrap;align-items:center;margin-top:14px}
.muted{color:var(--mut);font-size:13px}
.mono{font-family:ui-monospace,SFMono-Regular,Menlo,Consolas,monospace;background:#0d1218;border:1px solid var(--line);border-radius:9px;padding:9px 11px;font-size:13px;word-break:break-all}
.msg{border-radius:11px;padding:11px 14px;font-size:14px;margin-bottom:16px;border:1px solid}
.msg.ok{background:#0e2a20;border-color:#1c5a44;color:#a7f3d0}
.msg.err{background:#2a0e0e;border-color:#5a1c1c;color:#fecaca}
.msg.warn{background:#2a2410;border-color:#5a4a1c;color:#fde68a}
.login{max-width:360px;margin:12vh auto 0}
.foot{text-align:center;color:var(--mut);font-size:12px;margin-top:20px}
.ch{border:1px solid var(--line);border-radius:13px;padding:14px;margin-bottom:12px;background:var(--card2)}
.ch h3{margin:0 0 4px;font-size:15px;display:flex;align-items:center;gap:8px}
.two{display:grid;grid-template-columns:1fr 1fr;gap:12px}
.logs{font-size:13px}
.logrow{display:flex;gap:10px;padding:8px 0;border-bottom:1px solid var(--line);flex-wrap:wrap;align-items:center}
.logrow:last-child{border-bottom:0}
.logrow .t{color:var(--mut);font-family:ui-monospace,monospace;font-size:12px}
.pill{font-size:11px;padding:1px 7px;border-radius:999px;border:1px solid var(--line)}
.pill.ok{border-color:#1c5a44;color:#a7f3d0}
.pill.no{border-color:#5a1c1c;color:#fecaca}
@media(max-width:560px){.two{grid-template-columns:1fr}}`;
}

function pageShell(title, body, headExtra) {
  return `<!DOCTYPE html><html lang="zh-CN"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<meta name="robots" content="noindex, nofollow">
<title>${esc(title)}</title>
<style>${theme()}</style>${headExtra || ""}</head><body><div class="wrap">${body}<div class="foot">🦉 HedwigSMS · 全本地运行 · 零数据收集</div></div></body></html>`;
}

function loginPage(err) {
  const errBox = err ? `<div class="msg err">密码错误，请重试。</div>` : "";
  return pageShell(
    "HedwigSMS 登录",
    `<div class="login">
      <div class="hero"><div><h1>🦉 HedwigSMS 控制台</h1><p>请输入后台密码</p></div></div>
      <div class="card">
        ${errBox}
        <form method="POST">
          <input type="hidden" name="action" value="login">
          <label class="f">密码</label>
          <input type="password" name="password" autofocus autocomplete="current-password">
          <div class="row"><button class="btn" type="submit">登录</button></div>
        </form>
      </div>
    </div>`,
  );
}

function panelPage(cfg, env, msg, origin, adminPath) {
  let msgBox = "";
  if (msg === "saved") msgBox = `<div class="msg ok">设置已保存。</div>`;
  else if (msg === "login") msgBox = `<div class="msg ok">登录成功。</div>`;
  else if (msg === "pw") msgBox = `<div class="msg ok">密码已更新。</div>`;
  else if (msg === "regen")
    msgBox = `<div class="msg warn">已生成新的 Token，请立即在 App 中更新，否则将收不到推送。</div>`;
  else if (msg && msg.startsWith("test:")) {
    const rest = msg.slice(5);
    msgBox = rest.includes("失败")
      ? `<div class="msg err">测试结果：${esc(rest)}</div>`
      : `<div class="msg ok">测试结果：${esc(rest)}</div>`;
  } else if (msg) msgBox = `<div class="msg err">${esc(msg)}</div>`;

  const token = effectiveToken(env, cfg);
  const tokenFrom = cfg.authToken ? "（后台生成，已覆盖环境变量）" : "（来自环境变量 AUTH_TOKEN）";
  const isConfigured = (ch) => {
    const e = resolveEnv(env, cfg);
    const c = channels.find((x) => x.id === ch);
    return c ? c.isConfigured(e) : false;
  };

  const events = EVENT_META.map(
    ([k, label]) =>
      `<label class="sw"><input type="checkbox" name="event:${k}" ${cfg.events[k] !== false ? "checked" : ""}><span>${label}</span></label>`,
  ).join("");

  const chans = CHANNEL_META.map(([k, label]) => {
    const on = isConfigured(k);
    return `<label class="sw"><input type="checkbox" name="channel:${k}" ${cfg.channels[k] !== false ? "checked" : ""}><span>${label}<span class="badge ${on ? "on" : ""}">${on ? "已配置" : "未配置"}</span></span></label>`;
  }).join("");

  const channelEditors = CHANNEL_META.map(([ch, label]) => {
    const e = resolveEnv(env, cfg);
    const fields = CHANNEL_FIELDS[ch]
      .map(([field, flabel, envName]) => {
        const val = e[envName] || "";
        const fromEnv = (cfg.channelConfig?.[ch]?.[field] === undefined) && val;
        const hint = fromEnv ? `<span class="badge">来自环境变量</span>` : "";
        return `<label class="f">${esc(flabel)} ${hint}</label>
          <input type="text" name="ch:${ch}:${field}" value="${esc(val)}" placeholder="留空表示不使用">`;
      })
      .join("");
    const on = isConfigured(ch);
    return `<div class="ch">
        <h3>${esc(label)} <span class="badge ${on ? "on" : ""}">${on ? "已配置" : "未配置"}</span></h3>
        <div class="two">${fields}</div>
      </div>`;
  }).join("");

  const eventOptions = EVENT_META.map(([k, label]) => `<option value="${k}">${label}</option>`).join("");
  const channelOptions = [
    `<option value="__all__">全部已启用渠道</option>`,
    ...CHANNEL_META.map(([k, label]) => `<option value="${k}">${label}</option>`),
  ].join("");

  const logs = (env.__logs || [])
    .map((l) => {
      const pills = (l.results || [])
        .map((r) => `<span class="pill ${r.ok ? "ok" : "no"}">${esc(r.channel)}:${r.ok ? "ok" : "失败"}</span>`)
        .join(" ");
      return `<div class="logrow"><span class="t">${esc(l.time || "")}</span><span>${esc(l.type || "")}</span><span class="badge">${esc(l.source || "")}</span><span>${pills}</span></div>`;
    })
    .join("") || `<p class="muted">暂无记录。</p>`;

  const pauseBanner = cfg.pauseAll
    ? `<div class="msg warn">⚠️ 当前已「暂停全部推送」，所有事件都不会发送。</div>`
    : "";

  return pageShell(
    "HedwigSMS 控制台",
    `<div class="hero">
      <div><h1>🦉 HedwigSMS 控制台</h1><p>Worker：${esc(origin)} · 后台路径：${esc(adminPath)}</p></div>
      <form method="POST"><input type="hidden" name="action" value="logout"><button class="btn ghost" type="submit">退出</button></form>
    </div>
    ${pauseBanner}
    ${msgBox}

    <div class="card">
      <h2>连接信息</h2>
      <div class="sub">App 中填写的 Worker URL 与 Token。</div>
      <label class="f">Worker URL</label>
      <div class="mono">${esc(origin)}</div>
      <label class="f">Token ${esc(tokenFrom)}</label>
      <div class="mono">${esc(token || "(未设置)")}</div>
      <form method="POST" class="row">
        <input type="hidden" name="action" value="regen">
        <button class="btn ghost" type="submit" onclick="return confirm('重新生成后，App 里的旧 Token 会立即失效，需手动更新。确定继续？')">重新生成 Token</button>
      </form>
      <p class="muted">更新时间：${cfg.updatedAt ? esc(cfg.updatedAt) : "—"}</p>
    </div>

    <form method="POST">
      <input type="hidden" name="action" value="save">

      <div class="card">
        <h2>运行状态</h2>
        <label class="sw"><input type="checkbox" name="pauseAll" ${cfg.pauseAll ? "checked" : ""}><span>暂停全部推送（维护模式）</span></label>
      </div>

      <div class="card">
        <h2>推送事件开关</h2>
        <div class="sub">关闭的事件完全不会推送。</div>
        <div class="grid">${events}</div>
      </div>

      <div class="card">
        <h2>推送渠道开关</h2>
        <div class="sub">开关控制该渠道是否参与推送；下方可填写或修改各渠道配置。</div>
        <div class="grid">${chans}</div>
      </div>

      <div class="card">
        <h2>渠道配置</h2>
        <div class="sub">已显示现有配置（含环境变量）。修改后保存即写入 KV，并可覆盖环境变量。</div>
        ${channelEditors}
      </div>

      <div class="card">
        <h2>定时播报</h2>
        <div class="sub">定时播报由 App 端负责（App「设置 → 每日平安报告」中配置时间）。本 Worker 只做实时中转。</div>
      </div>

      <div class="card">
        <h2>频率与设备</h2>
        <label class="f">最小推送间隔（秒，0 = 不限流）</label>
        <input type="number" name="minIntervalSec" min="0" step="1" value="${cfg.minIntervalSec}">
        <p class="muted">两条推送之间至少间隔该秒数，防止刷屏。注意：设太大会挡掉验证码短信。</p>
        <label class="f">设备名称（留空则使用 App 上报的名称）</label>
        <input type="text" name="deviceName" value="${esc(cfg.deviceName)}" placeholder="例如：家里的备用机">
        <div class="row"><button class="btn" type="submit">保存全部设置</button></div>
      </div>
    </form>

    <form method="POST">
      <input type="hidden" name="action" value="test">
      <div class="card">
        <h2>测试推送</h2>
        <div class="two">
          <div><label class="f">事件类型</label><select name="testType">${eventOptions}</select></div>
          <div><label class="f">渠道</label><select name="testChannel">${channelOptions}</select></div>
        </div>
        <div class="row"><button class="btn ghost" type="submit">发送测试</button></div>
      </div>
    </form>

    <div class="card">
      <h2>最近推送记录</h2>
      <div class="sub">仅保留最近 20 条。</div>
      <div class="logs">${logs}</div>
    </div>

    <form method="POST">
      <input type="hidden" name="action" value="password">
      <div class="card">
        <h2>修改后台密码</h2>
        <div class="two">
          <div><label class="f">新密码</label><input type="password" name="newPassword" autocomplete="new-password"></div>
          <div><label class="f">确认新密码</label><input type="password" name="newPassword2" autocomplete="new-password"></div>
        </div>
        <div class="row"><button class="btn ghost" type="submit">更新密码</button></div>
      </div>
    </form>`,
  );
}

/* ======================= 分发 / dispatch ======================= */

async function dispatch(mergedEnv, cfg, payload, onlyChannel) {
  const message = formatMessage(payload);
  const active = channels.filter(
    (c) =>
      c.isConfigured(mergedEnv) &&
      cfg.channels[c.id] !== false &&
      (!onlyChannel || onlyChannel === "__all__" || c.id === onlyChannel),
  );
  if (active.length === 0) {
    return { ok: false, error: "没有可用的渠道（未配置或已关闭）。", results: [] };
  }
  const settled = await Promise.allSettled(active.map((c) => c.send(mergedEnv, message, payload)));
  const results = active.map((c, i) => ({
    channel: c.id,
    ok: settled[i].status === "fulfilled",
    error:
      settled[i].status === "rejected"
        ? String(settled[i].reason?.message || settled[i].reason)
        : undefined,
  }));
  return { ok: results.some((r) => r.ok), results };
}

/* ======================= 中转入口 / relay ======================= */

async function handleRelay(request, env) {
  const url = new URL(request.url);
  const cfg = await loadConfig(env);
  const token = url.searchParams.get("token");
  const expected = effectiveToken(env, cfg);
  if (!expected) {
    return json({ ok: false, error: "Server misconfigured: TOKEN not set." }, 500);
  }
  if (!safeEqual(token || "", expected)) {
    return json({ ok: false, error: "Unauthorized: bad token." }, 401);
  }

  let payload;
  try {
    payload = await request.json();
  } catch {
    return json({ ok: false, error: "Bad Request: invalid JSON." }, 400);
  }
  if (!payload || typeof payload !== "object" || !VALID_TYPES.has(payload.type)) {
    return json({ ok: false, error: `Bad Request: unknown type "${payload?.type}".` }, 400);
  }

  if (cfg.pauseAll) return json({ ok: true, type: payload.type, skipped: "paused" });
  if (cfg.events[payload.type] === false) {
    return json({ ok: true, type: payload.type, skipped: "event-disabled" });
  }
  if (cfg.deviceName) payload = { ...payload, device_name: cfg.deviceName };

  if (cfg.minIntervalSec > 0 && env.HEDWIG_CONFIG) {
    const now = Date.now();
    let last = 0;
    try {
      last = Number(await env.HEDWIG_CONFIG.get("lastPush")) || 0;
    } catch {}
    const wait = cfg.minIntervalSec * 1000 - (now - last);
    if (wait > 0) {
      return json({ ok: true, type: payload.type, skipped: "rate-limited", waitSec: Math.ceil(wait / 1000) });
    }
    try {
      await env.HEDWIG_CONFIG.put("lastPush", String(now));
    } catch {}
  }

  const merged = resolveEnv(env, cfg);
  const result = await dispatch(merged, cfg, payload);
  await appendLog(env, {
    time: formatTime(Date.now()),
    type: payload.type,
    source: "实时",
    results: result.results,
  });
  return json({ ok: result.ok, type: payload.type, results: result.results, error: result.error }, result.ok ? 200 : 502);
}

/* ======================= 后台 / admin ======================= */

async function handleAdmin(request, env, adminPath) {
  if (request.method === "GET") {
    const url = new URL(request.url);
    if (!(await isAuthed(env, request))) return html(loginPage(url.searchParams.get("err")));
    const cfg = await loadConfig(env);
    const logs = await loadLog(env);
    env = { ...env, __logs: logs.reverse() };
    return html(panelPage(cfg, env, url.searchParams.get("msg"), url.origin, adminPath));
  }

  if (request.method !== "POST") return json({ ok: false, error: "Method Not Allowed" }, 405);

  let form;
  try {
    form = await request.formData();
  } catch {
    return json({ ok: false, error: "Bad Request" }, 400);
  }
  const action = String(form.get("action") || "");

  if (action === "login") {
    const cfg = await loadConfig(env);
    const pw = String(form.get("password") || "");
    if (await checkPassword(env, cfg, pw)) {
      const tok = await makeSession(env);
      return redirect(
        adminPath + "?msg=login",
        `${COOKIE_NAME}=${encodeURIComponent(tok)}; Path=/; HttpOnly; Secure; SameSite=Lax; Max-Age=${Math.floor(SESSION_MS / 1000)}`,
      );
    }
    return redirect(adminPath + "?err=1");
  }

  if (!(await isAuthed(env, request))) return redirect(adminPath + "?err=1");

  if (action === "logout") {
    return redirect(adminPath, `${COOKIE_NAME}=; Path=/; HttpOnly; Secure; SameSite=Lax; Max-Age=0`);
  }

  const cfg = await loadConfig(env);

  if (action === "regen") {
    cfg.authToken = randomToken();
    await saveConfig(env, cfg);
    return redirect(adminPath + "?msg=regen");
  }

  if (action === "save") {
    cfg.pauseAll = form.get("pauseAll") != null;
    for (const [k] of EVENT_META) cfg.events[k] = form.get("event:" + k) != null;
    for (const [k] of CHANNEL_META) cfg.channels[k] = form.get("channel:" + k) != null;

    const cc = { ...(cfg.channelConfig || {}) };
    for (const [ch, fields] of Object.entries(CHANNEL_FIELDS)) {
      const one = { ...(cc[ch] || {}) };
      for (const [field] of fields) {
        const v = form.get(`ch:${ch}:${field}`);
        if (v !== null) one[field] = String(v).trim();
      }
      cc[ch] = one;
    }
    cfg.channelConfig = cc;

    const mi = Number(form.get("minIntervalSec"));
    cfg.minIntervalSec = Number.isFinite(mi) && mi > 0 ? Math.floor(mi) : 0;
    cfg.deviceName = String(form.get("deviceName") || "").slice(0, 60);

    await saveConfig(env, cfg);
    return redirect(adminPath + "?msg=saved");
  }

  if (action === "password") {
    const p1 = String(form.get("newPassword") || "");
    const p2 = String(form.get("newPassword2") || "");
    if (!p1 || p1 !== p2) return redirect(adminPath + "?msg=" + encodeURIComponent("两次密码不一致或为空"));
    if (p1.length < 6) return redirect(adminPath + "?msg=" + encodeURIComponent("密码太短，至少 6 位"));
    cfg.passwordHash = await sha256hex(p1);
    await saveConfig(env, cfg);
    return redirect(adminPath + "?msg=pw");
  }

  if (action === "test") {
    const type = String(form.get("testType") || "sms");
    const only = String(form.get("testChannel") || "__all__");
    const merged = resolveEnv(env, cfg);
    const sample = {
      type: VALID_TYPES.has(type) ? type : "sms",
      device_name: cfg.deviceName || "后台测试",
      timestamp: Date.now(),
      data: { sender: "10086", body: "这是一条来自 HedwigSMS 后台的测试消息。", sim: "卡1 · 中国移动", battery_level: 88 },
    };
    const res = await dispatch(merged, cfg, sample, only);
    await appendLog(env, { time: formatTime(Date.now()), type: sample.type, source: "测试", results: res.results });
    const summary = res.results.map((r) => `${r.channel}:${r.ok ? "ok" : "失败"}`).join(" ") || res.error || "无可用渠道";
    return redirect(adminPath + "?msg=" + encodeURIComponent("test:" + summary));
  }

  return redirect(adminPath + "?msg=" + encodeURIComponent("未知操作"));
}

/* ======================= 入口 / entry ======================= */

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    const adminPath = (env.ADMIN_PATH || "/admin").replace(/\/+$/, "");
    const path = url.pathname.replace(/\/+$/, "") || "/";

    if (path === adminPath) return handleAdmin(request, env, adminPath);

    if (request.method === "GET") {
      return json({ ok: true, service: "HedwigSMS Worker", hint: "POST your payload here." });
    }
    if (request.method !== "POST") return json({ ok: false, error: "Method Not Allowed" }, 405);
    return handleRelay(request, env);
  },
};
