package com.umicorp.autolotto.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class LedgerSummaryTest {

    @Test fun totals_sumPurchasePrizeAndPending() {
        // 36주문×5게임, 당첨 6주문 합 35,000원, 미추첨 1주문
        val rows = (1..36).map { i ->
            val st = when {
                i == 1 -> RowStatus.PENDING
                i <= 7 -> RowStatus.WIN
                else -> RowStatus.LOSE
            }
            LedgerRow(
                "K$i", LocalDate.parse("2026-11-22").minusWeeks(i.toLong() - 1), 1253 - i, 5, st,
                if (st == RowStatus.WIN) (if (i == 2) 10_000 else 5_000) else 0,
            )
        }
        val s = summarize(rows)
        assertEquals(180_000L, s.purchaseWon)
        assertEquals(180, s.units)
        assertEquals(35_000L, s.prizeWon)
        assertEquals(6, s.wins)
        assertEquals(1, s.pending)
        assertEquals(35_000.0 * 100 / 175_000, s.returnPct!!, 1e-9)
        assertEquals(LocalDate.parse("2026-11-22").minusWeeks(35), s.since)
    }

    @Test fun annuityRanksCountedSeparately() {
        val rows = listOf(
            LedgerRow("a", LocalDate.parse("2026-07-23"), 326, 1, RowStatus.WIN, 0, rk = "SECOND"),
            LedgerRow("b", LocalDate.parse("2026-07-23"), 326, 1, RowStatus.WIN, 1000, rk = "SEVENTH"),
        )
        val s = summarize(rows)
        assertEquals(1, s.annuityWins)
        assertEquals(1000L, s.prizeWon)
        assertEquals(2, s.wins)
    }

    @Test fun monthly_groupsByPurchaseMonthNewestFirst() {
        val rows = listOf(
            LedgerRow("a", LocalDate.parse("2026-10-31"), 1246, 5, RowStatus.LOSE, 0),
            LedgerRow("b", LocalDate.parse("2026-11-01"), 1247, 5, RowStatus.WIN, 5000),
        )
        val m = summarize(rows).monthly
        assertEquals(listOf(YearMonth.of(2026, 11), YearMonth.of(2026, 10)), m.map { it.month })
        assertEquals(MonthlyTotal(YearMonth.of(2026, 11), 5000, 5000, 1), m[0])
    }

    @Test fun yearPages_fillMonthsWithGapsAndLeaveFutureEmpty() {
        val monthly = listOf(
            MonthlyTotal(YearMonth.of(2026, 11), 15_000, 0, 0),
            MonthlyTotal(YearMonth.of(2026, 8), 20_000, 5_000, 1),
            MonthlyTotal(YearMonth.of(2023, 7), 5_000, 0, 0),
        )
        val pages = yearPages(monthly, YearMonth.of(2026, 11))
        assertEquals(listOf(2023, 2024, 2025, 2026), pages.map { it.year })
        assertTrue(pages.all { it.months.size == 12 })
        val y2026 = pages.last().months
        assertEquals(monthly[0], y2026[10])
        assertEquals(monthly[1], y2026[7])
        assertEquals(MonthlyTotal(YearMonth.of(2026, 1), 0, 0, 0), y2026[0])
        assertNull(y2026[11]) // 아직 오지 않은 12월
        assertTrue(pages[1].months.all { it != null && it.purchaseWon == 0L })
    }

    @Test fun yearPages_emptyLedgerIsThisYearOnly() {
        val pages = yearPages(emptyList(), YearMonth.of(2026, 3))
        assertEquals(listOf(2026), pages.map { it.year })
        assertEquals(3, pages[0].months.count { it != null })
    }

    // 6/45 한 주문(5게임)에서 두 게임이 당첨되면 당첨 2회다. 게임 수를 모르면 주문 1회로 센다.
    @Test fun wins_countWinningGamesWhenKnown() {
        val rows = listOf(
            LedgerRow("a", LocalDate.parse("2026-06-14"), 1229, 5, RowStatus.WIN, 10_000, w = 2),
            LedgerRow("b", LocalDate.parse("2026-06-07"), 1228, 5, RowStatus.WIN, 5_000),
        )
        val s = summarize(rows)
        assertEquals(3, s.wins)
        assertEquals(3, s.monthly.single().wins)
    }

    @Test fun empty_hasNullSinceAndRatio() {
        val s = summarize(emptyList())
        assertNull(s.since)
        assertNull(s.returnPct)
        assertEquals(0L, s.purchaseWon)
    }
}
