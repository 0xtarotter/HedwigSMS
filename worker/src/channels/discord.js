/**
 * Discord Webhook 渠道 / Discord Webhook channel
 * ------------------------------------------------------------------
 * Payload shape adapted from OpenClaw (MIT). Copyright (c) 2026 OpenClaw Foundation.
 *
 * 环境变量 / Env vars:
 *   DISCORD_WEBHOOK_URL  频道 Webhook 地址（频道设置 → 整合 → Webhook）
 */

const ID = "discord";

function isConfigured(env) {
  return Boolean(env.DISCORD_WEBHOOK_URL);
}

async function send(env, message) {
  const res = await fetch(env.DISCORD_WEBHOOK_URL, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      username: "HedwigSMS",
      // Discord 单条消息上限 2000 字符 / 2000-char limit per message.
      content: message.text.slice(0, 1990),
    }),
  });
  if (!res.ok) {
    const body = await res.text();
    throw new Error(`Discord HTTP ${res.status}: ${body}`);
  }
  return true;
}

export default { id: ID, isConfigured, send };
