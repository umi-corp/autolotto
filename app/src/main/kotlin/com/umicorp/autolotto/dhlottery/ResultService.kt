package com.umicorp.autolotto.dhlottery

import com.umicorp.autolotto.data.WinningResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * 당첨 확인 서비스 (Flutter ResultService 포트). 로그인 불필요.
 *
 * 원본은 자체 Dio(쿠키 비공유, followRedirects 기본 true)를 가졌다. 포트도 자체 세션을 기본으로 갖고,
 * 테스트는 MockWebServer 세션을 주입한다(Dart의 `ResultService([Dio? dio])` 선택 주입과 1:1).
 * 원본 Dio가 리다이렉트를 추적했으므로 두 GET은 `follow=true`.
 */
class ResultService(private val session: DhlotterySession = DhlotterySession()) {

    /**
     * 당첨번호 조회. [roundNo] null이면 최신 회차.
     * 실패(비200 / HTML / 빈 목록 / 예외) 시 null — 원본과 동일.
     */
    suspend fun getWinningNumbers(roundNo: Int? = null): WinningResult? = withContext(Dispatchers.IO) {
        try {
            // 쿠키 획득용 메인 방문
            session.get(session.baseUrl, follow = true).close()

            val url = session.base(ApiConstants.WINNING_NUMBER) +
                if (roundNo != null) "?drwNo=$roundNo" else ""

            session.get(url, follow = true).use { resp ->
                if (resp.code != 200) return@withContext null
                val body = resp.body?.string().orEmpty()
                val items = JSONObject(body).optJSONObject("data")?.optJSONArray("list")
                if (items == null || items.length() == 0) return@withContext null

                val item = items.getJSONObject(0)
                val numbers = listOf(
                    item.getInt("tm1WnNo"),
                    item.getInt("tm2WnNo"),
                    item.getInt("tm3WnNo"),
                    item.getInt("tm4WnNo"),
                    item.getInt("tm5WnNo"),
                    item.getInt("tm6WnNo"),
                ).sorted()

                WinningResult(
                    round = item.getInt("ltEpsd"),
                    numbers = numbers,
                    bonus = item.getInt("bnsWnNo"),
                    date = parseDate(item.optString("ltRflYmd", "")),
                    prize1st = item.optLong("rnk1WnAmt", 0), // Long — Int.MAX(21.4억) 초과 가능
                    prize2nd = item.optLong("rnk2WnAmt", 0),
                    prize3rd = item.optLong("rnk3WnAmt", 0),
                )
            }
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        /**
         * 내 번호 vs 당첨번호 매칭 (로컬 등수 계산).
         *
         * ⚠️ DESIGN: 앱은 표시에 이 값을 쓰지 않는다. 등수/당첨금의 출처는 dhlottery API(ticketDetail →
         * Purchase.gameRanks/gamePrizes)다. 원본 충실 포팅으로 남겨두되 결과 화면에 배선하지 말 것.
         */
        fun checkMatches(
            myNumbers: List<List<Int>>,
            winningNumbers: List<Int>,
            bonus: Int,
        ): List<MatchResult> {
            val winningSet = winningNumbers.toSet()
            val results = mutableListOf<MatchResult>()
            for (nums in myNumbers) {
                val numsSet = nums.toSet()
                val matched = numsSet.intersect(winningSet).toList().sorted()
                val count = matched.size
                val bonusMatch = numsSet.contains(bonus)

                val rank: String
                val prize: Int
                when {
                    count == 6 -> { rank = "rank1"; prize = 0 }            // 변동
                    count == 5 && bonusMatch -> { rank = "rank2"; prize = 0 } // 변동
                    count == 5 -> { rank = "rank3"; prize = 0 }            // 변동
                    count == 4 -> { rank = "rank4"; prize = 50000 }
                    count == 3 -> { rank = "rank5"; prize = 5000 }
                    else -> { rank = "nowin"; prize = 0 }
                }
                results.add(MatchResult(nums, matched, count, bonusMatch, rank, prize))
            }
            return results
        }

        /** yyyyMMdd(8자리) → yyyy-MM-dd, 그 외는 원문 그대로 (Dart _parseDate). */
        private fun parseDate(raw: String): String =
            if (raw.length == 8) "${raw.substring(0, 4)}-${raw.substring(4, 6)}-${raw.substring(6, 8)}" else raw
    }
}

/** 매칭 결과 (Flutter MatchResult 포트). 표시 비사용 — checkMatches 참조. */
data class MatchResult(
    val numbers: List<Int>,
    val matched: List<Int>,
    val matchCount: Int,
    val bonusMatch: Boolean,
    val rank: String,
    val prize: Int,
) {
    val isWinner: Boolean get() = rank != "nowin"
}
