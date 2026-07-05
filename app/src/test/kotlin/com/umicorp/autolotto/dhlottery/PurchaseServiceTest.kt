package com.umicorp.autolotto.dhlottery

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.json.JSONArray
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPublicKey
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * PurchaseService 테스트.
 * - 순수함수: getCurrentRound/getDrawDates 경계(요일·토 20:45), 회차/추첨일 일관성 불변식,
 *   buildParam 출력, parseNumbersFromResponse(유효/무효, off-by-one).
 * - 서비스: execBuy MockWebServer(성공 resultCode "100" / HTML→세션만료), 로그인 게이트, 게임 수 검증.
 * 실제 사이트는 절대 호출하지 않는다.
 */
class PurchaseServiceTest {

    private val KST = ZoneId.of("Asia/Seoul")
    private val FIRST_ROUND: LocalDate = LocalDate.of(2002, 12, 7) // 1회차 = 토요일
    private fun kst(y: Int, mo: Int, d: Int, h: Int, mi: Int) =
        ZonedDateTime.of(y, mo, d, h, mi, 0, 0, KST)

    // ---------- getCurrentRound 경계 ----------

    @Test
    fun `getCurrentRound anchor saturday before draw is round 1`() {
        assertEquals(1, PurchaseService.getCurrentRound(kst(2002, 12, 7, 10, 0)))
    }

    @Test
    fun `getCurrentRound saturday 2044 before draw no bump`() {
        assertEquals(1, PurchaseService.getCurrentRound(kst(2002, 12, 7, 20, 44)))
    }

    @Test
    fun `getCurrentRound saturday 2046 after draw bumps to next round`() {
        assertEquals(2, PurchaseService.getCurrentRound(kst(2002, 12, 7, 20, 46)))
    }

    @Test
    fun `getCurrentRound saturday exactly 2045 is not after draw`() {
        // Dart isAfter는 strict → 정확히 20:45는 추첨 이후 아님
        assertEquals(1, PurchaseService.getCurrentRound(kst(2002, 12, 7, 20, 45)))
    }

    @Test
    fun `getCurrentRound sunday uses floorMod not negative modulo`() {
        // 일요일: 순진한 (6-7)%7=-1 이면 thisSaturday가 과거(12/07)→round 1(오답).
        // floorMod(-1,7)=6 → thisSaturday=12/14 → round 2(정답).
        assertEquals(2, PurchaseService.getCurrentRound(kst(2002, 12, 8, 10, 0)))
    }

    @Test
    fun `getCurrentRound weekdays map to upcoming saturday round`() {
        assertEquals(2, PurchaseService.getCurrentRound(kst(2002, 12, 9, 10, 0)))  // 월
        assertEquals(2, PurchaseService.getCurrentRound(kst(2002, 12, 13, 23, 59))) // 금
        assertEquals(2, PurchaseService.getCurrentRound(kst(2002, 12, 14, 10, 0)))  // 토(추첨 전)
        assertEquals(3, PurchaseService.getCurrentRound(kst(2002, 12, 14, 20, 46))) // 토(추첨 후)
    }

    // ---------- getDrawDates / 회차·추첨일 일관성 ----------

    @Test
    fun `getDrawDates single-sources draw date from round`() {
        val d1 = PurchaseService.getDrawDates(1)
        assertEquals(LocalDate.of(2002, 12, 7), d1.drawDate)
        assertEquals(LocalDate.of(2002, 12, 7).plusDays(365), d1.payLimitDate)

        val d2 = PurchaseService.getDrawDates(2)
        assertEquals(LocalDate.of(2002, 12, 14), d2.drawDate)
    }

    @Test
    fun `invariant draw date is always saturday of the returned round`() {
        val moments = listOf(
            kst(2002, 12, 7, 10, 0),   // 토 추첨 전
            kst(2002, 12, 7, 20, 46),  // 토 추첨 후
            kst(2002, 12, 8, 10, 0),   // 일
            kst(2002, 12, 9, 10, 0),   // 월
            kst(2026, 6, 28, 12, 0),   // 임의 최근 일요일
            kst(2026, 6, 27, 21, 0),   // 임의 최근 토요일(추첨 후)
        )
        for (now in moments) {
            val round = PurchaseService.getCurrentRound(now)
            val dates = PurchaseService.getDrawDates(round)
            // (a) 항상 토요일
            assertEquals("round=$round drawDate must be Saturday", DayOfWeek.SATURDAY, dates.drawDate.dayOfWeek)
            // (b) 회차에서 단일소스화된 날짜와 정확히 일치
            assertEquals(FIRST_ROUND.plusWeeks((round - 1).toLong()), dates.drawDate)
            // (c) 지급기한 = 추첨일 + 365일
            assertEquals(dates.drawDate.plusDays(365), dates.payLimitDate)
        }
    }

    @Test
    fun `invariant post-draw saturday moves draw date to next saturday`() {
        // 토 20:46 → round 2 → 추첨일은 '오늘'(12/07)이 아니라 다음 토(12/14)여야 일관.
        val round = PurchaseService.getCurrentRound(kst(2002, 12, 7, 20, 46))
        val dates = PurchaseService.getDrawDates(round)
        assertEquals(2, round)
        assertEquals(LocalDate.of(2002, 12, 14), dates.drawDate)
    }

    // ---------- buildParam ----------

    @Test
    fun `buildParam manual sorts and zero-pads numbers`() {
        val arr = JSONArray(PurchaseService.buildParam(0, listOf(listOf(6, 5, 4, 3, 2, 1))))
        assertEquals(1, arr.length())
        val a = arr.getJSONObject(0)
        assertEquals("1", a.getString("genType"))
        assertEquals("01,02,03,04,05,06", a.getString("arrGameChoiceNum"))
        assertEquals("A", a.getString("alpabet")) // 원본 오타 키 유지
    }

    @Test
    fun `buildParam auto uses null and slot letters in order`() {
        val raw = PurchaseService.buildParam(2, listOf(listOf(1, 2, 3, 4, 5, 6)))
        // 자동 게임은 arrGameChoiceNum이 JSON null로 직렬화돼야 함
        assertTrue(raw.contains("\"arrGameChoiceNum\":null"))
        val arr = JSONArray(raw)
        assertEquals(3, arr.length())
        assertEquals("A", arr.getJSONObject(0).getString("alpabet"))
        assertEquals("1", arr.getJSONObject(0).getString("genType"))
        assertEquals("B", arr.getJSONObject(1).getString("alpabet"))
        assertEquals("0", arr.getJSONObject(1).getString("genType"))
        assertTrue(arr.getJSONObject(1).isNull("arrGameChoiceNum"))
        assertEquals("C", arr.getJSONObject(2).getString("alpabet"))
        assertEquals("0", arr.getJSONObject(2).getString("genType"))
    }

    @Test
    fun `buildParam caps slots at five`() {
        val arr = JSONArray(PurchaseService.buildParam(10, emptyList()))
        assertEquals(5, arr.length())
        assertEquals("E", arr.getJSONObject(4).getString("alpabet"))
    }

    // ---------- parseNumbersFromResponse ----------

    @Test
    fun `parseNumbers extracts six numbers stripping slot and mode`() {
        // "A|01|02|04|27|39|443" → 앞 "A|" 제거, 끝 모드 '3' 제거 → [1,2,4,27,39,44]
        val out = PurchaseService.parseNumbersFromResponse(listOf("A|01|02|04|27|39|443"))
        assertEquals(listOf(listOf(1, 2, 4, 27, 39, 44)), out)
    }

    @Test
    fun `parseNumbers handles multiple valid lines`() {
        val out = PurchaseService.parseNumbersFromResponse(
            listOf("A|01|02|03|04|05|061", "B|10|20|30|40|44|452"),
        )
        assertEquals(listOf(listOf(1, 2, 3, 4, 5, 6), listOf(10, 20, 30, 40, 44, 45)), out)
    }

    @Test
    fun `parseNumbers skips invalid lines`() {
        val out = PurchaseService.parseNumbersFromResponse(
            listOf(
                "A|",                       // 너무 짧음(<3)
                "B|01|02|03|xx|05|061",     // 숫자 아님
                "C|01|02|03|04|051",        // 5개(개수 부족)
                "D|01|02|03|04|05|461",     // 46 → 범위 초과
                "E|01|02|03|04|05|061",     // 유효
            ),
        )
        assertEquals(listOf(listOf(1, 2, 3, 4, 5, 6)), out)
    }

    @Test
    fun `parseNumbers empty input returns empty`() {
        assertEquals(emptyList<List<Int>>(), PurchaseService.parseNumbersFromResponse(emptyList()))
    }

    // ---------- 서비스(MockWebServer) ----------

    private lateinit var server: MockWebServer
    private lateinit var keyPair: KeyPair
    private lateinit var modulusHex: String
    private lateinit var exponentHex: String

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

    private fun ok(body: String) = MockResponse().setResponseCode(200).setBody(body)
    private fun rsaOk() = ok("{\"data\":{\"rsaModulus\":\"$modulusHex\",\"publicExponent\":\"$exponentHex\"}}")

    /** 로그인 성공 디스패처(시퀀스 테스트와 동일 골격) → isLoggedIn=true 상태를 만든다. */
    private fun loginDispatcher() = object : Dispatcher() {
        override fun dispatch(request: RecordedRequest): MockResponse =
            when (request.path?.substringBefore('?')) {
                ApiConstants.RSA_MODULUS -> rsaOk()
                ApiConstants.LOGIN -> ok("").addHeader("Set-Cookie", "JSESSIONID=s; Domain=.dhlottery.co.kr; Path=/")
                ApiConstants.GAME645 -> ok("").addHeader("Set-Cookie", "JSESSIONID=ol; Path=/")
                ApiConstants.BALANCE -> ok("{\"data\":{}}") // verifyLogin 200
                else -> ok("")
            }
    }

    /** baseUrl=olottoUrl=mock 주입 후 로그인까지 끝낸 (auth, session) 반환. */
    private suspend fun loggedIn(): Pair<AuthService, DhlotterySession> {
        val baseUrl = server.url("/").toString().trimEnd('/')
        val session = DhlotterySession(baseUrl = baseUrl, olottoUrl = baseUrl)
        val auth = AuthService(session)
        server.dispatcher = loginDispatcher()
        auth.login("u", "p")
        return auth to session
    }

    @Test
    fun `execBuy success path returns parsed PurchaseResult`() = runBlocking {
        val (auth, session) = loggedIn()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                when (request.path?.substringBefore('?')) {
                    ApiConstants.READY_SOCKET -> ok("{\"ready_ip\":\"10.0.0.1\"}")
                    ApiConstants.EXEC_BUY -> ok(
                        "{\"result\":{\"resultCode\":\"100\",\"resultMsg\":\"OK\"," +
                            "\"arrGameChoiceNum\":[\"A|01|02|03|04|05|061\"]}}",
                    )
                    else -> ok("")
                }
        }
        val result = PurchaseService(auth, session)
            .purchase(autoGames = 0, manualNumbers = listOf(listOf(1, 2, 3, 4, 5, 6)))

        assertEquals(1, result.totalGames)
        assertEquals(1000, result.amount)
        assertEquals(0, result.autoCount)
        assertEquals(1, result.manualCount)
        assertEquals(listOf(listOf(1, 2, 3, 4, 5, 6)), result.numbers)
    }

    @Test
    fun `execBuy non-100 result code throws with resultMsg`() = runBlocking {
        val (auth, session) = loggedIn()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                when (request.path?.substringBefore('?')) {
                    ApiConstants.READY_SOCKET -> ok("{\"ready_ip\":\"10.0.0.1\"}")
                    ApiConstants.EXEC_BUY -> ok("{\"result\":{\"resultCode\":\"200\",\"resultMsg\":\"잔액부족\"}}")
                    else -> ok("")
                }
        }
        val ex = try {
            PurchaseService(auth, session).purchase(autoGames = 1); null
        } catch (e: Exception) {
            e
        }
        assertTrue(ex is DhlotteryException && ex.message!!.contains("잔액부족"))
    }

    @Test
    fun `execBuy HTML response throws session-expired`() = runBlocking {
        val (auth, session) = loggedIn()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                when (request.path?.substringBefore('?')) {
                    ApiConstants.READY_SOCKET -> ok("{\"ready_ip\":\"10.0.0.1\"}")
                    ApiConstants.EXEC_BUY -> ok("<html><body>로그인 페이지</body></html>")
                    else -> ok("")
                }
        }
        val ex = try {
            PurchaseService(auth, session).purchase(autoGames = 1); null
        } catch (e: Exception) {
            e
        }
        assertTrue(ex is DhlotteryException && ex.message!!.contains("세션 만료"))
    }

    @Test
    fun `readySocket HTML response throws session-expired`() = runBlocking {
        val (auth, session) = loggedIn()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                if (request.path?.substringBefore('?') == ApiConstants.READY_SOCKET) {
                    ok("<html>expired</html>")
                } else {
                    ok("")
                }
        }
        val ex = try {
            PurchaseService(auth, session).purchase(autoGames = 1); null
        } catch (e: Exception) {
            e
        }
        assertTrue(ex is DhlotteryException && ex.message!!.contains("세션 만료"))
    }

    @Test
    fun `purchase requires login`() {
        val session = DhlotterySession() // 네트워크 호출 전 게이트에서 throw
        val auth = AuthService(session)
        val ex = try {
            runBlocking { PurchaseService(auth, session).purchase(autoGames = 1) }; null
        } catch (e: Exception) {
            e
        }
        assertTrue(ex is DhlotteryException && ex.message!!.contains("로그인이 필요"))
    }

    @Test
    fun `purchase rejects game count outside 1 to 5`() = runBlocking {
        val (auth, session) = loggedIn()
        val tooMany = try {
            PurchaseService(auth, session).purchase(autoGames = 6); null
        } catch (e: Exception) {
            e
        }
        assertTrue(tooMany is DhlotteryException && tooMany.message!!.contains("게임 수"))

        val zero = try {
            PurchaseService(auth, session).purchase(autoGames = 0); null
        } catch (e: Exception) {
            e
        }
        assertTrue(zero is DhlotteryException && zero.message!!.contains("게임 수"))
    }
}
