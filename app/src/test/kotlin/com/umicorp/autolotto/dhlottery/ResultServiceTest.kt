package com.umicorp.autolotto.dhlottery

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * ResultService 테스트 — 당첨번호 파싱(MockWebServer)과 checkMatches(순수 로컬 등수 계산).
 * checkMatches는 표시 비사용이지만 원본 충실 포팅이므로 동작을 고정한다.
 */
class ResultServiceTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() = server.shutdown()

    private fun ok(body: String) = MockResponse().setResponseCode(200).setBody(body)

    private fun resultFor(dispatcher: Dispatcher): ResultService {
        server.dispatcher = dispatcher
        val baseUrl = server.url("/").toString().trimEnd('/')
        return ResultService(DhlotterySession(baseUrl = baseUrl, olottoUrl = baseUrl))
    }

    @Test
    fun `getWinningNumbers parses and sorts numbers`() = runBlocking {
        val body = """
            {"data":{"list":[{
              "tm1WnNo":33,"tm2WnNo":3,"tm3WnNo":20,"tm4WnNo":15,"tm5WnNo":43,"tm6WnNo":11,
              "bnsWnNo":7,"ltEpsd":1124,"ltRflYmd":"20260627",
              "rnk1WnAmt":2000000000,"rnk2WnAmt":50000000,"rnk3WnAmt":1500000
            }]}}
        """.trimIndent()
        val result = resultFor(object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                if (request.path?.substringBefore('?') == ApiConstants.WINNING_NUMBER) ok(body) else ok("")
        }).getWinningNumbers(1124)!!

        assertEquals(1124, result.round)
        assertEquals(listOf(3, 11, 15, 20, 33, 43), result.numbers) // 정렬됨
        assertEquals(7, result.bonus)
        assertEquals("2026-06-27", result.date) // yyyyMMdd → yyyy-MM-dd
        assertEquals(2_000_000_000L, result.prize1st)
        assertEquals(50_000_000L, result.prize2nd)
        assertEquals(1_500_000L, result.prize3rd)
    }

    @Test
    fun `getWinningNumbers returns null on empty list`() = runBlocking {
        val r = resultFor(object : Dispatcher() {
            override fun dispatch(request: RecordedRequest) = ok("""{"data":{"list":[]}}""")
        }).getWinningNumbers()
        assertNull(r)
    }

    @Test
    fun `getWinningNumbers returns null on non-200`() = runBlocking {
        val r = resultFor(object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                if (request.path?.substringBefore('?') == ApiConstants.WINNING_NUMBER) {
                    MockResponse().setResponseCode(500)
                } else {
                    ok("")
                }
        }).getWinningNumbers()
        assertNull(r)
    }

    @Test
    fun `getWinningNumbers keeps prizes above Int MAX (overflow regression)`() = runBlocking {
        // 실제 1등 당첨금은 Int.MAX(약 21.4억)을 초과 가능(예: 26.7억). Int였다면 음수로 오버플로.
        val body = """
            {"data":{"list":[{
              "tm1WnNo":1,"tm2WnNo":2,"tm3WnNo":3,"tm4WnNo":4,"tm5WnNo":5,"tm6WnNo":6,
              "bnsWnNo":7,"ltEpsd":1130,"ltRflYmd":"20260704",
              "rnk1WnAmt":2670000000,"rnk2WnAmt":3000000000,"rnk3WnAmt":1500000
            }]}}
        """.trimIndent()
        val r = resultFor(object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                if (request.path?.substringBefore('?') == ApiConstants.WINNING_NUMBER) ok(body) else ok("")
        }).getWinningNumbers(1130)!!
        assertEquals(2_670_000_000L, r.prize1st)
        assertEquals(3_000_000_000L, r.prize2nd)
        assertTrue(r.prize1st > Int.MAX_VALUE)
    }

    // ---------- checkMatches (로컬 등수 계산 — 표시 비사용) ----------

    @Test
    fun `checkMatches computes ranks including bonus`() {
        val winning = listOf(1, 2, 3, 4, 5, 6)
        val out = ResultService.checkMatches(
            listOf(
                listOf(1, 2, 3, 4, 5, 6),     // 6개 → 1등
                listOf(1, 2, 3, 4, 5, 7),     // 5개+보너스 → 2등
                listOf(1, 2, 3, 4, 5, 8),     // 5개 → 3등
                listOf(1, 2, 3, 4, 8, 9),     // 4개 → 4등
                listOf(1, 2, 3, 8, 9, 10),    // 3개 → 5등
                listOf(8, 9, 10, 11, 12, 13), // 0개 → 낙첨
            ),
            winning,
            bonus = 7,
        )
        assertEquals(listOf("rank1", "rank2", "rank3", "rank4", "rank5", "nowin"), out.map { it.rank })
        assertEquals(listOf(0, 0, 0, 50000, 5000, 0), out.map { it.prize })
        assertTrue(out[1].bonusMatch)         // 2등: 보너스 일치
        assertFalse(out[2].bonusMatch)        // 3등: 보너스 불일치
        assertTrue(out[0].isWinner)
        assertFalse(out[5].isWinner)
        assertEquals(listOf(1, 2, 3, 4), out[3].matched) // 4등: 매칭 4개(정렬됨)
        assertEquals(4, out[3].matchCount)
    }
}
