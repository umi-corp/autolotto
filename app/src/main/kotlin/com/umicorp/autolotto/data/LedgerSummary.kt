package com.umicorp.autolotto.data

import java.time.LocalDate
import java.time.YearMonth

const val UNIT_PRICE = 1000L

private val ANNUITY_RANKS = setOf("FIRST", "SECOND", "BONUS")

data class MonthlyTotal(val month: YearMonth, val purchaseWon: Long, val prizeWon: Long, val wins: Int)

data class LedgerSummary(
    val since: LocalDate?,
    val purchaseWon: Long,
    val units: Int,
    val prizeWon: Long,
    val wins: Int,
    val annuityWins: Int,
    val pending: Int,
    val returnPct: Double?,
    val monthly: List<MonthlyTotal>,
)

private fun List<LedgerRow>.purchase() = sumOf { it.q } * UNIT_PRICE
private fun List<LedgerRow>.prize() = filter { it.st == RowStatus.WIN }.sumOf { it.amt }

fun summarize(rows: List<LedgerRow>): LedgerSummary {
    val units = rows.sumOf { it.q }
    val purchase = rows.purchase()
    val prize = rows.prize()
    // 미추첨 구매분은 아직 당첨금이 없으므로 분모에서 뺀다.
    val settled = purchase - rows.filter { it.st == RowStatus.PENDING }.purchase()
    return LedgerSummary(
        since = rows.minOfOrNull { it.d },
        purchaseWon = purchase,
        units = units,
        prizeWon = prize,
        wins = rows.count { it.st == RowStatus.WIN },
        annuityWins = rows.count { it.rk in ANNUITY_RANKS },
        pending = rows.count { it.st == RowStatus.PENDING },
        returnPct = if (settled > 0) prize * 100.0 / settled else null,
        monthly = rows.groupBy { YearMonth.from(it.d) }
            .map { (m, rs) -> MonthlyTotal(m, rs.purchase(), rs.prize(), rs.count { it.st == RowStatus.WIN }) }
            .sortedByDescending { it.month },
    )
}

/** 월별 시트의 한 해. [months]는 1~12월이고, 구매가 없던 달은 0, 아직 오지 않은 달은 null이다. */
data class YearPage(val year: Int, val months: List<MonthlyTotal?>)

/** 원장의 가장 오래된 해부터 올해까지 오름차순. */
fun yearPages(monthly: List<MonthlyTotal>, thisMonth: YearMonth): List<YearPage> {
    val byMonth = monthly.associateBy { it.month }
    val first = monthly.minOfOrNull { it.month.year } ?: thisMonth.year
    return (first..thisMonth.year).map { y ->
        YearPage(y, (1..12).map { m ->
            val ym = YearMonth.of(y, m)
            if (ym > thisMonth) null else byMonth[ym] ?: MonthlyTotal(ym, 0, 0, 0)
        })
    }
}
