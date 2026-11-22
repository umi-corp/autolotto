package com.umicorp.autolotto.dhlottery

import com.umicorp.autolotto.data.LedgerRow
import com.umicorp.autolotto.data.RowStatus
import com.umicorp.autolotto.data.Window
import com.umicorp.autolotto.data.WindowFetcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.time.format.DateTimeFormatter

/** 원장 목록(selectMyLotteryledger.do)의 한 창을 모든 페이지까지 받아 6/45 주문 행으로 바꾼다. 상세 조회는 하지 않는다. */
class LedgerService(private val session: DhlotterySession) : WindowFetcher {

    private var visited = false

    override suspend fun fetch(w: Window): List<LedgerRow> = withContext(Dispatchers.IO) {
        // 원장 페이지 방문(Referer·쿠키)은 프리필 창마다가 아니라 실행당 한 번이면 된다.
        if (!visited) {
            session.get(session.base(ApiConstants.MYPAGE_LEDGER)).close()
            visited = true
        }
        fetchLedgerPages(session, w).filter { it.optString("ltGdsNm") == "로또6/45" }.mapNotNull(::toRow)
    }

    private fun toRow(o: JSONObject): LedgerRow? {
        val k = o.optString("ntslOrdrNo").ifEmpty { return null }
        val d = HistoryService.parseDhDateTime(o.optString("eltOrdrDt"))?.toLocalDate() ?: return null
        val st = rowStatus(o.optString("ltWnResult"))
        return LedgerRow(
            k = k,
            d = d,
            r = o.optString("ltEpsd").toIntOrNull() ?: 0,
            q = o.optString("prchsQty").toIntOrNull() ?: 0,
            st = st,
            amt = if (st == RowStatus.PENDING) 0 else o.optString("ltWnAmt").filter(Char::isDigit).toLongOrNull() ?: 0,
        )
    }
}

private const val PAGE_SIZE = 100
private const val MAX_PAGES = 10
private val YMD: DateTimeFormatter = DateTimeFormatter.BASIC_ISO_DATE

private fun rowStatus(ltWnResult: String) = when (ltWnResult) {
    "당첨" -> RowStatus.WIN
    "낙첨" -> RowStatus.LOSE
    else -> RowStatus.PENDING
}

/**
 * 창 하나의 원장 목록 행 전부(상품 구분 없이). 일부 페이지만 받은 창을 반영하면 행이 사라지므로
 * 페이지 도중 실패·[MAX_PAGES] 초과는 모두 [IOException]이다.
 */
private fun fetchLedgerPages(session: DhlotterySession, w: Window): List<JSONObject> {
    val out = mutableListOf<JSONObject>()
    for (pageNum in 1..MAX_PAGES) {
        val body = session.get(
            session.base(ApiConstants.PURCHASE_HISTORY) +
                "?srchStrDt=${YMD.format(w.from)}&srchEndDt=${YMD.format(w.to)}" +
                "&pageNum=$pageNum&recordCountPerPage=$PAGE_SIZE&_=${System.currentTimeMillis()}",
            mapOf(
                "Accept" to "application/json, text/javascript, */*; q=0.01",
                "X-Requested-With" to "XMLHttpRequest",
                "Referer" to session.base(ApiConstants.MYPAGE_LEDGER),
            ),
        ).use {
            if (!it.isSuccessful) throw IOException("ledger_list_http_${it.code}")
            it.body?.string().orEmpty()
        }
        if (!body.trimStart().startsWith("{")) throw IOException("ledger_list_not_json")
        // 기간 거부("조회기간은 최대 3개월…")는 data=null로 온다.
        val data = JSONObject(body).optJSONObject("data") ?: throw IOException("ledger_list_no_data")
        val list = data.optJSONArray("list")
        val rows = (0 until (list?.length() ?: 0)).map { list!!.getJSONObject(it) }
        out += rows
        if (out.size >= data.optInt("total", 0)) return out
        if (rows.isEmpty()) throw IOException("ledger_list_short")
    }
    throw IOException("ledger_list_page_cap")
}
