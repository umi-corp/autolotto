package com.umicorp.autolotto.dhlottery

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URLEncoder

class DhlotteryException(message: String) : Exception(message)

/**
 * 동행복권 로그인/세션 서비스 (Flutter AuthService 포트).
 *
 * 로그인 흐름: 메인 → 로그인 페이지 → RSA 공개키 → id/pw RSA 암호화 → 로그인 POST →
 * game645 방문으로 ol 세션 발급 → mypage 호출로 검증. 검증 실패 시 INVALID_CREDENTIALS.
 *
 * 리다이렉트 추적은 Dart가 `_followRedirects`를 명시 호출한 지점만 `follow=true`로 맞춘다(1:1).
 */
class AuthService(private val session: DhlotterySession) {

    @Volatile
    var isLoggedIn: Boolean = false
        private set

    suspend fun login(userId: String, password: String): Boolean = withContext(Dispatchers.IO) {
        // 비파괴 로그인(crosscheck): 재로그인 실패가 직전까지 유효하던 세션을 파괴하지 않게
        // 진입 상태를 떠 두고 실패 시 복원한다. 동시성 직렬화는 호출자(AppContainer.loginMutex) 책임.
        val prevCookies = session.cookies.snapshot()
        val prevLoggedIn = isLoggedIn
        try {
            session.cookies.clear()
            isLoggedIn = false

            // 1. 메인 / 2. 로그인 페이지 (Dart: 리다이렉트 추적)
            session.get(session.baseUrl, follow = true).close()
            session.get(session.base(ApiConstants.LOGIN_PAGE), follow = true).close()

            // 3. RSA 공개키 (Dart: 추적 안 함)
            val rsaBody = session.get(
                session.base(ApiConstants.RSA_MODULUS),
                mapOf(
                    "Accept" to "application/json",
                    "X-Requested-With" to "XMLHttpRequest",
                    "Referer" to session.base(ApiConstants.LOGIN_PAGE),
                ),
            ).use { it.body?.string().orEmpty() }

            val data = runCatching { JSONObject(rsaBody).getJSONObject("data") }.getOrNull()
                ?: throw DhlotteryException("RSA 키를 가져올 수 없습니다.")
            val modulus = data.getString("rsaModulus")
            val exponent = data.getString("publicExponent")
            val encId = RsaCrypto.encrypt(userId, modulus, exponent)
            val encPw = RsaCrypto.encrypt(password, modulus, exponent)

            // 4. 로그인 POST (Dart: 추적). 본문은 Dart처럼 미리 만든 문자열.
            val form = "userId=$encId&userPswdEncn=$encPw&inpUserId=${URLEncoder.encode(userId, "UTF-8")}"
            session.post(
                session.base(ApiConstants.LOGIN),
                form,
                mapOf(
                    "Origin" to session.baseUrl,
                    "Referer" to session.base(ApiConstants.LOGIN_PAGE),
                ),
                follow = true,
            ).close()

            // JSESSIONID 없으면 메인 재방문으로 유도 (Dart: 추적)
            if (!session.cookies.hasCookie("JSESSIONID")) {
                session.get(session.base(ApiConstants.MAIN), follow = true).close()
            }

            // 5. game645 방문으로 ol 세션 발급 (Dart: 첫 main은 추적 안 함, game645는 추적)
            session.get(session.base(ApiConstants.MAIN)).close()
            session.get(session.ol(ApiConstants.GAME645), follow = true).close()

            // 6. 검증 (mypage 200이면 성공). 네트워크 예외는 전파 = 일시 장애 —
            // 자격증명 거절(INVALID_CREDENTIALS)과 구분해야 재시도 정책이 역전되지 않는다(crosscheck R2).
            val verified = verifyLogin()
            isLoggedIn = verified
            if (!verified) throw DhlotteryException("INVALID_CREDENTIALS")
            true
        } catch (e: Exception) {
            session.cookies.restore(prevCookies)
            isLoggedIn = prevLoggedIn
            throw e
        }
    }

    /** mypage 200 여부로 검증. 리다이렉트 추적 안 함(302 ≠ 성공). IO 예외·5xx는 전파(일시 장애). */
    private fun verifyLogin(): Boolean =
        session.get(
            session.base(ApiConstants.BALANCE),
            mapOf("X-Requested-With" to "XMLHttpRequest"),
            follow = false,
        ).use { resp ->
            // 5xx = 서버 장애(추첨 직후 피크 등) — 자격증명 거절로 오판하면 재시도가 봉쇄된다.
            if (resp.code >= 500) throw java.io.IOException("verify_http_${resp.code}")
            resp.code == 200
        }

    /** 예치금 잔액. 로그인 상태에서만. 만료(HTML)/실패 시 0. (Dart처럼 리다이렉트 추적 안 함) */
    suspend fun getBalance(): Int = withContext(Dispatchers.IO) {
        if (!isLoggedIn) return@withContext 0
        try {
            val body = session.get(
                session.base(ApiConstants.BALANCE),
                mapOf(
                    "X-Requested-With" to "XMLHttpRequest",
                    "Referer" to session.base("/mypage/home"),
                ),
            ).use { it.body?.string().orEmpty() }
            // HTML = 세션 만료 센티넬 (Dart: data.startsWith('{') 가드와 동일)
            if (!body.trimStart().startsWith("{")) return@withContext 0
            JSONObject(body).optJSONObject("data")
                ?.optJSONObject("userMndp")
                ?.optInt("crntEntrsAmt", 0) ?: 0
        } catch (e: Exception) {
            0
        }
    }

    fun logout() {
        session.cookies.clear()
        isLoggedIn = false
    }
}
