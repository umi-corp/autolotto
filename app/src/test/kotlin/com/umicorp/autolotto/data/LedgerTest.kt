package com.umicorp.autolotto.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.OffsetDateTime

class LedgerTest {

    private fun row(k: String, d: String, st: RowStatus = RowStatus.LOSE, amt: Long = 0, q: Int = 5) =
        LedgerRow(k, LocalDate.parse(d), 1200, q, st, amt)

    @Test fun json_roundTrip() {
        val l = Ledger(
            "645", PrefillState(false, LocalDate.parse("2021-11-22"), LocalDate.parse("2025-11-18")),
            OffsetDateTime.parse("2026-11-22T14:05:00+09:00"),
            listOf(
                row("A", "2026-11-21"),
                LedgerRow("B|1:123456", LocalDate.parse("2026-07-23"), 326, 1, RowStatus.WIN, 1000, rk = "SEVENTH", u = true),
                LedgerRow("C", LocalDate.parse("2026-06-14"), 1229, 5, RowStatus.WIN, 10_000, w = 2),
            ),
        )
        assertEquals(l, parseLedger(l.toJson(), "645"))
    }

    @Test fun parse_rejectsOtherProductAndVersion() {
        val j = emptyLedger("720").toJson()
        assertThrows(IllegalArgumentException::class.java) { parseLedger(j, "645") }
        assertThrows(IllegalArgumentException::class.java) { parseLedger(j.replace("\"v\":1", "\"v\":2"), "720") }
    }

    @Test fun applyWindow_upsertsAndKeepsMissing() {
        val local = listOf(row("A", "2026-11-21", RowStatus.PENDING), row("OLD", "2020-01-04"))
        val server = listOf(row("A", "2026-11-21", RowStatus.WIN, 5000), row("N", "2026-11-22"))
        val out = applyWindow(local, server).associateBy { it.k }
        assertEquals(setOf("A", "OLD", "N"), out.keys)
        assertEquals(RowStatus.WIN, out.getValue("A").st)
        assertEquals(5000L, out.getValue("A").amt)
    }

    // 회귀 방지: 최근 구간을 다시 받을 때 상세 조회로 센 당첨 게임 수를 잃지 않는다.
    @Test fun applyWindow_keepsWinCountWhileResultUnchanged() {
        val local = listOf(row("A", "2026-06-14", RowStatus.WIN, 10_000).copy(w = 2))
        assertEquals(2, applyWindow(local, listOf(row("A", "2026-06-14", RowStatus.WIN, 10_000))).single().w)
        assertEquals(null, applyWindow(local, listOf(row("A", "2026-06-14", RowStatus.WIN, 15_000))).single().w)
    }

    @Test fun applyWindow_idempotent() {
        val server = listOf(row("A", "2026-11-21"))
        assertEquals(applyWindow(emptyList(), server), applyWindow(applyWindow(emptyList(), server), server))
    }

    @Test fun syncWindows_newLedgerIsLastThreeMonths() {
        assertEquals(
            listOf(Window(LocalDate.parse("2026-08-22"), LocalDate.parse("2026-11-22"))),
            syncWindows(emptyLedger("645"), LocalDate.parse("2026-11-22")),
        )
    }

    @Test fun syncWindows_extendsToOldestPending() {
        val l = emptyLedger("645").copy(
            syncedAt = OffsetDateTime.parse("2026-11-20T10:00:00+09:00"),
            rows = listOf(row("P", "2026-07-01", RowStatus.PENDING)),
        )
        val w = syncWindows(l, LocalDate.parse("2026-11-22"))
        assertEquals(LocalDate.parse("2026-11-22"), w.first().to)
        assertEquals(LocalDate.parse("2026-07-01"), w.last().from)
        assertEquals(2, w.size)
    }

    @Test fun syncWindows_longAbsenceSplitsFromSyncedAtMinus14() {
        val l = emptyLedger("645").copy(syncedAt = OffsetDateTime.parse("2025-11-15T10:00:00+09:00"))
        assertEquals(LocalDate.parse("2025-11-01"), syncWindows(l, LocalDate.parse("2026-11-22")).last().from)
    }

    // 회귀 방지: 서버는 from + 3개월 < to 인 창을 거부한다(2026-11-22 실측: 02-28~05-29 거부, 03-01~06-01 허용).
    @Test fun windows_satisfyServerThreeMonthRule() {
        for (today in listOf("2026-05-29", "2026-05-31", "2026-07-31", "2026-12-31", "2028-02-29").map(LocalDate::parse)) {
            var p = initialPrefill(today)
            val windows = syncWindows(emptyLedger("645"), today).toMutableList()
            while (true) {
                val w = nextPrefillWindow(p) ?: break
                windows += w
                p = advancePrefill(p, w)
            }
            windows.forEach { w -> assertTrue("$today: $w", !w.from.plusMonths(3).isBefore(w.to)) }
            windows.zipWithNext().forEach { (newer, older) -> assertEquals("$today gap", newer.from.minusDays(1), older.to) }
        }
    }

    @Test fun prefill_runsToFloorThenDone() {
        var p = initialPrefill(LocalDate.parse("2026-11-22"))
        assertEquals(LocalDate.parse("2021-01-01"), p.floor) // 5년 전 해의 1월 1일
        assertEquals(LocalDate.parse("2026-08-21"), p.nextEnd)
        var n = 0
        while (true) {
            val w = nextPrefillWindow(p) ?: break
            assertTrue(w.from >= p.floor)
            p = advancePrefill(p, w)
            n++
        }
        assertTrue(p.done)
        assertEquals(23, n) // 동기화 1창 + 프리필 23창 = 24창
    }
}
