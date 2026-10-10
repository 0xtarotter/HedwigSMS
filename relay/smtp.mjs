/**
 * SMTP 邮件渠道（relay 专用）/ SMTP email channel (relay-only).
 * ------------------------------------------------------------------
 * 与 Android 直连的 SMTP 渠道对应，让本地中转也能用自备 SMTP 服务器发信，
 * 无需 Resend 等第三方账号。
 * Mirrors the Android direct-mode SMTP channel so the local relay can also send
 * email via your own SMTP server (no Resend account needed).
 *
 * 依赖 / Dependency: nodemailer（在 relay 目录执行 `npm install` 安装）。
 * 仅用于 Node relay —— 不放进 Worker 的共享渠道表，避免 Cloudflare 打包失败。
 * Relay-only; intentionally NOT added to the shared Worker channel registry.
 *
 * 环境变量 / Env vars:
 *   SMTP_HOST      SMTP 服务器，如 smtp.qq.com
 *   SMTP_PORT      端口（留空按 SMTP_SECURITY 自动 465/587/25）
 *   SMTP_USER      用户名（通常为邮箱）
 *   SMTP_PASS      密码 / 授权码
 *   SMTP_FROM      发件地址
 *   SMTP_TO        收件地址（多个用英文逗号分隔）
 *   SMTP_SECURITY  ssl（默认，465）/ starttls（587）/ none（25）
 */

const ID = "smtp";

function isConfigured(env) {
  return Boolean(env.SMTP_HOST && env.SMTP_FROM && env.SMTP_TO);
}

function emailHtml(title, text) {
  const safe = String(text).replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
  return (
    `<div style="margin:0;padding:24px 12px;background:#0A0D12;font-family:-apple-system,'PingFang SC','Segoe UI',Roboto,Helvetica,Arial,sans-serif;">` +
    `<div style="max-width:480px;margin:0 auto;background:#ffffff;border-radius:18px;overflow:hidden;box-shadow:0 10px 36px rgba(0,0,0,.35);">` +
    `<div style="background:linear-gradient(135deg,#0e2a20 0%,#0a0d12 100%);padding:20px 24px;">` +
    `<div style="color:#ffffff;font-size:18px;font-weight:700;letter-spacing:.3px;">🦉 HedwigSMS</div>` +
    `<div style="color:#34D399;font-size:13px;margin-top:3px;">${title}</div></div>` +
    `<div style="padding:22px 24px;color:#1b1f24;font-size:15px;line-height:1.75;white-space:pre-wrap;word-break:break-word;">${safe}</div>` +
    `<div style="padding:14px 24px;border-top:1px solid #eef0f2;color:#9aa6b2;font-size:12px;">本邮件由 HedwigSMS 自动转发 · 全本地运行 · 零数据收集</div>` +
    `</div></div>`
  );
}

let cachedKey = "";
let cachedTransport = null;

async function getTransport(env) {
  const security = env.SMTP_SECURITY || "ssl";
  const key = [env.SMTP_HOST, env.SMTP_PORT, env.SMTP_USER, env.SMTP_PASS, security].join("|");
  if (cachedTransport && cachedKey === key) return cachedTransport;
  cachedKey = key;

  let nodemailer;
  try {
    ({ default: nodemailer } = await import("nodemailer"));
  } catch (e) {
    throw new Error(
      "SMTP 需要 nodemailer：请在 relay 目录执行 `npm install`。原始错误： " + (e?.message || e),
    );
  }

  const port = Number(
    env.SMTP_PORT || (security === "starttls" ? 587 : security === "none" ? 25 : 465),
  );
  cachedTransport = nodemailer.createTransport({
    host: env.SMTP_HOST,
    port,
    secure: security === "ssl", // true = 465 implicit TLS
    requireTLS: security === "starttls",
    auth: env.SMTP_USER ? { user: env.SMTP_USER, pass: env.SMTP_PASS } : undefined,
  });
  return cachedTransport;
}

async function send(env, message) {
  const transport = await getTransport(env);
  const to = String(env.SMTP_TO)
    .split(",")
    .map((s) => s.trim())
    .filter(Boolean);
  await transport.sendMail({
    from: env.SMTP_FROM,
    to,
    subject: `[HedwigSMS] ${message.title}`,
    text: message.text,
    html: emailHtml(message.title, message.text),
  });
  return true;
}

export default { id: ID, isConfigured, send };
