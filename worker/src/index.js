/**
 * HedwigSMS Cloudflare Worker — 工程化拆分版入口 / modular entry
 * ==================================================================
 * 这是供 `wrangler deploy` 使用的工程化版本（多文件）。
 * This is the modular version for `wrangler deploy` (multi-file).
 *
 * 如果你想「复制粘贴到 Cloudflare Dashboard」一键部署，
 * 请使用仓库根目录的 worker/index.single.js（自包含单文件版）。
 * For copy-paste-into-Dashboard deployment, use worker/index.single.js
 * (a self-contained single-file build).
 *
 * Message dispatch payload shapes adapted from OpenClaw (MIT).
 * Copyright (c) 2026 OpenClaw Foundation.
 */
import { formatMessage } from "./format.js";
import { channels } from "./channels/index.js";

const VALID_TYPES = new Set([
  "sms",
  "battery_low",
  "power_connected",
  "power_disconnected",
  "heartbeat",
  "service_on",
  "service_off",
]);

/** 定长安全比较，防时序攻击 / constant-time comparison to avoid timing attacks. */
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

export default {
  async fetch(request, env) {
    // ---- 0. 健康检查 / health check (GET) ----
    if (request.method === "GET") {
      return json({ ok: true, service: "HedwigSMS Worker", hint: "POST your payload here." });
    }
    if (request.method !== "POST") {
      return json({ ok: false, error: "Method Not Allowed" }, 405);
    }

    // ---- 1. Token 鉴权 / auth ----
    const url = new URL(request.url);
    const token = url.searchParams.get("token");
    if (!env.AUTH_TOKEN) {
      return json({ ok: false, error: "Server misconfigured: AUTH_TOKEN not set." }, 500);
    }
    if (!safeEqual(token || "", env.AUTH_TOKEN)) {
      return json({ ok: false, error: "Unauthorized: bad token." }, 401);
    }

    // ---- 2. 解析与校验负载 / parse & validate payload ----
    let payload;
    try {
      payload = await request.json();
    } catch {
      return json({ ok: false, error: "Bad Request: invalid JSON." }, 400);
    }
    if (!payload || typeof payload !== "object" || !VALID_TYPES.has(payload.type)) {
      return json({ ok: false, error: `Bad Request: unknown type "${payload?.type}".` }, 400);
    }

    // ---- 3. 格式化 / format ----
    const message = formatMessage(payload);

    // ---- 4. 多路并行分发 / fan-out dispatch ----
    const active = channels.filter((c) => c.isConfigured(env));
    if (active.length === 0) {
      return json(
        { ok: false, error: "No channel configured. Set at least one channel's env vars." },
        500,
      );
    }

    const settled = await Promise.allSettled(
      active.map((c) => c.send(env, message, payload)),
    );
    const results = active.map((c, i) => ({
      channel: c.id,
      ok: settled[i].status === "fulfilled",
      error: settled[i].status === "rejected" ? String(settled[i].reason?.message || settled[i].reason) : undefined,
    }));

    const anyOk = results.some((r) => r.ok);
    return json({ ok: anyOk, type: payload.type, results }, anyOk ? 200 : 502);
  },
};
