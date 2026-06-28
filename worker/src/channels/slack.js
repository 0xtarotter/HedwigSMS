/**
 * Slack Incoming Webhook 渠道 / Slack Incoming Webhook channel
 * ------------------------------------------------------------------
 * Payload shape adapted from OpenClaw (MIT). Copyright (c) 2026 OpenClaw Foundation.
 * 采用最简单的 Incoming Webhook（无需 bot token）/ Uses the simplest Incoming Webhook.
 *
 * 环境变量 / Env vars:
 *   SLACK_WEBHOOK_URL  https://hooks.slack.com/services/xxx/yyy/zzz
 */

const ID = "slack";

function isConfigured(env) {
  return Boolean(env.SLACK_WEBHOOK_URL);
}

async function send(env, message) {
  const res = await fetch(env.SLACK_WEBHOOK_URL, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ text: message.text }),
  });
  if (!res.ok) {
    const body = await res.text();
    throw new Error(`Slack HTTP ${res.status}: ${body}`);
  }
  return true;
}

export default { id: ID, isConfigured, send };
