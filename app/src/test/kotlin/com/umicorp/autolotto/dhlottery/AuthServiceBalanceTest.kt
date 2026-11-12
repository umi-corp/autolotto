package com.umicorp.autolotto.dhlottery

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPublicKey

/**
 * 잔액 조회 실패는 0이 아니라 null이어야 한다.
 *
 * 0으로 위장하면 BalanceAlert가 "💰 잔액 부족 — 예치금 잔액이 0원입니다"를 허위 발송하고,
 * 같은 날 중복방지 잠금까지 걸려 진짜 잔액 부족 알림이 막힌다(사용자 리포트 계열 버그).
 */
class AuthServiceBalanceTest {

    private lateinit var server: MockWebServer
    private lateinit var modulusHex: String
    private lateinit var exponentHex: String

    /** 로그인 완료 후 BALANCE 응답을 갈아끼우기 위한 스위치. */
    private var balanceAfterLogin: MockResponse? = null

    @Before
    fun setUp() {
        val pub = KeyPairGenerator.getInstance("RSA")
            .apply { initialize(2048) }.generateKeyPair().public as RSAPublicKey
        modulusHex = pub.modulus.toString(16)
        exponentHex = pub.publicExponent.toString(16)
        server = MockWebServer()
        server.start()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                when (request.path?.substringBefore('?')) {
                    ApiConstants.RSA_MODULUS ->
                        ok("""{"data":{"rsaModulus":"$modulusHex","publicExponent":"$exponentHex"}}""")
                    // 로그인 검증(verifyLogin)도 이 경로를 쓴다 — 로그인 전엔 항상 정상 200을 준다.
                    ApiConstants.BALANCE -> balanceAfterLogin ?: ok(BALANCE_JSON)
                    else -> ok("")
                }
        }
    }

    @After
    fun tearDown() = server.shutdown()

    private fun ok(body: String) = MockResponse().setResponseCode(200).setBody(body)

    private fun newAuth(): AuthService {
        val baseUrl = server.url("/").toString().trimEnd('/')
        return AuthService(DhlotterySession(baseUrl = baseUrl, olottoUrl = baseUrl))
    }

    /** 로그인을 정상 응답으로 통과시킨 뒤 이후 BALANCE 응답만 [after]로 바꾼다. */
    private suspend fun loginThen(after: MockResponse?): AuthService {
        val auth = newAuth()
        auth.login("id", "pw")
        balanceAfterLogin = after
        return auth
    }

    @Test
    fun `balance is null when server is unreachable (5xx)`() = runBlocking {
        val auth = loginThen(MockResponse().setResponseCode(503).setBody("<html>maintenance</html>"))
        assertNull(auth.getBalance())
    }

    @Test
    fun `balance is null when session expired (HTML instead of JSON)`() = runBlocking {
        val auth = loginThen(ok("<!DOCTYPE html><html>login</html>"))
        assertNull(auth.getBalance())
    }

    @Test
    fun `balance parses amount on normal response`() = runBlocking {
        val auth = loginThen(null)
        assertEquals(12345, auth.getBalance())
    }

    @Test
    fun `balance is null before login (never 0)`() = runBlocking {
        assertNull(newAuth().getBalance())
    }

    /** 문법만 유효한 오류 응답이 0으로 둔갑하면 허위 "잔액 부족" 알림이 그대로 재발한다. */
    @Test
    fun `balance is null when the response is valid JSON without the balance structure`() = runBlocking {
        val auth = loginThen(ok("""{"error":"maintenance"}"""))
        assertNull(auth.getBalance())
    }

    @Test
    fun `balance is null when crntEntrsAmt is missing`() = runBlocking {
        val auth = loginThen(ok("""{"data":{"userMndp":{}}}"""))
        assertNull(auth.getBalance())
    }

    /** 반대 방향 회귀 방지 — 서버가 진짜 0원을 알려주면 그건 0이다(모름 아님). */
    @Test
    fun `a genuine zero balance is reported as 0, not null`() = runBlocking {
        val auth = loginThen(ok("""{"data":{"userMndp":{"crntEntrsAmt":0}}}"""))
        assertEquals(0, auth.getBalance())
    }

    private companion object {
        const val BALANCE_JSON = """{"data":{"userMndp":{"crntEntrsAmt":12345}}}"""
    }
}
