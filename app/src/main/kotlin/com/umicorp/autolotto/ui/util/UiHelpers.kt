package com.umicorp.autolotto.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.umicorp.autolotto.R
import java.util.Locale

/**
 * UI 헬퍼 (원본 `lib/utils/ui_helpers.dart` 포트).
 *
 * - 색/숫자 포맷은 일반 함수(컴포지션 불필요).
 * - 로컬라이즈가 필요한 헬퍼는 `@Composable`(stringResource 사용) — 백그라운드(Worker)에선
 *   쓰지 않는다. 알림 문구는 하드코딩 한국어(Notifications.kt)다.
 */

/** 로또 번호 공 색상 (1~10 노랑, 11~20 파랑, 21~30 빨강, 31~40 회색, 41~45 초록). 유일한 하드코딩 색 예외(브랜드 공). */
fun ballColor(n: Int): Color = when {
    n <= 10 -> Color(0xFFFBC400)
    n <= 20 -> Color(0xFF69C8F2)
    n <= 30 -> Color(0xFFFF7272)
    n <= 40 -> Color(0xFFAAAAAA)
    else -> Color(0xFFB0D840)
}

/** 천 단위 콤마 포맷. 당첨금은 Long(Int.MAX 초과 가능) — 원본 `formatNumber`의 음수 처리까지 `%,d`가 대신한다. */
fun formatNumber(n: Long): String = String.format(Locale.US, "%,d", n)

/** Int 잔액/소액용 오버로드 (잔액 `balance`는 Int). */
fun formatNumber(n: Int): String = formatNumber(n.toLong())

/** rank 코드값 → 로컬라이즈 문자열 (원본 `localizedRank`). */
@Composable
fun localizedRank(rankCode: String): String = when (rankCode) {
    "rank1" -> stringResource(R.string.rank1st)
    "rank2" -> stringResource(R.string.rank2nd)
    "rank3" -> stringResource(R.string.rank3rd)
    "rank4" -> stringResource(R.string.rank4th)
    "rank5" -> stringResource(R.string.rank5th)
    "nowin" -> stringResource(R.string.statusNoWin)
    "pending" -> stringResource(R.string.statusPending)
    else -> rankCode
}

/** 로컬라이즈된 요일명 리스트 (월~일) — 원본 `localizedDayNames`. */
@Composable
fun localizedDayNames(): List<String> = listOf(
    stringResource(R.string.dayMon),
    stringResource(R.string.dayTue),
    stringResource(R.string.dayWed),
    stringResource(R.string.dayThu),
    stringResource(R.string.dayFri),
    stringResource(R.string.daySat),
    stringResource(R.string.daySun),
)

/**
 * 자동 구매 스케줄 텍스트 (원본 `formatPurchaseScheduleL10n`).
 * day는 1=월 .. 7=일. weeklySchedule 템플릿("매주 {day}요일 {time}")에 짧은 요일명 + HH:mm을 넣는다.
 */
@Composable
fun formatPurchaseSchedule(day: Int, hour: Int, minute: Int): String {
    val time = String.format(Locale.US, "%02d:%02d", hour, minute)
    return stringResource(R.string.weeklySchedule, localizedDayNames()[day - 1], time)
}
