/**
 * 飞书 / Lark 自定义机器人渠道 / Feishu (Lark) custom-bot channel
 * ------------------------------------------------------------------
 * Payload shape adapted from OpenClaw (MIT). Copyright (c) 2026 OpenClaw Foundation.
 * 使用「群自定义机器人」Webhook，可选「签名校验」/ Uses group custom-bot webhook,
 * with optional signature verification.
 *
 * 环境变量 / Env vars:
 *   FEISHU_WEBHOOK_URL   自定义机器人地址（https://open.feishu.cn/open-apis/bot/v2/hook/xxx）
 *   FEISHU_SIGN_SECRET   （可选）开启「签名校验」时填写 / optional signing secret
 */

const ID = "feishu";

function isConfigured(env) {
  return Boolean(env.FEISHU_WEBHOOK_URL);
}

/**
 * 飞书签名算法 / Feishu signature:
 *   key = `${timestamp}\n${secret}`，用空消息体 HMAC-SHA256 后 Base64。
 * 参考飞书官方文档 / per Feishu official docs.
 */
async function genSign(secret, timestamp) {
  const stringToSign = `${timestamp}\n${secret}`;
  const enc = new TextEncoder();
  const key = await crypto.subtle.importKey(
    "raw",
    enc.encode(stringToSign),
    { name: "HMAC", hash: "SHA-256" },
    false,
    ["sign"],
  );
  // 飞书：以 stringToSign 作为 key，对空字符串签名 / sign empty data with stringToSign as key.
  const sig = await crypto.subtle.sign("HMAC", key, new Uint8Array(0));
  return btoa(String.fromCharCode(...new Uint8Array(sig)));
}

async function send(env, message) {
  const payload = {
    msg_type: "text",
    content: { text: message.text },
  };

  if (env.FEISHU_SIGN_SECRET) {
    const timestamp = Math.floor(Date.now() / 1000);
    payload.timestamp = String(timestamp);
    payload.sign = await genSign(env.FEISHU_SIGN_SECRET, timestamp);
  }

  const res = await fetch(env.FEISHU_WEBHOOK_URL, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });
  const body = await res.json().catch(() => ({}));
  // 飞书 HTTP 200 但 code != 0 也是失败 / HTTP 200 with code != 0 is still a failure.
  if (!res.ok || (typeof body.code === "number" && body.code !== 0)) {
    throw new Error(`Feishu error: ${JSON.stringify(body)}`);
  }
  return true;
}

export default { id: ID, isConfigured, send };
