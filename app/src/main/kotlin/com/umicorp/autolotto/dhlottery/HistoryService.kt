package com.umicorp.autolotto.dhlottery

import com.umicorp.autolotto.data.Purchase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 동행복권 마이페이지 구매 내역 조회 (Flutter HistoryService 포트).
 *
 * 내역은 로컬 DB 없이 매번 라이브 조회한다(DESIGN §3). 머니패스는 아니지만 등수/당첨금 표시의
 * 출처이므로 원본 history_service.dart와 의미상 1:1을 유지한다.
 *
 * 흐름: 마이페이지 원장 방문(Referer용) → selectMyLotteryledger.do(목록) →
 *       "로또6/45" 항목마다 lotto645TicketDetail.do(번호+당첨결과) → Purchase 빌드.
 *
 * 세션 만료 시 JSON 자리에 HTML이 오는데, org.json 파싱이 던지는 JSONException으로 원본 Dart의
 * 암묵적 throw(String 인덱싱 실패)를 그대로 재현한다 — 목록 호출: 예외 전파 / 상세 호출: try-catch로 건너뜀.
 *
 * 세션(로그인 쿠키 보유)은 생성자 주입. 원본은 `_auth.dio`(리다이렉트 추적 off)를 썼으므로 follow는 기본값(false).
 */
class HistoryService(private val session: DhlotterySession) {

    /** 최근 구매 내역 가져오기 (최대 [count]건). */
    suspend fun fetchRecentPurchases(count: Int = 5): List<Purchase> = withContext(Dispatchers.IO) {
        val now = LocalDateTime.now()
        val thirtyDaysAgo = now.minusDays(30)
        val todayStr = DATE_FMT.format(now)
        val fromStr = DATE_FMT.format(thirtyDaysAgo)

        // 1. 마이페이지 원장 방문 (Referer용)
        session.get(session.base(ApiConstants.MYPAGE_LEDGER)).close()

        // 2. 구매 목록 조회
        val listBody = session.get(
            session.base(ApiConstants.PURCHASE_HISTORY) +
                "?srchStrDt=$fromStr&srchEndDt=$todayStr&pageNum=1&recordCountPerPage=100&_=${System.currentTimeMillis()}",
            mapOf(
                "Accept" to "application/json, text/javascript, */*; q=0.01",
                "X-Requested-With" to "XMLHttpRequest",
                "Referer" to session.base(ApiConstants.MYPAGE_LEDGER),
            ),
        ).use { it.body?.string().orEmpty() }

        // HTML(세션 만료)이면 JSONObject가 던진다 → 원본 Dart의 String 인덱싱 throw와 동일하게 전파.
        val items = JSONObject(listBody).optJSONObject("data")?.optJSONArray("list")
        val purchases = mutableListOf<Purchase>()
        if (items == null) return@withContext purchases // data/list 누락은 빈 목록(원본 `?? []`)

        for (i in 0 until items.length()) {
            val item = items.getJSONObject(i)
            if (item.optString("ltGdsNm") != "로또6/45") continue

            val round = item.optString("ltEpsdView").replace("회", "").trim().toIntOrNull() ?: 0
            val ntslOrdrNo = item.optString("ntslOrdrNo")
            val gmInfo = item.optString("gmInfo")
            val purchaseDateStr = item.optString("eltOrdrDt")
            val drawDateStr = item.optString("epsdRflDt")
            if (ntslOrdrNo.isEmpty() || gmInfo.isEmpty()) continue

            // 3. 상세 조회 (번호 + 당첨결과)
            try {
                val purchaseDt = parseDhDateTime(purchaseDateStr) ?: now
                val startDt = DATE_FMT.format(purchaseDt.minusDays(7))
                val endDt = DATE_FMT.format(purchaseDt.plusDays(7))

                val detailBody = session.get(
                    session.base(ApiConstants.TICKET_DETAIL) +
                        "?ntslOrdrNo=$ntslOrdrNo&srchStrDt=$startDt&srchEndDt=$endDt&barcd=$gmInfo",
                    mapOf(
                        "X-Requested-With" to "XMLHttpRequest",
                        "Referer" to session.base(ApiConstants.MYPAGE_LEDGER),
                    ),
                ).use { it.body?.string().orEmpty() }

                val data = JSONObject(detailBody).optJSONObject("data")
                if (data == null || !data.optBoolean("success", false)) continue

                val ticket = data.getJSONObject("ticket")
                val gameDtl = ticket.optJSONArray("game_dtl") ?: JSONArray()
                val drawed = ticket.optBoolean("drawed", false)

                // 당첨번호 추출 (win_num 없으면 null — 원본의 비저장 필드)
                val winNum = ticket.optJSONArray("win_num")?.let { toIntList(it) }
                val bonusNum = ticket.optIntOrNull("bonus_num")

                val numbers = mutableListOf<List<Int>>()
                var autoCount = 0
                var manualCount = 0
                // 게임별 당첨결과 (API가 직접 제공)
                val gameRanks = mutableListOf<String>()
                val gamePrizes = mutableListOf<Long>()

                for (g in 0 until gameDtl.length()) {
                    val game = gameDtl.getJSONObject(g)
                    val nums = game.optJSONArray("num")?.let { toIntList(it) } ?: emptyList()
                    if (nums.size == 6) {
                        numbers.add(nums)
                        val type = game.optInt("type", 3)
                        if (type == 1) manualCount++ else autoCount++

                        val apiRank = game.optInt("rank", 0)
                        val apiAmt = game.optLong("amt", 0)
                        gameRanks.add(if (drawed) rankLabel(apiRank) else "pending")
                        gamePrizes.add(apiAmt)
                    }
                }

                if (numbers.isEmpty()) continue

                // 추첨 완료 여부로 checked 판단
                val checked = drawed
                var bestRank: String? = null
                var totalPrize = 0L
                if (checked) {
                    bestRank = "nowin"
                    for (r in gameRanks) {
                        if (RANK_ORDER.indexOf(r) < RANK_ORDER.indexOf(bestRank!!)) bestRank = r
                    }
                    totalPrize = gamePrizes.sum()
                }

                purchases.add(
                    Purchase(
                        round = round,
                        date = parseDhDateTime(drawDateStr) ?: parseDhDateTime(purchaseDateStr) ?: now,
                        numbers = numbers,
                        autoCount = autoCount,
                        manualCount = manualCount,
                        amount = numbers.size * 1000,
                        checked = checked,
                        rank = bestRank,
                        prize = totalPrize,
                        gameRanks = gameRanks,
                        gamePrizes = gamePrizes,
                        winningNumbers = winNum,
                        bonusNumber = bonusNum,
                    ),
                )
                if (purchases.size >= count) break
            } catch (e: Exception) {
                // 원본은 debugPrint 후 continue (릴리스에서 제거). 상세 실패(HTML/누락)는 건너뛴다.
                continue
            }
        }

        purchases
    }

    private companion object {
        val DATE_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")
        private val COMPACT_DT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")

        // bestRank 비교 순서: 작은 인덱스가 더 좋은 등수 (원본 rankOrder 그대로).
        val RANK_ORDER = listOf("rank1", "rank2", "rank3", "rank4", "rank5", "nowin")

        /** API rank 숫자 → 코드값 (1~5=rank1~rank5, 그 외=nowin). 원본 _rankLabel. */
        fun rankLabel(rank: Int): String = if (rank in 1..5) "rank$rank" else "nowin"

        /**
         * Dart DateTime.tryParse 대응(실패 시 null). dhlottery가 쓰는 형식만 처리:
         * ISO('T') · 공백 구분 datetime · 날짜만(yyyy-MM-dd).
         * ponytail: 라이브에서 다른 형식이 나오면 패턴 추가 — 실패해도 원본처럼 now로 폴백한다.
         */
        fun parseDhDateTime(raw: String): LocalDateTime? {
            if (raw.isBlank()) return null
            runCatching { return LocalDateTime.parse(raw) }
            runCatching { return LocalDateTime.parse(raw.replace(' ', 'T')) }
            runCatching { return LocalDate.parse(raw).atStartOfDay() }                                   // yyyy-MM-dd
            runCatching { return LocalDate.parse(raw, DateTimeFormatter.BASIC_ISO_DATE).atStartOfDay() } // yyyyMMdd (Dart tryParse 허용)
            runCatching { return LocalDateTime.parse(raw, COMPACT_DT) }                                  // yyyyMMdd'T'HHmmss
            return null
        }
    }
}

/** `as int?`(없음/null → null) 대응. */
private fun JSONObject.optIntOrNull(key: String): Int? =
    if (has(key) && !isNull(key)) optInt(key) else null

/** JSONArray<int> → List<Int>. 원본 `.cast<int>()` 대응(단 즉시 변환). */
private fun toIntList(arr: JSONArray): List<Int> = (0 until arr.length()).map { arr.getInt(it) }
