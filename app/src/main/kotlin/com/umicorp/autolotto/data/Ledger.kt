package com.umicorp.autolotto.data

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.time.DateTimeException
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId

/** 누적 구매·당첨 원장(계정별 JSON). 창을 받을 때 덮어쓰기·추가만 하고 행을 지우지 않는다. */
const val LEDGER_VERSION = 1

val LEDGER_ZONE: ZoneId = ZoneId.of("Asia/Seoul")

enum class RowStatus { PENDING, LOSE, WIN }

data class LedgerRow(
    val k: String,
    val d: LocalDate,
    val r: Int,
    val q: Int,
    val st: RowStatus,
    val amt: Long,
    val rk: String? = null,
    val u: Boolean = false,
)

data class PrefillState(val done: Boolean, val floor: LocalDate, val nextEnd: LocalDate)

data class Ledger(
    val product: String,
    val prefill: PrefillState?,
    val syncedAt: OffsetDateTime?,
    val rows: List<LedgerRow>,
)

data class Window(val from: LocalDate, val to: LocalDate)

fun interface WindowFetcher {
    suspend fun fetch(w: Window): List<LedgerRow>
}

fun emptyLedger(product: String) = Ledger(product, null, null, emptyList())

fun Ledger.toJson(): String = JSONObject()
    .put("v", LEDGER_VERSION)
    .put("product", product)
    .apply {
        prefill?.let {
            put("prefill", JSONObject().put("done", it.done).put("floor", it.floor.toString()).put("nextEnd", it.nextEnd.toString()))
        }
        syncedAt?.let { put("syncedAt", it.toString()) }
    }
    .put("rows", JSONArray(rows.map { it.toJson() }))
    .toString()

private fun LedgerRow.toJson() = JSONObject()
    .put("k", k).put("d", d.toString()).put("r", r).put("q", q)
    .put("st", st.name.lowercase()).put("amt", amt)
    .apply {
        rk?.let { put("rk", it) }
        if (u) put("u", true)
    }

fun parseLedger(json: String, product: String): Ledger = try {
    val o = JSONObject(json)
    require(o.getInt("v") == LEDGER_VERSION) { "unsupported ledger version" }
    require(o.getString("product") == product) { "ledger product mismatch" }
    val rows = o.getJSONArray("rows")
    Ledger(
        product = product,
        prefill = o.optJSONObject("prefill")?.let {
            PrefillState(it.getBoolean("done"), LocalDate.parse(it.getString("floor")), LocalDate.parse(it.getString("nextEnd")))
        },
        syncedAt = o.optString("syncedAt").takeIf { it.isNotEmpty() }?.let(OffsetDateTime::parse),
        rows = (0 until rows.length()).map { parseRow(rows.getJSONObject(it)) },
    )
} catch (e: JSONException) {
    throw IllegalArgumentException("malformed ledger", e)
} catch (e: DateTimeException) {
    throw IllegalArgumentException("malformed ledger date", e)
}

private fun parseRow(o: JSONObject) = LedgerRow(
    k = o.getString("k"),
    d = LocalDate.parse(o.getString("d")),
    r = o.getInt("r"),
    q = o.getInt("q"),
    st = RowStatus.valueOf(o.getString("st").uppercase()),
    amt = o.getLong("amt"),
    rk = o.optString("rk").takeIf { it.isNotEmpty() },
    u = o.optBoolean("u", false),
)

/** 키가 같은 행은 서버 값으로 덮어쓰고 새 키는 더한다. 서버에 없는 원장 행은 남긴다(서버 보관 한도 밖 기록 보존). */
fun applyWindow(rows: List<LedgerRow>, serverRows: List<LedgerRow>): List<LedgerRow> =
    (rows + serverRows).associateBy { it.k }.values.sortedByDescending { it.d }

/** 서버는 `from + 3개월 < to`인 창을 거부한다. 월말 보정(05-29 − 3개월 = 02-28)이 걸리면 시작일을 그만큼 늦춘다. */
private fun threeMonthsBefore(end: LocalDate): LocalDate =
    generateSequence(end.minusMonths(3)) { it.plusDays(1) }.first { !it.plusMonths(3).isBefore(end) }

private fun window(end: LocalDate, lower: LocalDate) = Window(maxOf(threeMonthsBefore(end), lower), end)

private fun windowsDown(end: LocalDate, lower: LocalDate): List<Window> =
    generateSequence(window(end, lower)) { w -> if (w.from > lower) window(w.from.minusDays(1), lower) else null }.toList()

fun syncWindows(ledger: Ledger, today: LocalDate): List<Window> {
    val from = listOfNotNull(
        ledger.rows.filter { it.st == RowStatus.PENDING }.minOfOrNull { it.d },
        ledger.syncedAt?.atZoneSameInstant(LEDGER_ZONE)?.toLocalDate()?.minusDays(14),
        threeMonthsBefore(today),
    ).min()
    return windowsDown(today, from)
}

/** 프리필 하한은 5년 전 해의 1월 1일이다(시작할 때 한 번 정해 [PrefillState.floor]에 고정). */
fun initialPrefill(today: LocalDate) =
    PrefillState(false, LocalDate.of(today.year - 5, 1, 1), threeMonthsBefore(today).minusDays(1))

fun nextPrefillWindow(p: PrefillState): Window? = if (p.done) null else window(p.nextEnd, p.floor)

fun advancePrefill(p: PrefillState, applied: Window): PrefillState =
    if (applied.from <= p.floor) p.copy(done = true) else p.copy(nextEnd = applied.from.minusDays(1))
