/**
 * HedwigSMS 一键中转 / one-command relay launcher.
 * ------------------------------------------------------------------
 * 仿 BlueBubbles：启动本地中转 → 用 cloudflared「快速隧道」自动拿到公网 URL →
 * 自动生成 Token → 打开配对面板（含二维码）。无需 Cloudflare 账号。
 * BlueBubbles-style: start local relay → cloudflared quick tunnel auto public URL →
 * auto-generate token → open the pairing dashboard (with QR). No Cloudflare account.
 *
 * 用法 / usage:
 *   cd relay && cp .env.example .env  # 填好至少一个渠道 / fill in at least one channel
 *   node start.mjs
 *
 * 依赖 / requires: Node 18+ 和 cloudflared（macOS: brew install cloudflared）。
 */
import { spawn } from "node:child_process";
import crypto from "node:crypto";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { createRelay } from "./server.mjs";

const __dirname = path.dirname(fileURLToPath(import.meta.url));

// --- 极简 .env 加载（无第三方依赖）/ minimal .env loader (no deps) ---
function loadEnv() {
  const p = path.join(__dirname, ".env");
  if (!fs.existsSync(p)) return;
  for (const line of fs.readFileSync(p, "utf8").split("\n")) {
    const m = line.match(/^\s*([A-Z0-9_]+)\s*=\s*(.*)\s*$/);
    if (!m) continue;
    let v = m[2].trim();
    if ((v.startsWith('"') && v.endsWith('"')) || (v.startsWith("'") && v.endsWith("'"))) {
      v = v.slice(1, -1);
    }
    if (!(m[1] in process.env)) process.env[m[1]] = v;
  }
}
loadEnv();

const PORT = Number(process.env.PORT || 4040);

// --- Token：未设置则自动生成 / auto-generate token if absent ---
let token = process.env.AUTH_TOKEN;
let generated = false;
if (!token) { token = crypto.randomBytes(16).toString("hex"); generated = true; }

const relay = createRelay({ port: PORT, token, env: process.env });
await relay.listen();
console.log(`\n🦉 HedwigSMS Relay listening on http://localhost:${PORT}`);
if (generated) console.log(`🔑 Auto-generated AUTH_TOKEN: ${token}`);

// --- cloudflared 快速隧道 / quick tunnel ---
console.log("☁️  Starting Cloudflare quick tunnel (no account needed)…");
const cf = spawn("cloudflared", ["tunnel", "--url", `http://localhost:${PORT}`], {
  stdio: ["ignore", "pipe", "pipe"],
});

let announced = false;
function handle(buf) {
  const s = buf.toString();
  const m = s.match(/https:\/\/[a-z0-9-]+\.trycloudflare\.com/);
  if (m && !announced) {
    announced = true;
    relay.setPublicUrl(m[0]);
    printReady(m[0]);
  }
}
cf.stdout.on("data", handle);
cf.stderr.on("data", handle);
cf.on("error", (e) => {
  console.error("\n❌ Failed to launch cloudflared. Install it first:");
  console.error("   macOS:  brew install cloudflared");
  console.error("   docs:   https://developers.cloudflare.com/cloudflare-one/connections/connect-networks/downloads/");
  console.error("Detail:", e.message);
  console.log(`\n💡 你仍可仅用本地地址联调：http://localhost:${PORT}/pair`);
});

function printReady(publicUrl) {
  // 面板用本机地址打开（一定能解析），页面内显示的是公网 URL+二维码。
  // Open the dashboard via localhost (always resolvable); it still shows the public URL/QR.
  const localPair = `http://localhost:${PORT}/pair`;
  const pair = `${publicUrl}/pair`;
  console.log("\n────────────────────────────────────────────────");
  console.log("✅ 公网地址已就绪 / Public URL ready:");
  console.log(`   Server URL : ${publicUrl}`);
  console.log(`   Token      : ${token}`);
  console.log(`   配对面板    : ${localPair}   (公网: ${pair})`);
  console.log("────────────────────────────────────────────────");
  console.log("👉 在手机 HedwigSMS 点「📷 扫码导入配置」，扫描上面面板里的二维码即可。");
  console.log("👉 Open the pairing page, then scan the QR with the app.\n");
  console.log("⚠️  快速隧道是临时地址，重启会变；本电脑需保持开机联网。");
  console.log("⚠️  长期 7×24 建议改用云端 Worker（见根目录 README）。\n");
  // 自动打开浏览器（本机地址，避免新隧道域名 DNS 尚未生效）/ auto-open via localhost.
  const opener = process.platform === "darwin" ? "open"
    : process.platform === "win32" ? "start" : "xdg-open";
  spawn(opener, [localPair], { stdio: "ignore", detached: true, shell: process.platform === "win32" }).unref();
}

function shutdown() {
  console.log("\n👋 Shutting down…");
  try { cf.kill(); } catch { /* noop */ }
  relay.close();
  process.exit(0);
}
process.on("SIGINT", shutdown);
process.on("SIGTERM", shutdown);
