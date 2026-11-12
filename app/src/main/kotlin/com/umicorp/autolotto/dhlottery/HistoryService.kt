package com.umicorp.autolotto.dhlottery

import com.umicorp.autolotto.data.Purchase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
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
 * **실패 구분**: 동행복권에 닿지 못한 실패(타임아웃·연결끊김·5xx·JSON 자리 HTML)는 전부
 * [IOException]으로 통일해 즉시 전파한다. 조용히 건너뛰면 목록이 비어 "구매 기록이 없습니다"로
 * 위장되기 때문(실제 사용자 리포트). 반면 서버가 정상 응답한 상태에서 개별 주문만 표시 불가한
 * 경우(success=false, 번호 0개 등)는 원본대로 건너뛴다 — 취소 주문 하나가 화면 전체를 죽이면 안 된다.
 *
 * 세션(로그인 쿠키 보유)은 생성자 주입. 원본은 `_auth.dio`(리다이렉트 추적 off)를 썼으므로 follow는 기본값(false).
 */
class HistoryService(private val session: DhlotterySession) {

    /** 최근 구매 내역 (최근 30일, 최대 [count]건) — 결과확인 워커용. */
    suspend fun fetchRecentPurchases(count: Int = 5): List<Purchase> =
        fetchPurchases(LocalDate.now().minusDays(30), LocalDate.now(), count)

    /**
     * [from]~[to](포함) 기간의 구매 내역. 동행복권 조회창 한도(최대 3개월)는 호출자가 지킨다 —
     * 내역 화면이 3개월 창 단위 "더 보기"로 최대 1년(서버 보관 한도)까지 거슬러 올라간다.
     */
    suspend fun fetchPurchases(from: LocalDate, to: LocalDate, count: Int = Int.MAX_VALUE): List<Purchase> = withContext(Dispatchers.IO) {
        val now = LocalDateTime.now() // 날짜 파싱 실패 시 폴백(원본 동작)
        val todayStr = DATE_FMT.format(to)
        val fromStr = DATE_FMT.format(from)

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
        ).use {
            if (it.code >= 500) throw IOException("history_list_http_${it.code}")
            it.body?.string().orEmpty()
        }

        // JSON 자리에 HTML = 세션 만료·점검 페이지. 연결 실패는 전부 IOException으로 통일해
        // 호출부가 "동행복권에 닿지 못함"을 한 가지 조건으로 판정하게 한다.
        if (!listBody.trimStart().startsWith("{")) throw IOException("history_list_not_json")

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
                ).use {
                    if (it.code >= 500) throw IOException("history_detail_http_${it.code}")
                    it.body?.string().orEmpty()
                }
                if (!detailBody.trimStart().startsWith("{")) throw IOException("history_detail_not_json")

                // `{`로 시작해도 절단·손상된 본문은 전송 실패지 "이 주문만 못 보는" 상황이 아니다.
                // JSONException으로 두면 아래 catch가 건너뛰어 다시 빈 목록으로 위장된다.
                val data = runCatching { JSONObject(detailBody) }
                    .getOrElse { throw IOException("history_detail_malformed_json") }
                    .optJSONObject("data")
                if (data == null || !data.optBoolean("success", false)) continue

                val ticket = data.getJSONObject("ticket")
                val gameDtl = ticket.optJSONArray("game_dtl") ?: JSONArray()
                val drawed = ticket.optBoolean("drawed", false)

                // 당첨번호 추출 (win_num 없으면 null — 원본의 비저장 필드).
                // 실측 정규화(에뮬레이터 1233/1234회): 미추첨이면 win_num이 0으로 채워져 오고,
                // 추첨 후엔 보너스 포함 7개에 bonus_num이 비기도 한다 → 미추첨=null, 7개면 7번째=보너스.
                val winNumRaw = ticket.optJSONArray("win_num")?.let { toIntList(it) }
                    ?.takeIf { drawed && it.all { n -> n in 1..45 } }
                val winNum = winNumRaw?.take(6)
                val bonusNum = ticket.optIntOrNull("bonus_num")?.takeIf { it in 1..45 }
                    ?: winNumRaw?.getOrNull(6)

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
            } catch (e: IOException) {
                // 연결·서버 실패를 건너뛰면 항목이 통째로 사라져 화면이 "구매 기록이 없습니다"로
                // 위장된다(실제 사용자 리포트). 어차피 전체 실패로 처리하므로 남은 항목을
                // 기다리지 않고 즉시 중단한다 — 13건 × 10초 타임아웃을 다 소진할 이유가 없다.
                throw e
            } catch (e: Exception) {
                // 서버는 정상 응답했는데 이 주문만 표시 불가(취소 주문 등) — 원본대로 건너뛴다.
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
