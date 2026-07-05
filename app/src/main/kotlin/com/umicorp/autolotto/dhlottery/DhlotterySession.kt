package com.umicorp.autolotto.dhlottery

import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.util.concurrent.TimeUnit

/**
 * dhlottery 세션 하나(쿠키 저장소 + OkHttpClient)를 소유하고 모든 서비스가 공유한다.
 * Flutter 구조(AuthService가 Dio 소유, Purchase/History가 차용)를 옮긴 것.
 *
 * baseUrl/olottoUrl 주입 가능 — 테스트는 MockWebServer URL을 주입한다.
 *
 * - followRedirects(false): 리다이렉트는 [followingRedirects]로 수동 추적(Dart _followRedirects와 동일).
 * - get/post **기본 follow=false** (Dart 전역 `followRedirects:false`와 일치). Dart가 `_followRedirects`를
 *   명시 호출한 지점만 호출부에서 `follow = true`로 부른다. (만료 302 자동추적으로 인한 세션 오염 방지)
 * - 인터셉터: 요청마다 기본 헤더(없을 때만) + Cookie 주입, 응답마다 Set-Cookie 저장.
 */
class DhlotterySession(
    val baseUrl: String = ApiConstants.DEFAULT_BASE,
    val olottoUrl: String = ApiConstants.DEFAULT_OLOTTO,
) {
    val cookies = DomainCookieStore()

    fun base(path: String): String = baseUrl + path
    fun ol(path: String): String = olottoUrl + path

    private val client: OkHttpClient = OkHttpClient.Builder()
        .followRedirects(false)
        .followSslRedirects(false)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val original = chain.request()
            val host = original.url.host
            val builder = original.newBuilder()
            for ((k, v) in ApiConstants.DEFAULT_HEADERS) {
                if (original.header(k) == null) builder.header(k, v)
            }
            val cookie = cookies.cookieHeader(host)
            if (cookie.isNotEmpty()) builder.header("Cookie", cookie)

            val response = chain.proceed(builder.build())
            val setCookies = response.headers("Set-Cookie")
            if (setCookies.isNotEmpty()) cookies.store(setCookies, host)
            response
        }
        .build()

    fun get(url: String, headers: Map<String, String> = emptyMap(), follow: Boolean = false): Response {
        val builder = Request.Builder().url(url).get()
        headers.forEach { (k, v) -> builder.header(k, v) }
        val resp = client.newCall(builder.build()).execute()
        return if (follow) followingRedirects(resp) else resp
    }

    /** 미리 만든 문자열 본문으로 POST (Dart가 문자열 data를 쓴 로그인용). */
    fun post(
        url: String,
        body: String,
        headers: Map<String, String> = emptyMap(),
        follow: Boolean = false,
    ): Response = execPost(url, body.toRequestBody(FORM_MEDIA), headers, follow)

    /** 필드 Map을 폼 인코딩해 POST (Dart가 Map data를 쓴 구매용 — 각 값을 URL-encode). */
    fun postForm(
        url: String,
        fields: Map<String, String>,
        headers: Map<String, String> = emptyMap(),
        follow: Boolean = false,
    ): Response {
        val fb = FormBody.Builder()
        fields.forEach { (k, v) -> fb.add(k, v) }
        return execPost(url, fb.build(), headers, follow)
    }

    private fun execPost(url: String, body: RequestBody, headers: Map<String, String>, follow: Boolean): Response {
        val builder = Request.Builder().url(url).post(body)
        headers.forEach { (k, v) -> builder.header(k, v) }
        val resp = client.newCall(builder.build()).execute()
        return if (follow) followingRedirects(resp) else resp
    }

    /** 301/302/303을 GET으로 최대 10회 추적(상대경로는 현재 URL 기준 resolve). */
    private fun followingRedirects(start: Response): Response {
        var resp = start
        var count = 0
        while (count < 10 && resp.code in REDIRECT_CODES) {
            val location = resp.header("Location") ?: break
            val next = resp.request.url.resolve(location) ?: break
            resp.close()
            resp = client.newCall(Request.Builder().url(next).get().build()).execute()
            count++
        }
        return resp
    }

    private companion object {
        val REDIRECT_CODES = setOf(301, 302, 303)
        val FORM_MEDIA = "application/x-www-form-urlencoded".toMediaType()
    }
}
