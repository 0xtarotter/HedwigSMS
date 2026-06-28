/**
 * HedwigSMS Cloudflare Worker — 自包含单文件版 / self-contained single-file build
 * ==================================================================================
 * 👉 最适合新手：直接复制本文件全部内容，粘贴到 Cloudflare Dashboard 的
 *    Workers 编辑器（Quick Edit）即可部署，无需任何命令行 / build 工具。
 * 👉 Beginner-friendly: copy this entire file and paste it into the Cloudflare
 *    Dashboard Workers editor (Quick Edit). No CLI / build tools required.
 *
 * 部署后请在 Worker 的「Settings → Variables」中配置环境变量（见下方注释）。
 * After deploying, set environment variables under "Settings → Variables".
 *
 * 工程化（多文件）版本见 worker/src/。/ See worker/src/ for the modular version.
 *
 * Message dispatch payload shapes adapted from OpenClaw (MIT).
 * Copyright (c) 2026 OpenClaw Foundation.
 *
 * ─────────────────────────  环境变量速查 / ENV VARS  ─────────────────────────
 *  必填 Required:
 *    AUTH_TOKEN              访问令牌，需与 App 中填写的一致 / shared secret
 *  至少配置一个渠道 / configure at least ONE channel:
 *    TELEGRAM_BOT_TOKEN + TELEGRAM_CHAT_ID
 *    FEISHU_WEBHOOK_URL  (+ 可选 FEISHU_SIGN_SECRET)
 *    DISCORD_WEBHOOK_URL
 *    SLACK_WEBHOOK_URL
 *    CUSTOM_WEBHOOK_URL  (通用：Bark/Server酱/企业微信/钉钉/n8n…)
 *    RESEND_API_KEY + EMAIL_FROM + EMAIL_TO  (邮件 via Resend)
 * ────────────────────────────────────────────────────────────────────────────
 */

const VALID_TYPES = new Set([
  "sms",
  "battery_low",
  "power_connected",
  "power_disconnected",
  "heartbeat",
]);

/* ============================ 工具 / utils ============================ */

function safeEqual(a, b) {
  if (typeof a !== "string" || typeof b !== "string") return false;
  if (a.length !== b.length) return false;
  let diff = 0;
  for (let i = 0; i < a.length; i++) diff |= a.charCodeAt(i) ^ b.charCodeAt(i);
  return diff === 0;
}

function json(data, status = 200) {
  return new Response(JSON.stringify(data, null, 2), {
    status,
    headers: { "Content-Type": "application/json; charset=utf-8" },
  });
}

function formatTime(ts) {
  const ms = typeof ts === "number" && ts > 0 ? ts : Date.now();
  const d = new Date(ms + 8 * 60 * 60 * 1000); // UTC -> UTC+8 (北京时间)
  const pad = (n) => String(n).padStart(2, "0");
  return (
    `${d.getUTCFullYear()}-${pad(d.getUTCMonth() + 1)}-${pad(d.getUTCDate())} ` +
    `${pad(d.getUTCHours())}:${pad(d.getUTCMinutes())}:${pad(d.getUTCSeconds())}`
  );
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

function escapeHtml(s) {
  return String(s).replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
}

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
        text: escapeHtml(message.text),
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
  const enc = new TextEncoder();
  const key = await crypto.subtle.importKey(
    "raw",
    enc.encode(stringToSign),
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
          `<div style="padding:22px 24px;color:#1b1f24;font-size:15px;line-height:1.75;white-space:pre-wrap;word-break:break-word;">${escapeHtml(message.text)}</div>` +
          `<div style="padding:14px 24px;border-top:1px solid #eef0f2;color:#9aa6b2;font-size:12px;">本邮件由 HedwigSMS 自动转发 · 全本地运行 · 零数据收集</div>` +
          `</div></div>`,
        text: message.text,
      }),
    });
    if (!res.ok) throw new Error(`Resend HTTP ${res.status}: ${await res.text()}`);
    return true;
  },
};

// 新增渠道：实现 {id, isConfigured, send} 并加入此数组 / add channels here.
const channels = [telegram, feishu, discord, slack, webhook, email];

/* ======================= 入口 / entry ======================= */

export default {
  async fetch(request, env) {
    if (request.method === "GET") {
      return json({ ok: true, service: "HedwigSMS Worker", hint: "POST your payload here." });
    }
    if (request.method !== "POST") {
      return json({ ok: false, error: "Method Not Allowed" }, 405);
    }

    const url = new URL(request.url);
    const token = url.searchParams.get("token");
    if (!env.AUTH_TOKEN) {
      return json({ ok: false, error: "Server misconfigured: AUTH_TOKEN not set." }, 500);
    }
    if (!safeEqual(token || "", env.AUTH_TOKEN)) {
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

    const message = formatMessage(payload);
    const active = channels.filter((c) => c.isConfigured(env));
    if (active.length === 0) {
      return json({ ok: false, error: "No channel configured. Set at least one channel's env vars." }, 500);
    }

    const settled = await Promise.allSettled(active.map((c) => c.send(env, message, payload)));
    const results = active.map((c, i) => ({
      channel: c.id,
      ok: settled[i].status === "fulfilled",
      error:
        settled[i].status === "rejected"
          ? String(settled[i].reason?.message || settled[i].reason)
          : undefined,
    }));

    const anyOk = results.some((r) => r.ok);
    return json({ ok: anyOk, type: payload.type, results }, anyOk ? 200 : 502);
  },
};
