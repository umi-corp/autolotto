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
import java.time.LocalDate

/**
 * HistoryService 테스트 — MockWebServer로 원장 목록 + 티켓 상세를 스텁해 Purchase 빌드를 검증.
 * 실제 사이트는 절대 호출하지 않는다.
 */
class HistoryServiceTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() = server.shutdown()

    private fun ok(body: String) = MockResponse().setResponseCode(200).setBody(body)

    /** 목록/상세 응답을 주입해 HistoryService 생성 (원장 페이지 방문 등은 빈 응답). */
    private fun historyFor(ledger: String, detail: String): HistoryService {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                when (request.path?.substringBefore('?')) {
                    ApiConstants.PURCHASE_HISTORY -> ok(ledger)
                    ApiConstants.TICKET_DETAIL -> ok(detail)
                    else -> ok("")
                }
        }
        val baseUrl = server.url("/").toString().trimEnd('/')
        return HistoryService(DhlotterySession(baseUrl = baseUrl, olottoUrl = baseUrl))
    }

    // 비-로또 항목(필터 대상) + 로또6/45 항목 1건.
    private val ledgerOneLotto = """
        {"data":{"list":[
          {"ltGdsNm":"연금복권720+","ntslOrdrNo":"X","gmInfo":"Y"},
          {"ltGdsNm":"로또6/45","ltEpsdView":"1124회","ntslOrdrNo":"ORD123","gmInfo":"BARCODE1",
           "eltOrdrDt":"2026-06-25","epsdRflDt":"2026-06-27"}
        ]}}
    """.trimIndent()

    @Test
    fun `fetchRecentPurchases parses drawed ticket ranks prize and winning numbers`() = runBlocking {
        val detail = """
            {"data":{"success":true,"ticket":{
              "drawed":true,
              "win_num":[3,11,15,20,33,43],
              "bonus_num":7,
              "game_dtl":[
                {"num":[1,2,3,4,5,6],"type":1,"rank":5,"amt":5000},
                {"num":[10,20,30,40,44,45],"type":0,"rank":0,"amt":0}
              ]
            }}}
        """.trimIndent()
        val purchases = historyFor(ledgerOneLotto, detail).fetchRecentPurchases()

        assertEquals(1, purchases.size) // 비-로또 항목은 필터됨
        val p = purchases[0]
        assertEquals(1124, p.round)
        assertEquals(listOf(listOf(1, 2, 3, 4, 5, 6), listOf(10, 20, 30, 40, 44, 45)), p.numbers)
        assertEquals(1, p.manualCount) // type=1
        assertEquals(1, p.autoCount)   // type=0
        assertEquals(2000, p.amount)
        assertEquals(2, p.totalGames)
        assertTrue(p.checked)
        assertEquals(listOf("rank5", "nowin"), p.gameRanks)
        assertEquals(listOf(5000L, 0L), p.gamePrizes)
        assertEquals("rank5", p.rank)   // 최고 등수
        assertEquals(5000L, p.prize)     // 총 당첨금
        assertEquals(listOf(3, 11, 15, 20, 33, 43), p.winningNumbers)
        assertEquals(7, p.bonusNumber)
        assertEquals(LocalDate.of(2026, 6, 27).atStartOfDay(), p.date) // epsdRflDt 우선
    }

    @Test
    fun `fetchRecentPurchases marks undrawn ticket pending with null rank`() = runBlocking {
        val detail = """
            {"data":{"success":true,"ticket":{
              "drawed":false,
              "game_dtl":[{"num":[1,2,3,4,5,6],"type":0,"rank":0,"amt":0}]
            }}}
        """.trimIndent()
        val p = historyFor(ledgerOneLotto, detail).fetchRecentPurchases()[0]

        assertFalse(p.checked)
        assertEquals(listOf("pending"), p.gameRanks)
        assertNull(p.rank)
        assertEquals(0, p.prize)
        assertEquals(1, p.autoCount)
        assertEquals(0, p.manualCount)
        assertEquals(1000, p.amount)
        assertNull(p.winningNumbers) // win_num 없음
        assertNull(p.bonusNumber)
    }

    @Test
    fun `fetchRecentPurchases skips item when detail success is false`() = runBlocking {
        val purchases = historyFor(ledgerOneLotto, """{"data":{"success":false}}""").fetchRecentPurchases()
        assertTrue(purchases.isEmpty())
    }

    @Test
    fun `fetchRecentPurchases returns empty when data list missing`() = runBlocking {
        val purchases = historyFor("""{"data":{}}""", "").fetchRecentPurchases()
        assertTrue(purchases.isEmpty())
    }

    @Test
    fun `fetchRecentPurchases honours count limit`() = runBlocking {
        val ledgerThree = """
            {"data":{"list":[
              {"ltGdsNm":"로또6/45","ltEpsdView":"1회","ntslOrdrNo":"O1","gmInfo":"B1","eltOrdrDt":"2026-06-25","epsdRflDt":"2026-06-27"},
              {"ltGdsNm":"로또6/45","ltEpsdView":"1회","ntslOrdrNo":"O2","gmInfo":"B2","eltOrdrDt":"2026-06-25","epsdRflDt":"2026-06-27"},
              {"ltGdsNm":"로또6/45","ltEpsdView":"1회","ntslOrdrNo":"O3","gmInfo":"B3","eltOrdrDt":"2026-06-25","epsdRflDt":"2026-06-27"}
            ]}}
        """.trimIndent()
        val detail = """
            {"data":{"success":true,"ticket":{"drawed":false,
              "game_dtl":[{"num":[1,2,3,4,5,6],"type":0,"rank":0,"amt":0}]}}}
        """.trimIndent()
        val purchases = historyFor(ledgerThree, detail).fetchRecentPurchases(count = 2)
        assertEquals(2, purchases.size)
    }

    @Test
    fun `fetchRecentPurchases keeps prize above Int MAX (overflow regression)`() = runBlocking {
        val detail = """
            {"data":{"success":true,"ticket":{"drawed":true,
              "win_num":[1,2,3,4,5,6],"bonus_num":7,
              "game_dtl":[{"num":[1,2,3,4,5,6],"type":0,"rank":1,"amt":2670000000}]}}}
        """.trimIndent()
        val p = historyFor(ledgerOneLotto, detail).fetchRecentPurchases()[0]
        assertEquals(listOf(2_670_000_000L), p.gamePrizes) // Int였다면 음수로 절단
        assertEquals(2_670_000_000L, p.prize)
        assertTrue(p.prize > Int.MAX_VALUE)
    }

    @Test
    fun `fetchPurchases queries the requested window`() = runBlocking {
        val detail = """
            {"data":{"success":true,"ticket":{"drawed":false,
              "game_dtl":[{"num":[1,2,3,4,5,6],"type":0,"rank":0,"amt":0}]}}}
        """.trimIndent()
        val purchases = historyFor(ledgerOneLotto, detail)
            .fetchPurchases(LocalDate.of(2026, 1, 5), LocalDate.of(2026, 4, 5))
        assertEquals(1, purchases.size)

        server.takeRequest() // 1) 마이페이지 원장 방문
        val listPath = server.takeRequest().path.orEmpty() // 2) 목록 조회
        assertTrue(listPath, listPath.contains("srchStrDt=20260105"))
        assertTrue(listPath, listPath.contains("srchEndDt=20260405"))
    }

    @Test
    fun `fetchRecentPurchases parses compact yyyyMMdd dates (Dart tryParse parity)`() = runBlocking {
        val ledger = """
            {"data":{"list":[
              {"ltGdsNm":"로또6/45","ltEpsdView":"1124회","ntslOrdrNo":"ORD9","gmInfo":"BC9",
               "eltOrdrDt":"20260625","epsdRflDt":"20260627"}
            ]}}
        """.trimIndent()
        val detail = """
            {"data":{"success":true,"ticket":{"drawed":false,
              "game_dtl":[{"num":[1,2,3,4,5,6],"type":0,"rank":0,"amt":0}]}}}
        """.trimIndent()
        val p = historyFor(ledger, detail).fetchRecentPurchases()[0]
        assertEquals(LocalDate.of(2026, 6, 27).atStartOfDay(), p.date) // 컴팩트 날짜가 now로 폴백 안 됨
    }
}
