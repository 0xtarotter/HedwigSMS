/**
 * 通用自定义 Webhook 渠道 / Generic custom Webhook channel
 * ------------------------------------------------------------------
 * 通用兜底渠道：把「格式化文本 + 原始负载」一起 POST 过去，
 * 可对接 Bark / Server酱 / 企业微信机器人 / 钉钉 / n8n 等任意平台。
 * A catch-all channel: POSTs both the formatted text and the raw payload,
 * so you can wire it to Bark / ServerChan / WeCom bot / DingTalk / n8n, etc.
 *
 * 环境变量 / Env vars:
 *   CUSTOM_WEBHOOK_URL  目标地址 / target URL
 *
 * 发送的 JSON / Outgoing JSON:
 *   { "title": "...", "body": "...", "text": "...", "type": "...",
 *     "device_name": "...", "timestamp": 123, "data": {...} }
 * 说明 / Note: 额外带上 "body" 字段以兼容 Bark（Bark 只读 body 作正文）。
 *   `body` is included for Bark compatibility (Bark reads `body` as the content).
 */

const ID = "webhook";

function isConfigured(env) {
  return Boolean(env.CUSTOM_WEBHOOK_URL);
}

async function send(env, message, payload) {
  const res = await fetch(env.CUSTOM_WEBHOOK_URL, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      title: message.title,
      // Bark 只认 body 作正文；补上以便 Bark 能显示完整内容 / Bark reads `body`, so include it.
      body: message.text,
      text: message.text,
      type: payload.type,
      device_name: payload.device_name,
      timestamp: payload.timestamp,
      data: payload.data || {},
    }),
  });
  if (!res.ok) {
    const body = await res.text();
    throw new Error(`Custom webhook HTTP ${res.status}: ${body}`);
  }
  return true;
}

export default { id: ID, isConfigured, send };
