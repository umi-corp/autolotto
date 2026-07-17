package com.umicorp.autolotto.data

import java.time.LocalDateTime

/**
 * 구매 내역 1건 (Flutter Purchase 모델의 Hive 제거 순수 포트).
 *
 * 로컬 DB 없이 dhlottery에서 매번 라이브 조회하므로 영속 애너테이션이 없다(DESIGN §3).
 * 등수(gameRanks)·당첨금(gamePrizes)은 dhlottery API(ticketDetail)가 출처다.
 * rank 코드값: rank1..rank5 / nowin / pending.
 *
 * 원본은 checked/rank/prize/gameRanks/gamePrizes/winningNumbers/bonusNumber가 생성 후 대입되는
 * var였으나, 포트는 HistoryService가 한 번에 완성해 넘기므로 전부 val(필요 시 copy()).
 * winningNumbers·bonusNumber는 원본에서 Hive 비저장(API 응답 전용)이던 필드.
 */
data class Purchase(
    val round: Int,
    val date: LocalDateTime,
    val numbers: List<List<Int>>,
    val autoCount: Int,
    val manualCount: Int,
    val amount: Int,
    val checked: Boolean = false,
    val rank: String? = null,
    val prize: Long = 0, // 당첨금 Long — Int.MAX(21.4억) 초과 가능
    val gameRanks: List<String>? = null,
    val gamePrizes: List<Long>? = null,
    val winningNumbers: List<Int>? = null,
    val bonusNumber: Int? = null,
) {
    val totalGames: Int get() = autoCount + manualCount
}

/**
 * 같은 회차의 주문 여러 건을 1건으로 병합 — 즉시/추가 구매 도입으로 회차당 주문이 여러 건일 수
 * 있는데, 내역 카드와 결과확인 알림은 "회차당 1건"을 가정한다(회차별 그루핑이 설계 의도).
 * 게임·게임별 결과는 목록 순서를 보존하며 이어 붙이고, 등수는 최고·당첨금은 합산한다.
 * 목록 순서(dhlottery 응답 순)와 회차 간 정렬은 호출자가 관리한다.
 */
fun mergePurchasesByRound(purchases: List<Purchase>): List<Purchase> =
    purchases.groupBy { it.round }.map { (_, group) -> group.reduce(::mergeTwo) }

private fun mergeTwo(a: Purchase, b: Purchase): Purchase = a.copy(
    numbers = a.numbers + b.numbers,
    autoCount = a.autoCount + b.autoCount,
    manualCount = a.manualCount + b.manualCount,
    amount = a.amount + b.amount,
    checked = a.checked && b.checked,
    rank = betterRank(a.rank, b.rank),
    prize = a.prize + b.prize,
    // 게임별 결과는 numbers 인덱스와 정렬돼야 한다 — 한쪽만 null이면 pending/0으로 패딩.
    gameRanks = concatAligned(a.gameRanks, a.numbers.size, b.gameRanks, b.numbers.size, "pending"),
    gamePrizes = concatAligned(a.gamePrizes, a.numbers.size, b.gamePrizes, b.numbers.size, 0L),
    winningNumbers = a.winningNumbers ?: b.winningNumbers,
    bonusNumber = a.bonusNumber ?: b.bonusNumber,
)

private fun <T> concatAligned(a: List<T>?, aSize: Int, b: List<T>?, bSize: Int, pad: T): List<T>? =
    if (a == null && b == null) null
    else (a ?: List(aSize) { pad }) + (b ?: List(bSize) { pad })

/** 작은 인덱스 = 좋은 등수 (HistoryService.RANK_ORDER와 동일 순서). null은 상대 값 채택. */
private val RANK_MERGE_ORDER = listOf("rank1", "rank2", "rank3", "rank4", "rank5", "nowin")

private fun betterRank(a: String?, b: String?): String? = when {
    a == null -> b
    b == null -> a
    RANK_MERGE_ORDER.indexOf(a) <= RANK_MERGE_ORDER.indexOf(b) -> a
    else -> b
}
