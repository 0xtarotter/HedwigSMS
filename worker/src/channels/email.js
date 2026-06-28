/**
 * Email 渠道（via Resend）/ Email channel (via Resend)
 * ------------------------------------------------------------------
 * 说明 / Note: Cloudflare Workers 上原先常用的 MailChannels 免费方案已于 2024 年停止，
 * 这里改用 Resend 的 HTTP API（每月有免费额度，注册即用）。
 * 如需更换为 SendGrid / Mailgun / Postmark，只需改写本文件的 send()。
 * MailChannels' free tier for Workers was discontinued in 2024, so this uses
 * Resend's HTTP API instead. Swap send() for SendGrid/Mailgun/Postmark if desired.
 *
 * 环境变量 / Env vars:
 *   RESEND_API_KEY  Resend API key（https://resend.com）
 *   EMAIL_FROM      已在 Resend 验证域名的发件地址，如 hedwig@yourdomain.com
 *   EMAIL_TO        收件地址（多个用英文逗号分隔 / comma-separated for multiple）
 */

const ID = "email";

function isConfigured(env) {
  return Boolean(env.RESEND_API_KEY && env.EMAIL_FROM && env.EMAIL_TO);
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

async function send(env, message) {
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
      html: emailHtml(message.title, message.text),
      text: message.text,
    }),
  });
  if (!res.ok) {
    const body = await res.text();
    throw new Error(`Resend HTTP ${res.status}: ${body}`);
  }
  return true;
}

export default { id: ID, isConfigured, send };
