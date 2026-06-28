package com.hedwig.sms.net

import com.hedwig.sms.model.HedwigPayload
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * 网络客户端 / Network client.
 *
 * 用 OkHttp 异步把统一负载 POST 到用户的 Cloudflare Worker。
 * Asynchronously POSTs the unified payload to the user's Cloudflare Worker via OkHttp.
 */
object HedwigApiClient {

    private val JSON = "application/json; charset=utf-8".toMediaType()

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    /** 转发结果 / forwarding result. */
    data class Result(val success: Boolean, val statusCode: Int, val message: String)

    /**
     * 异步发送 / send asynchronously.
     * @param requestUrl 已含 ?token= 的完整地址 / full URL including ?token=
     */
    fun send(
        requestUrl: String,
        payload: HedwigPayload,
        onResult: (Result) -> Unit,
    ) {
        val body = payload.toJson().toRequestBody(JSON)
        val request = Request.Builder()
            .url(requestUrl)
            .post(body)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                onResult(Result(false, 0, e.message ?: "network error"))
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val ok = it.isSuccessful
                    onResult(Result(ok, it.code, if (ok) "OK" else "HTTP ${it.code}"))
                }
            }
        })
    }
}
