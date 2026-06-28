package com.hedwig.sms.net

import com.hedwig.sms.core.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * 通过 GitHub Releases 检查更新 / Update check via GitHub Releases.
 *
 * 调用 GitHub API 的 latest release，比对版本号；有新版返回下载/发布页地址。
 * Calls the GitHub "latest release" API, compares versions, and returns download/page URLs.
 */
object UpdateChecker {

    data class UpdateInfo(
        val version: String,
        val notes: String,
        val downloadUrl: String, // APK 资源直链或发布页 / APK asset or release page
        val pageUrl: String,
    )

    /** 结果：null 表示已是最新 / null means already up to date. */
    sealed class Result {
        data class Available(val info: UpdateInfo) : Result()
        data object UpToDate : Result()
        data class Failed(val message: String) : Result()
        data object NotConfigured : Result()
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun check(currentVersion: String): Result = withContext(Dispatchers.IO) {
        if (Constants.GITHUB_REPO.startsWith("REPLACE_ME")) return@withContext Result.NotConfigured
        try {
            val url = "https://api.github.com/repos/${Constants.GITHUB_REPO}/releases/latest"
            val req = Request.Builder().url(url)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "HedwigSMS")
                .build()
            client.newCall(req).execute().use { res ->
                if (!res.isSuccessful) return@withContext Result.Failed("HTTP ${res.code}")
                val obj = JSONObject(res.body?.string().orEmpty())
                val tag = obj.optString("tag_name").ifBlank { obj.optString("name") }
                val notes = obj.optString("body").take(600)
                val pageUrl = obj.optString("html_url")
                // 找 .apk 资源直链 / find an .apk asset
                var apk = ""
                val assets = obj.optJSONArray("assets")
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val a = assets.getJSONObject(i)
                        if (a.optString("name").endsWith(".apk", true)) {
                            apk = a.optString("browser_download_url"); break
                        }
                    }
                }
                if (tag.isBlank()) return@withContext Result.Failed("no release")
                if (isNewer(tag, currentVersion)) {
                    Result.Available(
                        UpdateInfo(
                            version = tag,
                            notes = notes,
                            downloadUrl = apk.ifBlank { pageUrl },
                            pageUrl = pageUrl,
                        ),
                    )
                } else {
                    Result.UpToDate
                }
            }
        } catch (e: Exception) {
            Result.Failed(e.message ?: "error")
        }
    }

    /** 语义化比较：tag 是否比 current 新 / is tag newer than current. */
    private fun isNewer(tag: String, current: String): Boolean {
        val a = parse(tag); val b = parse(current)
        val n = maxOf(a.size, b.size)
        for (i in 0 until n) {
            val x = a.getOrElse(i) { 0 }; val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    private fun parse(v: String): List<Int> =
        v.trim().removePrefix("v").removePrefix("V")
            .split(".", "-", "+")
            .mapNotNull { it.takeWhile { c -> c.isDigit() }.toIntOrNull() }
            .ifEmpty { listOf(0) }
}
