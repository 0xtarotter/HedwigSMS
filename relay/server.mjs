/**
 * HedwigSMS 本地中转 / Local relay server.
 * ------------------------------------------------------------------
 * 复用 Worker 的同一套「格式化 + 多渠道分发」逻辑，让你无需 Cloudflare 账号，
 * 在自己电脑上 30 秒跑起一个转发端（配合 cloudflared 快速隧道得到公网地址）。
 * Reuses the SAME format + multi-channel dispatch code as the Worker, so you can run a
 * forwarder on your own computer with no Cloudflare account (paired with a cloudflared
 * quick tunnel for a public URL).
 *
 * 端点 / endpoints:
 *   POST /            转发端点（与 Worker 完全一致的契约）/ forwarding endpoint
 *   GET  /pair        配对面板：显示 Server URL + 二维码（仿 BlueBubbles）/ pairing dashboard
 *   GET  /            健康检查 / health
 *
 * QR 面板思路参考 BlueBubbles (Apache-2.0, © BlueBubblesApp)。
 */
import http from "node:http";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { formatMessage } from "../worker/src/format.js";
import { channels } from "../worker/src/channels/index.js";
import smtp from "./smtp.mjs";

// relay 专属渠道（不进 Worker 共享表）/ relay-only channels (not in the shared Worker registry).
const relayChannels = [...channels, smtp];

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const VALID_TYPES = new Set([
  "sms", "battery_low", "power_connected", "power_disconnected", "heartbeat",
]);

function safeEqual(a, b) {
  if (typeof a !== "string" || typeof b !== "string") return false;
  if (a.length !== b.length) return false;
  let diff = 0;
  for (let i = 0; i < a.length; i++) diff |= a.charCodeAt(i) ^ b.charCodeAt(i);
  return diff === 0;
}

function readBody(req) {
  return new Promise((resolve) => {
    let data = "";
    req.on("data", (c) => (data += c));
    req.on("end", () => resolve(data));
  });
}

// 内联二维码库（MIT, davidshimjs）/ inline qrcode.js (MIT) for the pairing page.
function loadQrLib() {
  const candidates = [
    path.join(__dirname, "../tools/qrcode.min.js"),
    path.join(__dirname, "qrcode.min.js"),
  ];
  for (const p of candidates) {
    try { return fs.readFileSync(p, "utf8"); } catch { /* next */ }
  }
  return ""; // 缺失则面板退化为纯文本 / page degrades to text if missing
}

function pairPage(publicUrl, token, qrLib) {
  const cfg = `hedwig://config?u=${encodeURIComponent(publicUrl)}&t=${encodeURIComponent(token)}&d=`;
  return `<!DOCTYPE html><html lang="zh"><head><meta charset="utf-8"/>
<meta name="viewport" content="width=device-width,initial-scale=1"/>
<title>HedwigSMS · 配对面板 Pairing</title>
<style>
:root{color-scheme:dark}body{margin:0;font-family:-apple-system,"PingFang SC","Microsoft YaHei",system-ui,sans-serif;background:#0A0D12;color:#E7ECF2;display:flex;justify-content:center}
.wrap{width:100%;max-width:620px;padding:28px 20px 60px}h1{font-size:22px;margin:0 0 6px}
.row{display:flex;align-items:center;gap:10px;margin:10px 0;font-size:15px;flex-wrap:wrap}
.k{color:#9AA6B2;min-width:120px}.v{font-family:ui-monospace,Menlo,monospace;word-break:break-all}
.card{background:#121823;border:1px solid #243042;border-radius:16px;padding:20px;margin-top:18px}
.qrbox{margin:22px auto 0;width:264px;padding:12px;background:#fff;border-radius:16px}
#qr{display:flex;justify-content:center}.brand{color:#34D399;font-weight:700}
.warn{background:#2a1d10;border:1px solid #5a3a13;color:#f0c98a;padding:12px 14px;border-radius:12px;font-size:13px;margin-top:18px;line-height:1.6}
.hint{color:#9AA6B2;font-size:13px;text-align:center;margin-top:14px}
button{background:#1b2433;color:#E7ECF2;border:1px solid #243042;border-radius:10px;padding:6px 12px;cursor:pointer}
</style></head><body><div class="wrap">
<h1>🦉 HedwigSMS · <span class="brand">配对面板 Pairing</span></h1>
<div class="card">
  <div class="row"><span class="k">Server URL</span><span class="v" id="url">${publicUrl}</span>
    <button onclick="navigator.clipboard.writeText(document.getElementById('url').textContent)">复制 Copy</button></div>
  <div class="row"><span class="k">Token</span><span class="v" id="tok">${token}</span>
    <button onclick="navigator.clipboard.writeText(document.getElementById('tok').textContent)">复制 Copy</button></div>
  <div class="qrbox"><div id="qr"></div></div>
  <p class="hint">用手机 HedwigSMS 的「📷 扫码导入配置」扫描上方二维码即可一键配好。<br/>Scan with “Scan to import config” in the app.</p>
  <div class="row" style="margin-top:18px;justify-content:center">
    <a href="/setup" style="color:#34D399;font-weight:700;text-decoration:none">⚙️ 配置 IM 渠道 / Configure IM channels →</a>
  </div>
</div>
<div class="warn">🔒 二维码含访问令牌，请勿外传/截图分享。本地中转需保持本电脑开机联网。<br/>The QR contains your token — don’t share it. The relay requires this computer to stay on &amp; online.</div>
</div>
<script>${qrLib}</script>
<script>
new QRCode(document.getElementById('qr'),{text:${JSON.stringify(cfg)},width:240,height:240,correctLevel:QRCode.CorrectLevel.M});
</script></body></html>`;
}

// 渠道字段元数据（驱动配置向导 UI + 测试）/ channel field metadata (drives wizard UI + test).
const CHANNEL_FIELDS = {
  telegram: { name: "Telegram", fields: [
    { key: "TELEGRAM_BOT_TOKEN", label: "Bot Token", required: true },
    { key: "TELEGRAM_CHAT_ID", label: "Chat ID", required: true },
  ] },
  feishu: { name: "飞书 Feishu", fields: [
    { key: "FEISHU_WEBHOOK_URL", label: "Webhook URL", required: true },
    { key: "FEISHU_SIGN_SECRET", label: "签名密钥 Sign Secret（可选/optional）", required: false },
  ] },
  discord: { name: "Discord", fields: [
    { key: "DISCORD_WEBHOOK_URL", label: "Webhook URL", required: true },
  ] },
  slack: { name: "Slack", fields: [
    { key: "SLACK_WEBHOOK_URL", label: "Incoming Webhook URL", required: true },
  ] },
  webhook: { name: "通用 Webhook / Generic", fields: [
    { key: "CUSTOM_WEBHOOK_URL", label: "Webhook URL（Bark/Server酱/企业微信/钉钉…）", required: true },
  ] },
  email: { name: "Email (Resend)", fields: [
    { key: "RESEND_API_KEY", label: "Resend API Key", required: true },
    { key: "EMAIL_FROM", label: "发件 From", required: true },
    { key: "EMAIL_TO", label: "收件 To（逗号分隔/comma-separated）", required: true },
  ] },
  smtp: { name: "SMTP 邮件 / SMTP Email", fields: [
    { key: "SMTP_HOST", label: "SMTP 服务器 Host", required: true },
    { key: "SMTP_PORT", label: "端口 Port（留空自动/blank=auto）", required: false },
    { key: "SMTP_USER", label: "用户名 User", required: false },
    { key: "SMTP_PASS", label: "密码/授权码 Password", required: false },
    { key: "SMTP_FROM", label: "发件 From", required: true },
    { key: "SMTP_TO", label: "收件 To（逗号分隔）", required: true },
    { key: "SMTP_SECURITY", label: "加密 ssl/starttls/none", required: false },
  ] },
};

const ALL_FIELD_KEYS = Object.values(CHANNEL_FIELDS).flatMap((c) => c.fields.map((f) => f.key));

/** 写入 relay/.env（合并保留 AUTH_TOKEN/PORT）/ write .env, preserving AUTH_TOKEN/PORT. */
function writeEnvFile(dir, updates) {
  const p = path.join(dir, ".env");
  const all = {};
  if (process.env.AUTH_TOKEN) all.AUTH_TOKEN = process.env.AUTH_TOKEN;
  if (process.env.PORT) all.PORT = process.env.PORT;
  for (const k of ALL_FIELD_KEYS) {
    const v = (k in updates) ? updates[k] : process.env[k];
    if (v) all[k] = v;
  }
  const out = Object.entries(all)
    .map(([k, v]) => `${k}="${String(v).replace(/"/g, '\\"')}"`)
    .join("\n") + "\n";
  fs.writeFileSync(p, out);
}

/** 自动发现 Telegram chat_id / auto-discover Telegram chat ids via getUpdates. */
async function telegramDiscover(botToken) {
  const base = `https://api.telegram.org/bot${botToken}`;
  const meRes = await fetch(`${base}/getMe`);
  const me = await meRes.json();
  if (!me.ok) throw new Error(me.description || "invalid bot token");
  const updRes = await fetch(`${base}/getUpdates`);
  const upd = await updRes.json();
  const seen = new Map();
  for (const u of upd.result || []) {
    const chat = u.message?.chat || u.channel_post?.chat || u.my_chat_member?.chat;
    if (chat && !seen.has(chat.id)) {
      const title = chat.title || [chat.first_name, chat.last_name].filter(Boolean).join(" ") || chat.username || chat.type;
      seen.set(chat.id, { id: String(chat.id), title, type: chat.type });
    }
  }
  return { bot: me.result.username, chats: [...seen.values()] };
}

function setupPage() {
  const cur = {};
  for (const k of ALL_FIELD_KEYS) cur[k] = process.env[k] || "";
  return `<!DOCTYPE html><html lang="zh"><head><meta charset="utf-8"/>
<meta name="viewport" content="width=device-width,initial-scale=1"/>
<title>HedwigSMS · IM 配置向导 / Setup Wizard</title>
<style>
:root{color-scheme:dark}body{margin:0;font-family:-apple-system,"PingFang SC","Microsoft YaHei",system-ui,sans-serif;background:#0A0D12;color:#E7ECF2;display:flex;justify-content:center}
.wrap{width:100%;max-width:680px;padding:26px 18px 70px}h1{font-size:22px;margin:0 0 4px}
.sub{color:#9AA6B2;font-size:13px;margin-bottom:16px;line-height:1.6}
.card{background:#121823;border:1px solid #243042;border-radius:16px;padding:18px;margin-top:16px}
.ct{font-weight:700;font-size:16px;margin-bottom:10px}
label{display:block;font-size:12px;color:#9AA6B2;margin:10px 0 5px}
input{width:100%;box-sizing:border-box;padding:11px 12px;border-radius:10px;border:1px solid #243042;background:#0E141D;color:#E7ECF2;font-size:14px}
input:focus{outline:none;border-color:#34D399}
.btnrow{display:flex;gap:10px;margin-top:12px;flex-wrap:wrap}
button{background:#1b2433;color:#E7ECF2;border:1px solid #2c3a4f;border-radius:10px;padding:9px 14px;cursor:pointer;font-size:14px}
button.primary{background:#1f7a4d;border-color:#26935d}button.go{background:#243042}
.status{font-size:13px;margin-top:8px;min-height:18px}.ok{color:#34D399}.err{color:#ff7b7b}
.chips{display:flex;gap:8px;flex-wrap:wrap;margin-top:8px}
.chip{background:#0E141D;border:1px solid #2c3a4f;border-radius:8px;padding:6px 10px;cursor:pointer;font-size:13px}
.chip:hover{border-color:#34D399}.brand{color:#34D399}
.save{position:sticky;bottom:0;padding:14px 0}
a{color:#34D399}
</style></head><body><div class="wrap">
<h1>🦉 HedwigSMS · <span class="brand">IM 配置向导 Setup</span></h1>
<p class="sub">填好任意渠道 → 点「测试」当场验证 → 点「保存」即时生效（无需重启）。<br/>
Fill any channel → “Test” to verify instantly → “Save” applies live (no restart). · <a href="/pair">← 配对面板 Pairing</a></p>

<div class="card">
  <div class="ct">✈️ Telegram</div>
  <label>Bot Token（@BotFather 获取）</label>
  <input id="TELEGRAM_BOT_TOKEN" value="${escAttr(cur.TELEGRAM_BOT_TOKEN)}" placeholder="123456:ABC-..." />
  <div class="btnrow"><button class="go" onclick="tgDiscover()">🔎 自动获取 chat_id</button></div>
  <div id="tgchips" class="chips"></div>
  <label>Chat ID</label>
  <input id="TELEGRAM_CHAT_ID" value="${escAttr(cur.TELEGRAM_CHAT_ID)}" placeholder="点上方按钮自动获取，或填你的数字 id" />
  <div class="btnrow"><button class="primary" onclick="test('telegram',['TELEGRAM_BOT_TOKEN','TELEGRAM_CHAT_ID'])">📨 测试发送</button></div>
  <div class="status" id="st_telegram"></div>
  <div class="sub" style="margin-top:8px">提示：先给你的 bot 发一句话，再点「自动获取 chat_id」。</div>
</div>

<div class="card">
  <div class="ct">🐦 飞书 Feishu</div>
  <label>Webhook URL（群 → 设置 → 群机器人 → 自定义机器人）</label>
  <input id="FEISHU_WEBHOOK_URL" value="${escAttr(cur.FEISHU_WEBHOOK_URL)}" placeholder="https://open.feishu.cn/open-apis/bot/v2/hook/..." />
  <label>签名密钥 Sign Secret（可选）</label>
  <input id="FEISHU_SIGN_SECRET" value="${escAttr(cur.FEISHU_SIGN_SECRET)}" placeholder="开启签名校验时填" />
  <div class="btnrow"><button class="primary" onclick="test('feishu',['FEISHU_WEBHOOK_URL','FEISHU_SIGN_SECRET'])">📨 测试发送</button></div>
  <div class="status" id="st_feishu"></div>
</div>

<div class="card">
  <div class="ct">🎮 Discord</div>
  <label>Webhook URL（频道 → 整合 → Webhook）</label>
  <input id="DISCORD_WEBHOOK_URL" value="${escAttr(cur.DISCORD_WEBHOOK_URL)}" placeholder="https://discord.com/api/webhooks/..." />
  <div class="btnrow"><button class="primary" onclick="test('discord',['DISCORD_WEBHOOK_URL'])">📨 测试发送</button></div>
  <div class="status" id="st_discord"></div>
</div>

<div class="card">
  <div class="ct">💬 Slack</div>
  <label>Incoming Webhook URL</label>
  <input id="SLACK_WEBHOOK_URL" value="${escAttr(cur.SLACK_WEBHOOK_URL)}" placeholder="https://hooks.slack.com/services/..." />
  <div class="btnrow"><button class="primary" onclick="test('slack',['SLACK_WEBHOOK_URL'])">📨 测试发送</button></div>
  <div class="status" id="st_slack"></div>
</div>

<div class="card">
  <div class="ct">🔗 通用 Webhook / Generic（Bark · Server酱 · 企业微信 · 钉钉 · n8n…）</div>
  <label>Webhook URL</label>
  <input id="CUSTOM_WEBHOOK_URL" value="${escAttr(cur.CUSTOM_WEBHOOK_URL)}" placeholder="https://..." />
  <div class="btnrow"><button class="primary" onclick="test('webhook',['CUSTOM_WEBHOOK_URL'])">📨 测试发送</button></div>
  <div class="status" id="st_webhook"></div>
</div>

<div class="card">
  <div class="ct">✉️ Email (Resend)</div>
  <label>Resend API Key</label>
  <input id="RESEND_API_KEY" value="${escAttr(cur.RESEND_API_KEY)}" placeholder="re_..." />
  <label>发件 From（已在 Resend 验证的域名地址）</label>
  <input id="EMAIL_FROM" value="${escAttr(cur.EMAIL_FROM)}" placeholder="hedwig@yourdomain.com" />
  <label>收件 To</label>
  <input id="EMAIL_TO" value="${escAttr(cur.EMAIL_TO)}" placeholder="you@example.com" />
  <div class="btnrow"><button class="primary" onclick="test('email',['RESEND_API_KEY','EMAIL_FROM','EMAIL_TO'])">📨 测试发送</button></div>
  <div class="status" id="st_email"></div>
</div>

<div class="card">
  <div class="ct">📮 SMTP 邮件 / SMTP Email</div>
  <label>SMTP 服务器 Host（如 smtp.qq.com）</label>
  <input id="SMTP_HOST" value="${escAttr(cur.SMTP_HOST)}" placeholder="smtp.qq.com" />
  <label>端口 Port（留空按加密方式自动 465/587/25）</label>
  <input id="SMTP_PORT" value="${escAttr(cur.SMTP_PORT)}" placeholder="465" />
  <label>用户名 User（通常是邮箱）</label>
  <input id="SMTP_USER" value="${escAttr(cur.SMTP_USER)}" placeholder="you@qq.com" />
  <label>密码 / 授权码 Password</label>
  <input id="SMTP_PASS" type="password" value="${escAttr(cur.SMTP_PASS)}" placeholder="授权码" />
  <label>发件 From</label>
  <input id="SMTP_FROM" value="${escAttr(cur.SMTP_FROM)}" placeholder="you@qq.com" />
  <label>收件 To（逗号分隔）</label>
  <input id="SMTP_TO" value="${escAttr(cur.SMTP_TO)}" placeholder="you@example.com" />
  <label>加密方式 Security（ssl / starttls / none）</label>
  <input id="SMTP_SECURITY" value="${escAttr(cur.SMTP_SECURITY) || 'ssl'}" placeholder="ssl" />
  <div class="btnrow"><button class="primary" onclick="test('smtp',['SMTP_HOST','SMTP_PORT','SMTP_USER','SMTP_PASS','SMTP_FROM','SMTP_TO','SMTP_SECURITY'])">📨 测试发送</button></div>
  <div class="status" id="st_smtp"></div>
  <div class="sub" style="margin-top:8px">提示：需要先在本机执行 <code>npm install</code> 安装 nodemailer。</div>
</div>

<div class="save"><button class="primary" style="width:100%;padding:13px" onclick="saveAll()">💾 保存全部配置 / Save all</button>
<div class="status" id="st_save"></div></div>
</div>
<script>
const KEYS=${JSON.stringify(ALL_FIELD_KEYS)};
function val(id){return document.getElementById(id).value.trim();}
function set(id){let o={};return o;}
function show(el,ok,msg){var e=document.getElementById(el);e.className='status '+(ok?'ok':'err');e.textContent=msg;}
function gather(keys){var o={};keys.forEach(k=>o[k]=val(k));return o;}
async function api(path,body){const r=await fetch(path,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(body)});return r.json();}
async function tgDiscover(){
  var t=val('TELEGRAM_BOT_TOKEN'); if(!t){show('st_telegram',false,'请先填 Bot Token');return;}
  show('st_telegram',true,'查询中… / querying…');
  try{var d=await api('/api/telegram/chatid',{botToken:t});
    if(!d.ok){show('st_telegram',false,'失败：'+d.error);return;}
    var box=document.getElementById('tgchips');box.innerHTML='';
    if(!d.chats.length){show('st_telegram',false,'未发现会话。请先给 @'+d.bot+' 发一句话再点此。');return;}
    d.chats.forEach(c=>{var b=document.createElement('div');b.className='chip';b.textContent=c.title+' ('+c.id+')';
      b.onclick=()=>{document.getElementById('TELEGRAM_CHAT_ID').value=c.id;show('st_telegram',true,'已填入 chat_id: '+c.id);};box.appendChild(b);});
    show('st_telegram',true,'@'+d.bot+' · 发现 '+d.chats.length+' 个会话，点下面选一个 👇');
  }catch(e){show('st_telegram',false,'错误：'+e.message);}
}
async function test(channel,keys){
  show('st_'+channel,true,'发送中… / sending…');
  try{var d=await api('/api/test',{channel:channel,fields:gather(keys)});
    show('st_'+channel,d.ok,d.ok?'✅ 测试消息已发送，请到对应 App 查收':'❌ '+d.error);
  }catch(e){show('st_'+channel,false,'错误：'+e.message);}
}
async function saveAll(){
  show('st_save',true,'保存中…');
  try{var d=await api('/api/save',{fields:gather(KEYS)});
    show('st_save',d.ok,d.ok?'✅ 已保存并即时生效':'❌ '+d.error);
  }catch(e){show('st_save',false,'错误：'+e.message);}
}
</script></body></html>`;
}

function escAttr(s) {
  return String(s || "").replace(/&/g, "&amp;").replace(/"/g, "&quot;").replace(/</g, "&lt;");
}

/**
 * 创建中转服务 / create the relay.
 * @param {{port:number, token:string, env:object}} opts
 */
export function createRelay({ port, token, env }) {
  let publicUrl = `http://localhost:${port}`;
  const qrLib = loadQrLib();

  const server = http.createServer(async (req, res) => {
    const json = (obj, code = 200) => {
      res.writeHead(code, { "Content-Type": "application/json; charset=utf-8" });
      res.end(JSON.stringify(obj, null, 2));
    };
    const url = new URL(req.url, `http://localhost:${port}`);

    // 配置/向导端点仅限本机访问（隧道请求带 cf-* 头会被拒）。
    // Config/wizard endpoints are local-only; tunnel requests carry cf-* headers and are blocked.
    const viaTunnel = Boolean(
      req.headers["cf-connecting-ip"] || req.headers["cf-ray"] || req.headers["x-forwarded-for"],
    );
    const isConfigPath = url.pathname === "/setup" || url.pathname.startsWith("/api/");
    if (isConfigPath && viaTunnel) {
      return json({ ok: false, error: "Setup is local-only. Open it on the computer running the relay." }, 403);
    }

    // --- 配置向导页 / setup wizard page ---
    if (req.method === "GET" && url.pathname === "/setup") {
      res.writeHead(200, { "Content-Type": "text/html; charset=utf-8" });
      res.end(setupPage());
      return;
    }

    // --- 向导 API（本机）/ wizard APIs (local) ---
    if (req.method === "POST" && url.pathname === "/api/telegram/chatid") {
      try {
        const { botToken } = JSON.parse(await readBody(req));
        return json({ ok: true, ...(await telegramDiscover(botToken)) });
      } catch (e) {
        return json({ ok: false, error: String(e.message || e) }, 200);
      }
    }
    if (req.method === "POST" && url.pathname === "/api/test") {
      try {
        const { channel, fields } = JSON.parse(await readBody(req));
        const ch = relayChannels.find((c) => c.id === channel);
        if (!ch) return json({ ok: false, error: "unknown channel" });
        if (!ch.isConfigured(fields || {})) return json({ ok: false, error: "请填完必填项 / missing required fields" });
        const payload = {
          type: "heartbeat",
          device_name: "配置向导 / Setup Wizard",
          timestamp: Date.now(),
          data: { battery_level: 100 },
        };
        await ch.send(fields, formatMessage(payload), payload);
        return json({ ok: true });
      } catch (e) {
        return json({ ok: false, error: String(e.message || e) }, 200);
      }
    }
    if (req.method === "POST" && url.pathname === "/api/save") {
      try {
        const { fields } = JSON.parse(await readBody(req));
        for (const [k, v] of Object.entries(fields || {})) process.env[k] = v; // 即时生效 / live
        writeEnvFile(__dirname, fields || {});
        return json({ ok: true });
      } catch (e) {
        return json({ ok: false, error: String(e.message || e) }, 200);
      }
    }

    // --- 配对面板 / pairing dashboard ---
    if (req.method === "GET" && url.pathname === "/pair") {
      res.writeHead(200, { "Content-Type": "text/html; charset=utf-8" });
      res.end(pairPage(publicUrl, token, qrLib));
      return;
    }
    if (req.method === "GET") {
      return json({ ok: true, service: "HedwigSMS Relay", pair: `${publicUrl}/pair` });
    }
    if (req.method !== "POST") return json({ ok: false, error: "Method Not Allowed" }, 405);

    // --- 鉴权 / auth ---
    if (!safeEqual(url.searchParams.get("token") || "", token)) {
      return json({ ok: false, error: "Unauthorized: bad token." }, 401);
    }

    // --- 解析 / parse ---
    let payload;
    try { payload = JSON.parse(await readBody(req)); }
    catch { return json({ ok: false, error: "Bad Request: invalid JSON." }, 400); }
    if (!payload || typeof payload !== "object" || !VALID_TYPES.has(payload.type)) {
      return json({ ok: false, error: `Bad Request: unknown type "${payload?.type}".` }, 400);
    }

    // --- 格式化 + 分发 / format + dispatch ---
    const message = formatMessage(payload);
    const active = relayChannels.filter((c) => c.isConfigured(env));
    if (active.length === 0) {
      return json({ ok: false, error: "No channel configured. Set channel env vars in relay/.env." }, 500);
    }
    const settled = await Promise.allSettled(active.map((c) => c.send(env, message, payload)));
    const results = active.map((c, i) => ({
      channel: c.id,
      ok: settled[i].status === "fulfilled",
      error: settled[i].status === "rejected"
        ? String(settled[i].reason?.message || settled[i].reason) : undefined,
    }));
    const anyOk = results.some((r) => r.ok);
    return json({ ok: anyOk, type: payload.type, results }, anyOk ? 200 : 502);
  });

  return {
    listen: () => new Promise((r) => server.listen(port, r)),
    setPublicUrl: (u) => { if (u) publicUrl = u; },
    getPublicUrl: () => publicUrl,
    close: () => server.close(),
  };
}
