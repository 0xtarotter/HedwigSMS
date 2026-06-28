package com.hedwig.sms.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Telegram chat_id 自动发现 / auto-discover Telegram chat ids.
 *
 * 粘贴 Bot Token → 调 getUpdates 列出最近与机器人有过交互的会话，省去手动翻 JSON。
 * Paste a bot token → call getUpdates to list recent chats, so users don't read raw JSON.
 */
object TelegramHelper {

    data class Chat(val id: String, val title: String, val type: String)
    data class Discovery(val botUsername: String, val chats: List<Chat>)

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    suspend fun discover(botToken: String): kotlin.Result<Discovery> = withContext(Dispatchers.IO) {
        runCatching {
            val base = "https://api.telegram.org/bot${botToken.trim()}"
            val me = JSONObject(get("$base/getMe"))
            if (!me.optBoolean("ok")) error(me.optString("description", "invalid bot token"))
            val username = me.getJSONObject("result").optString("username")

            val upd = JSONObject(get("$base/getUpdates"))
            val seen = LinkedHashMap<String, Chat>()
            val arr = upd.optJSONArray("result")
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val u = arr.getJSONObject(i)
                    val chat = u.optJSONObject("message")?.optJSONObject("chat")
                        ?: u.optJSONObject("channel_post")?.optJSONObject("chat")
                        ?: u.optJSONObject("my_chat_member")?.optJSONObject("chat")
                        ?: continue
                    if (!chat.has("id")) continue
                    val id = chat.get("id").toString()
                    if (!seen.containsKey(id)) {
                        val title = chat.optString("title").ifBlank {
                            listOf(chat.optString("first_name"), chat.optString("last_name"))
                                .filter { it.isNotBlank() }.joinToString(" ")
                                .ifBlank { chat.optString("username").ifBlank { chat.optString("type") } }
                        }
                        seen[id] = Chat(id, title, chat.optString("type"))
                    }
                }
            }
            Discovery(username, seen.values.toList())
        }
    }

    private fun get(url: String): String {
        client.newCall(Request.Builder().url(url).get().build()).execute().use { res ->
            return res.body?.string().orEmpty()
        }
    }
}
