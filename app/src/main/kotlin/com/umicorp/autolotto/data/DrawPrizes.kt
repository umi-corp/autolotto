package com.umicorp.autolotto.data

data class RankPrize(val games: Int, val amount: Long)

sealed interface DrawPrizes {
    val winning: WinningResult? get() = null

    data class Published(override val winning: WinningResult, val ranks: List<RankPrize>) : DrawPrizes
    data class Pending(override val winning: WinningResult? = null) : DrawPrizes
    data object Error : DrawPrizes
}

fun prizePage(round: Int, latestRound: Int): Int = round.coerceIn(1, latestRound) - 1

fun prizeAfterTax(prize: Long): Long {
    if (prize <= 2_000_000L) return prize
    val taxable = prize - 1_000L
    val base = taxable.coerceAtMost(300_000_000L)
    val excess = taxable - base
    val incomeTax = base * 20L / 100L + excess / 100L * 30L + excess % 100L * 30L / 100L
    val localTax = incomeTax / 100L * 10L
    return prize - incomeTax / 10L * 10L - localTax
}

fun myPrizeGames(round: Int, loggedIn: Boolean, purchases: List<Purchase>): Map<Int, List<Char>> {
    if (!loggedIn) return emptyMap()
    val ranks = purchases.find { it.round == round && it.checked }?.gameRanks.orEmpty()
    return (1..5).mapNotNull { rank ->
        val games = ranks.indices.filter { ranks[it] == "rank$rank" }.map { 'A' + it }
        if (games.isEmpty()) null else rank to games
    }.toMap()
}
