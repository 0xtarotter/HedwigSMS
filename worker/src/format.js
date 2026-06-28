/**
 * HedwigSMS - 消息格式化 / Message formatting
 * ------------------------------------------------------------------
 * 将来自 Android 端的统一 JSON 负载转换为排版优雅、带 Emoji 的中文文本。
 * Convert the unified JSON payload from the Android client into a
 * nicely-formatted, emoji-rich (Chinese) text message.
 *
 * 统一负载格式 / Unified payload shape:
 * {
 *   "type": "sms" | "battery_low" | "power_connected" | "power_disconnected" | "heartbeat",
 *   "device_name": "String",
 *   "timestamp": Long (毫秒 / milliseconds),
 *   "data": { "sender"?, "body"?, "battery_level"? }
 * }
 */

/**
 * 将毫秒时间戳格式化为东八区（北京时间）可读字符串。
 * Format a millisecond timestamp into a human-readable Beijing-time string.
 * Cloudflare Workers 默认 UTC，这里手动 +8 小时（备用机多在国内）。
 */
function formatTime(ts) {
  const ms = typeof ts === "number" && ts > 0 ? ts : Date.now();
  const d = new Date(ms + 8 * 60 * 60 * 1000); // UTC -> UTC+8
  const pad = (n) => String(n).padStart(2, "0");
  return (
    `${d.getUTCFullYear()}-${pad(d.getUTCMonth() + 1)}-${pad(d.getUTCDate())} ` +
    `${pad(d.getUTCHours())}:${pad(d.getUTCMinutes())}:${pad(d.getUTCSeconds())}`
  );
}

/**
 * 根据 payload 生成「纯文本」消息正文（适用于飞书 / Slack / Discord / 自定义 Webhook / Email）。
 * Build a plain-text message body. Telegram 单独支持 HTML（见 channels/telegram.js）。
 *
 * @param {object} payload 已校验的统一负载 / validated unified payload
 * @returns {{ title: string, text: string }}
 */
export function formatMessage(payload) {
  const device = payload.device_name || "未知设备 / Unknown device";
  const time = formatTime(payload.timestamp);
  const data = payload.data || {};

  switch (payload.type) {
    case "sms": {
      const sender = data.sender || "未知号码 / Unknown";
      const body = data.body || "(空内容 / empty)";
      const simLine = data.sim ? `📲 接收卡 SIM：${data.sim}\n` : "";
      const title = `📩 新短信 New SMS`;
      const text =
        `📩 新短信 | New SMS\n` +
        `———————————————\n` +
        `📱 设备 Device：${device}\n` +
        simLine +
        `👤 发件人 From：${sender}\n` +
        `🕒 时间 Time：${time}\n` +
        `———————————————\n` +
        `${body}`;
      return { title, text };
    }

    case "battery_low": {
      const level = data.battery_level ?? "?";
      const title = `🪫 低电量预警 Low Battery`;
      const text =
        `🪫 低电量预警 | Low Battery Warning\n` +
        `———————————————\n` +
        `📱 设备 Device：${device}\n` +
        `🔋 当前电量 Level：${level}%\n` +
        `🕒 时间 Time：${time}\n` +
        `———————————————\n` +
        `⚠️ 请尽快为备用机充电 / Please charge the backup phone soon.`;
      return { title, text };
    }

    case "power_disconnected": {
      const level = data.battery_level ?? "?";
      const title = `⚡️ 电源已断开 Power Lost`;
      const text =
        `⚡️ 电源已断开（疑似停电！）| Power Disconnected (possible outage!)\n` +
        `———————————————\n` +
        `📱 设备 Device：${device}\n` +
        `🔋 当前电量 Level：${level}%\n` +
        `🕒 时间 Time：${time}\n` +
        `———————————————\n` +
        `🏠 请留意家中电力情况 / Please check the power at home.`;
      return { title, text };
    }

    case "power_connected": {
      const level = data.battery_level ?? "?";
      const title = `🔌 电源已接通 Power Restored`;
      const text =
        `🔌 电源已接通 | Power Connected\n` +
        `———————————————\n` +
        `📱 设备 Device：${device}\n` +
        `🔋 当前电量 Level：${level}%\n` +
        `🕒 时间 Time：${time}\n` +
        `———————————————\n` +
        `✅ 充电中，一切正常 / Charging, all good.`;
      return { title, text };
    }

    case "heartbeat": {
      const level = data.battery_level ?? "?";
      const title = `💚 设备平安报告 Daily Report`;
      const text =
        `💚 设备平安报告 | Device Health Report\n` +
        `———————————————\n` +
        `📱 设备 Device：${device}\n` +
        `🔋 当前电量 Level：${level}%\n` +
        `🌐 网络状态 Network：正常 OK\n` +
        `🕒 时间 Time：${time}\n` +
        `———————————————\n` +
        `🛡️ Hedwig 正在守护中 / Hedwig is on guard.`;
      return { title, text };
    }

    case "service_on": {
      const level = data.battery_level ?? "?";
      const title = `✅ 守护已开启 Guard Started`;
      const text =
        `✅ 守护已开启 | Guard Started\n———————————————\n` +
        `📱 设备 Device：${device}\n🔋 当前电量 Level：${level}%\n🕒 时间 Time：${time}\n———————————————\n` +
        `🛡️ Hedwig 开始守护，短信将实时转发。`;
      return { title, text };
    }

    case "service_off": {
      const level = data.battery_level ?? "?";
      const title = `🛑 守护已停止 Guard Stopped`;
      const text =
        `🛑 守护已停止 | Guard Stopped\n———————————————\n` +
        `📱 设备 Device：${device}\n🔋 当前电量 Level：${level}%\n🕒 时间 Time：${time}\n———————————————\n` +
        `⚠️ 短信将不再转发，请留意！`;
      return { title, text };
    }

    default: {
      // 未知类型也兜底转发，方便调试 / Fallback for unknown types (debug-friendly).
      const title = `📨 Hedwig 通知 Notification`;
      const text =
        `📨 Hedwig 通知 | Notification\n` +
        `📱 设备 Device：${device}\n` +
        `🏷️ 类型 Type：${payload.type}\n` +
        `🕒 时间 Time：${time}\n` +
        `📦 数据 Data：${JSON.stringify(data)}`;
      return { title, text };
    }
  }
}
