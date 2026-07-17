package com.umicorp.autolotto.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import java.time.LocalDateTime

/**
 * mergePurchasesByRound — 같은 회차의 주문 여러 건(예약구매 + 즉시/추가 구매)을 1건으로 병합.
 * 내역 카드·결과확인 알림이 "회차당 1건"을 가정하므로 병합이 그 가정을 복원한다.
 */
class PurchaseMergeTest {

    private fun purchase(
        round: Int,
        numbers: List<List<Int>>,
        auto: Int = numbers.size,
        manual: Int = 0,
        checked: Boolean = false,
        rank: String? = null,
        prize: Long = 0,
        gameRanks: List<String>? = null,
        gamePrizes: List<Long>? = null,
    ) = Purchase(
        round = round,
        date = LocalDateTime.of(2026, 7, 18, 20, 45),
        numbers = numbers,
        autoCount = auto,
        manualCount = manual,
        amount = numbers.size * 1000,
        checked = checked,
        rank = rank,
        prize = prize,
        gameRanks = gameRanks,
        gamePrizes = gamePrizes,
    )

    @Test
    fun `같은 회차 주문 두 건이 게임 순서를 보존하며 한 건으로 합쳐진다`() {
        val first = purchase(1233, listOf(listOf(1, 2, 3, 4, 5, 6), listOf(7, 8, 9, 10, 11, 12)), auto = 1, manual = 1)
        val extra = purchase(1233, listOf(listOf(13, 14, 15, 16, 17, 18)))
        val merged = mergePurchasesByRound(listOf(first, extra))

        assertEquals(1, merged.size)
        val m = merged.single()
        assertEquals(3, m.numbers.size)
        assertEquals(listOf(1, 2, 3, 4, 5, 6), m.numbers[0])
        assertEquals(listOf(13, 14, 15, 16, 17, 18), m.numbers[2])
        assertEquals(2, m.autoCount)
        assertEquals(1, m.manualCount)
        assertEquals(3000, m.amount)
    }

    @Test
    fun `다른 회차는 병합하지 않고, 단일 주문 회차는 원본 그대로 둔다`() {
        val a = purchase(1233, listOf(listOf(1, 2, 3, 4, 5, 6)))
        val b = purchase(1232, listOf(listOf(7, 8, 9, 10, 11, 12)))
        val merged = mergePurchasesByRound(listOf(a, b))

        assertEquals(2, merged.size)
        assertSame(a, merged[0])
        assertSame(b, merged[1])
    }

    @Test
    fun `추첨 후 병합 - 등수는 최고, 당첨금은 합산, 게임별 결과는 이어 붙는다`() {
        val first = purchase(
            1233, listOf(listOf(1, 2, 3, 4, 5, 6)),
            checked = true, rank = "nowin", prize = 0,
            gameRanks = listOf("nowin"), gamePrizes = listOf(0L),
        )
        val extra = purchase(
            1233, listOf(listOf(7, 8, 9, 10, 11, 12)),
            checked = true, rank = "rank4", prize = 50000,
            gameRanks = listOf("rank4"), gamePrizes = listOf(50000L),
        )
        val m = mergePurchasesByRound(listOf(first, extra)).single()

        assertEquals("rank4", m.rank)
        assertEquals(50000L, m.prize)
        assertEquals(listOf("nowin", "rank4"), m.gameRanks)
        assertEquals(listOf(0L, 50000L), m.gamePrizes)
        assertEquals(true, m.checked)
    }

    @Test
    fun `게임별 결과가 한쪽만 있으면 pending 패딩으로 정렬을 유지한다`() {
        val withRanks = purchase(
            1233, listOf(listOf(1, 2, 3, 4, 5, 6)),
            gameRanks = listOf("pending"), gamePrizes = listOf(0L),
        )
        val withoutRanks = purchase(1233, listOf(listOf(7, 8, 9, 10, 11, 12)))
        val m = mergePurchasesByRound(listOf(withRanks, withoutRanks)).single()

        assertEquals(listOf("pending", "pending"), m.gameRanks)
        assertEquals(listOf(0L, 0L), m.gamePrizes)
    }
}
