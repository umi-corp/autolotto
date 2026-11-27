package com.umicorp.autolotto.data

import com.umicorp.autolotto.dhlottery.PurchaseService.Companion.latestCompletedRound
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZonedDateTime

class PrizeByRankTest {
    @Test
    fun `gross exemption stops at two million won`() {
        assertEquals(2_000_000L, prizeAfterTax(2_000_000))
        assertEquals(1_560_221L, prizeAfterTax(2_000_001))
    }

    @Test
    fun `higher rate applies only above three hundred million after game cost`() {
        assertEquals(234_000_220L, prizeAfterTax(300_000_000))
        assertEquals(234_000_221L, prizeAfterTax(300_000_001))
        assertEquals(234_001_000L, prizeAfterTax(300_001_000))
        assertEquals(234_001_001L, prizeAfterTax(300_001_001))
        assertEquals(234_001_033L, prizeAfterTax(300_001_033))
        assertEquals(234_001_024L, prizeAfterTax(300_001_034))
        assertEquals(234_001_070L, prizeAfterTax(300_001_100))
    }

    @Test
    fun `after tax uses official example and rounds each tax down to ten won`() {
        assertEquals(703_000_330L, prizeAfterTax(1_000_000_000))
        assertEquals(1_419_125_346L, prizeAfterTax(2_068_843_286))
        assertEquals(39_222_056L, prizeAfterTax(50_284_386))
        assertEquals(1_693_196L, prizeAfterTax(1_693_196))
    }

    @Test
    fun `latest page follows completed draws in Seoul`() {
        assertEquals(1251, latestCompletedRound(ZonedDateTime.parse("2026-11-28T20:44:59+09:00[Asia/Seoul]")))
        assertEquals(1252, latestCompletedRound(ZonedDateTime.parse("2026-11-28T11:45:01Z")))
        assertEquals(1252, latestCompletedRound(ZonedDateTime.parse("2026-11-29T00:00:00+09:00[Asia/Seoul]")))
    }

    @Test
    fun `entry draw maps to its page and stays within the pager bounds`() {
        assertEquals(0, prizePage(1, 1252))
        assertEquals(1250, prizePage(1251, 1252))
        assertEquals(1251, prizePage(1252, 1252))
        assertEquals(1251, prizePage(1253, 1252))
    }

    @Test
    fun `checked purchases group each winning game under its own rank`() {
        assertEquals(mapOf(5 to listOf('A', 'E'), 4 to listOf('C'), 3 to listOf('F')), myPrizeGames(1251, true, listOf(purchase)))
    }

    @Test
    fun `guest missing unchecked and losing purchases have no winning rows`() {
        assertEquals(emptyMap<Int, List<Char>>(), myPrizeGames(1251, false, listOf(purchase)))
        assertEquals(emptyMap<Int, List<Char>>(), myPrizeGames(1250, true, listOf(purchase)))
        assertEquals(emptyMap<Int, List<Char>>(), myPrizeGames(1251, true, listOf(purchase.copy(checked = false))))
        assertEquals(emptyMap<Int, List<Char>>(), myPrizeGames(1251, true, listOf(purchase.copy(gameRanks = List(6) { "nowin" }))))
    }

    private val purchase = Purchase(
        round = 1251,
        date = LocalDateTime.of(2026, 11, 21, 10, 0),
        numbers = List(6) { listOf(1, 2, 3, 4, 5, 6) },
        autoCount = 6,
        manualCount = 0,
        amount = 6000,
        checked = true,
        gameRanks = listOf("rank5", "nowin", "rank4", "pending", "rank5", "rank3"),
    )
}
