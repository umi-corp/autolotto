package com.umicorp.autolotto.data

/**
 * 당첨번호 결과 (Flutter ResultService.WinningResult 포트).
 *
 * result.dart의 Hive `WinningNumbers`와 필드가 동일하므로 그 순수 모델까지 겸한다
 * (로컬 DB 생략 — DESIGN §3).
 *
 * date는 이미 "yyyy-MM-dd"로 포맷된 문자열(ResultService.parseDate 결과).
 */
data class WinningResult(
    val round: Int,
    val numbers: List<Int>,
    val bonus: Int,
    val date: String,
    // 당첨금은 Long — 1등 당첨금이 Int.MAX(약 21.4억)을 초과할 수 있다(예: 26.7억).
    val prize1st: Long = 0,
    val prize2nd: Long = 0,
    val prize3rd: Long = 0,
)
