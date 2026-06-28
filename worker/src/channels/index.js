/**
 * 渠道注册表 / Channel registry
 * ------------------------------------------------------------------
 * 新增渠道：在此处 import 并加入数组即可 / To add a channel, import & append here.
 * 每个渠道导出 { id, isConfigured(env), send(env, message, payload) }。
 */
import telegram from "./telegram.js";
import feishu from "./feishu.js";
import discord from "./discord.js";
import slack from "./slack.js";
import webhook from "./webhook.js";
import email from "./email.js";

export const channels = [telegram, feishu, discord, slack, webhook, email];
