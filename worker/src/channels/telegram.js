/**
 * Telegram Bot 渠道 / Telegram Bot channel
 * ------------------------------------------------------------------
 * Payload shape adapted from OpenClaw (MIT). Copyright (c) 2026 OpenClaw Foundation.
 * 通过官方 Bot API 直接 fetch，无需任何 SDK / Direct fetch to the official Bot API.
 *
 * 环境变量 / Env vars:
 *   TELEGRAM_BOT_TOKEN  机器人 token（@BotFather 获取）
 *   TELEGRAM_CHAT_ID    目标会话 ID（个人/群组/频道）
 */

const ID = "telegram";

function isConfigured(env) {
  return Boolean(env.TELEGRAM_BOT_TOKEN && env.TELEGRAM_CHAT_ID);
}

/** 转义 Telegram HTML 特殊字符 / Escape HTML special chars for Telegram. */
function escapeHtml(s) {
  return String(s)
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;");
}

async function send(env, message) {
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
  if (!res.ok) {
    const body = await res.text();
    throw new Error(`Telegram HTTP ${res.status}: ${body}`);
  }
  return true;
}

export default { id: ID, isConfigured, send };
