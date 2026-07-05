package com.umicorp.autolotto.dhlottery

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPublicKey
import javax.crypto.Cipher

/**
 * 로그인 6단계 시퀀스 통합 테스트 — 원본 auth_service_test.dart(DioAdapter)를 MockWebServer로 복원.
 * base URL 주입으로 모든 요청을 로컬 서버로 보내고, 실제 RSA 암호화가 end-to-end로 동작한다.
 */
class AuthServiceSequenceTest {

    private lateinit var server: MockWebServer
    private lateinit var keyPair: KeyPair
    private lateinit var modulusHex: String
    private lateinit var exponentHex: String
    private var capturedLoginBody: String? = null

    @Before
    fun setUp() {
        keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val pub = keyPair.public as RSAPublicKey
        modulusHex = pub.modulus.toString(16)
        exponentHex = pub.publicExponent.toString(16)
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() = server.shutdown()

    private fun authFor(dispatcher: Dispatcher): AuthService {
        server.dispatcher = dispatcher
        val baseUrl = server.url("/").toString().trimEnd('/')
        return AuthService(DhlotterySession(baseUrl = baseUrl, olottoUrl = baseUrl))
    }

    private fun ok(body: String) = MockResponse().setResponseCode(200).setBody(body)
    private fun rsaOk() = ok("{\"data\":{\"rsaModulus\":\"$modulusHex\",\"publicExponent\":\"$exponentHex\"}}")

    /** 성공 경로 디스패처. balanceBody로 BALANCE 응답을 바꾼다(verify+getBalance 공용). */
    private fun successDispatcher(balanceBody: String = "{\"data\":{}}") = object : Dispatcher() {
        override fun dispatch(request: RecordedRequest): MockResponse =
            when (request.path?.substringBefore('?')) {
                "/", "" -> ok("")
                ApiConstants.LOGIN_PAGE -> ok("")
                ApiConstants.RSA_MODULUS -> rsaOk()
                ApiConstants.LOGIN -> {
                    capturedLoginBody = request.body.readUtf8()
                    ok("").addHeader("Set-Cookie", "JSESSIONID=test-session-123; Domain=.dhlottery.co.kr; Path=/")
                }
                ApiConstants.MAIN -> ok("")
                ApiConstants.GAME645 -> ok("").addHeader("Set-Cookie", "JSESSIONID=ol-session-456; Path=/")
                ApiConstants.BALANCE -> ok(balanceBody)
                else -> MockResponse().setResponseCode(404)
            }
    }

    @Test
    fun `login runs full 6-step sequence with real RSA end-to-end`() = runBlocking {
        val auth = authFor(successDispatcher())
        val result = auth.login("testUser", "testPass")

        assertTrue(result)
        assertTrue(auth.isLoggedIn)
        assertTrue(server.requestCount >= 7) // main,login,rsa,loginPost,main,game645,verify

        // 실제 POST된 userId를 개인키로 복호화 → 원문 일치 (RSA가 시퀀스 안에서 정상 동작)
        val body = capturedLoginBody ?: error("login POST 본문 미캡처")
        val userIdHex = Regex("userId=([0-9a-f]+)").find(body)!!.groupValues[1]
        assertEquals("testUser", decrypt(userIdHex))
    }

    @Test
    fun `verify failure throws INVALID_CREDENTIALS and leaves isLoggedIn false`() {
        val auth = authFor(object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                when (request.path?.substringBefore('?')) {
                    ApiConstants.RSA_MODULUS -> rsaOk()
                    ApiConstants.LOGIN -> ok("").addHeader("Set-Cookie", "JSESSIONID=x; Domain=.dhlottery.co.kr; Path=/")
                    ApiConstants.GAME645 -> ok("").addHeader("Set-Cookie", "JSESSIONID=ol; Path=/")
                    ApiConstants.BALANCE -> MockResponse().setResponseCode(401) // 검증 실패
                    else -> ok("")
                }
        })
        val ex = try { runBlocking { auth.login("u", "p") }; null } catch (e: Exception) { e }
        assertTrue(ex is DhlotteryException && ex.message!!.contains("INVALID_CREDENTIALS"))
        assertFalse(auth.isLoggedIn)
    }

    @Test
    fun `missing RSA key throws and leaves isLoggedIn false`() {
        val auth = authFor(object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                if (request.path?.substringBefore('?') == ApiConstants.RSA_MODULUS) ok("{\"data\":null}") else ok("")
        })
        val ex = try { runBlocking { auth.login("u", "p") }; null } catch (e: Exception) { e }
        assertTrue(ex is DhlotteryException)
        assertFalse(auth.isLoggedIn)
    }

    @Test
    fun `getBalance parses amount after login`() = runBlocking {
        val auth = authFor(successDispatcher("{\"data\":{\"userMndp\":{\"crntEntrsAmt\":50000}}}"))
        auth.login("testUser", "testPass")
        assertEquals(50000, auth.getBalance())
    }

    @Test
    fun `failed re-login resets isLoggedIn (no zombie session)`() = runBlocking {
        val auth = authFor(successDispatcher())
        auth.login("u", "p")
        assertTrue(auth.isLoggedIn)

        // RSA 깨지는 디스패처로 교체 후 재로그인 실패 → isLoggedIn=false 여야 함
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                if (request.path?.substringBefore('?') == ApiConstants.RSA_MODULUS) ok("{\"data\":null}") else ok("")
        }
        try { auth.login("u", "p") } catch (_: Exception) {}
        assertFalse(auth.isLoggedIn)
    }

    private fun decrypt(hex: String): String {
        val bytes = ByteArray(hex.length / 2) { hex.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
        val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        cipher.init(Cipher.DECRYPT_MODE, keyPair.private)
        return String(cipher.doFinal(bytes), Charsets.UTF_8)
    }
}
