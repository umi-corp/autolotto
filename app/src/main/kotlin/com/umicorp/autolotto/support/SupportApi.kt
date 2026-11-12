package com.umicorp.autolotto.support

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/** 문의 게시판 상태 API(company 스펙 #5). 키는 헤더로만 보내고 로그에 남기지 않는다. */
class SupportApi(
    private val baseUrl: String = "https://$SUPPORT_HOST",
    private val client: OkHttpClient = OkHttpClient(),
) {

    /** 실패(네트워크·404·5xx·형식 오류)는 null — 호출자는 아무것도 바꾸지 않는다. */
    suspend fun status(key: String): SupportStatus? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url("$baseUrl/api/$SUPPORT_SERVICE/status")
                .header("X-Support-Key", key)
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val json = JSONObject(resp.body?.string() ?: return@use null)
                SupportStatus(unread = json.getBoolean("unread"), waiting = json.getBoolean("waiting"))
            }
        } catch (_: Exception) {
            null
        }
    }
}
