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
