package com.umicorp.autolotto.dhlottery

import com.umicorp.autolotto.data.LedgerRow
import com.umicorp.autolotto.data.RowStatus
import com.umicorp.autolotto.data.Window
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.time.LocalDate

/** 픽스처는 실측 응답 형태를 따른 합성 값이다(실제 주문번호·번호 아님). */
class LedgerServiceTest {

    private lateinit var server: MockWebServer
    private val pages = mutableListOf<String?>()
    private val window = Window(LocalDate.parse("2026-08-22"), LocalDate.parse("2026-11-22"))

    @Before fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After fun tearDown() = server.shutdown()

    private fun lotto(i: Int, result: String = "낙첨", amt: String? = "0") =
        """{"ntslOrdrNo":"O$i","ltGdsCd":"LO40","ltGdsNm":"로또6/45","ltEpsd":${1200 + i % 50},
           "eltOrdrDt":"2026-10-01","prchsQty":5,"ltWnResult":"$result","ltWnAmt":${amt?.let { "\"$it\"" } ?: "null"}}"""

    private fun page(total: Int, rows: List<String>) = """{"data":{"total":$total,"list":[${rows.joinToString(",")}]}}"""

    private fun serviceFor(respond: (pageNum: Int) -> MockResponse): LedgerService {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                if (request.path?.substringBefore('?') == ApiConstants.PURCHASE_HISTORY) {
                    val n = request.requestUrl!!.queryParameter("pageNum")
                    pages += n
                    respond(n!!.toInt())
                } else {
                    MockResponse().setBody("")
                }
        }
        val baseUrl = server.url("/").toString().trimEnd('/')
        return LedgerService(DhlotterySession(baseUrl = baseUrl, olottoUrl = baseUrl))
    }

    private fun ok(body: String) = MockResponse().setResponseCode(200).setBody(body)

    @Test fun fetch_mapsLottoRowsAndSkipsOthers() = runBlocking {
        val pension = """{"ntslOrdrNo":"P1","ltGdsCd":"LP72","ltGdsNm":"연금복권720+","ltEpsd":326,
            "eltOrdrDt":"2026-10-01","prchsQty":1,"ltWnResult":"낙첨","ltWnAmt":"0"}"""
        val svc = serviceFor { ok(page(3, listOf(lotto(1, "당첨", "5000"), lotto(2, "미추첨", null), pension))) }
        val rows = svc.fetch(window).associateBy { it.k }
        assertEquals(setOf("O1", "O2"), rows.keys)
        assertEquals(RowStatus.WIN, rows.getValue("O1").st)
        assertEquals(5000L, rows.getValue("O1").amt)
        assertEquals(RowStatus.PENDING, rows.getValue("O2").st)
        assertEquals(0L, rows.getValue("O2").amt)
        assertEquals(5, rows.getValue("O1").q)
        assertEquals(1201, rows.getValue("O1").r)
        assertEquals(LocalDate.parse("2026-10-01"), rows.getValue("O1").d)
    }

    @Test fun fetch_pagesUntilTotal() = runBlocking {
        val svc = serviceFor { n ->
            ok(page(140, (if (n == 1) 0 until 100 else 100 until 140).map { lotto(it) }))
        }
        assertEquals(140, svc.fetch(window).size)
        assertEquals(listOf("1", "2"), pages)
    }

    @Test fun fetch_midPageFailure_throws() {
        val svc = serviceFor { n ->
            if (n == 1) ok(page(140, (0 until 100).map { lotto(it) })) else MockResponse().setResponseCode(500)
        }
        assertThrows(IOException::class.java) { runBlocking { svc.fetch(window) } }
    }

    @Test fun fetch_htmlBody_throws() {
        val svc = serviceFor { ok("<html><body>login</body></html>") }
        assertThrows(IOException::class.java) { runBlocking { svc.fetch(window) } }
    }

    @Test fun fetch_rejectedWindow_throws() {
        val svc = serviceFor {
            ok("""{"resultCode":"E","resultMessage":"조회기간은 최대 3개월까지 가능합니다.","data":null}""")
        }
        assertThrows(IOException::class.java) { runBlocking { svc.fetch(window) } }
    }

    /** 하루 창 목록에 [order] 한 건, 상세는 [detail] 응답. 상세 요청 URL을 [detailUrls]에 남긴다. */
    private val detailUrls = mutableListOf<String>()
    private fun winService(detail: MockResponse): LedgerService {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.path?.substringBefore('?')) {
                ApiConstants.PURCHASE_HISTORY -> ok(page(1, listOf(
                    """{"ntslOrdrNo":"O1","ltGdsCd":"LO40","ltGdsNm":"로또6/45","ltEpsd":1229,"gmInfo":"BAR1",
                       "eltOrdrDt":"2026-06-14","prchsQty":5,"ltWnResult":"당첨","ltWnAmt":"10000"}""",
                )))
                ApiConstants.TICKET_DETAIL -> detail.also { detailUrls += request.path!! }
                else -> MockResponse().setBody("")
            }
        }
        val baseUrl = server.url("/").toString().trimEnd('/')
        return LedgerService(DhlotterySession(baseUrl = baseUrl, olottoUrl = baseUrl))
    }

    private val twoFifths = ok(
        """{"data":{"success":true,"ticket":{"drawed":true,"game_dtl":[
           {"num":[1,2,3,4,5,6],"rank":5,"amt":5000},{"num":[1,2,3,4,5,7],"rank":5,"amt":5000},
           {"num":[1,2,3,4,8,9],"rank":0,"amt":0},{"num":[1,2,3,8,9,10],"rank":0,"amt":0},{"num":[1,2,8,9,10,11],"rank":0,"amt":0}]}}}""",
    )
    private val winRow = LedgerRow("O1", LocalDate.parse("2026-06-14"), 1229, 5, RowStatus.WIN, 10_000)

    @Test fun winCount_countsWinningGamesFromDetail() = runBlocking {
        assertEquals(2, winService(twoFifths).winCount(winRow))
        assertTrue(detailUrls.single().contains("ntslOrdrNo=O1"))
        assertTrue(detailUrls.single().contains("barcd=BAR1"))
    }

    @Test fun resolveWinCounts_fillsOnlyUnknownWins() = runBlocking {
        val rows = listOf(winRow, winRow.copy(k = "O2", w = 3), winRow.copy(k = "O3", st = RowStatus.LOSE, amt = 0))
        val out = resolveWinCounts(rows, winService(twoFifths)).associateBy { it.k }
        assertEquals(2, out.getValue("O1").w)
        assertEquals(3, out.getValue("O2").w)
        assertEquals(null, out.getValue("O3").w)
        assertEquals(1, detailUrls.size)
    }

    @Test fun resolveWinCounts_connectionFailureLeavesRowsForNextSync() = runBlocking {
        val out = resolveWinCounts(listOf(winRow), winService(MockResponse().setResponseCode(500)))
        assertEquals(null, out.single().w)
    }

    @Test fun fetch_pageCapExceeded_throws() {
        val svc = serviceFor { n -> ok(page(1200, (0 until 100).map { lotto(n * 100 + it) })) }
        assertThrows(IOException::class.java) { runBlocking { svc.fetch(window) } }
        assertEquals(10, pages.size)
    }
}
