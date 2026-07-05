package com.umicorp.autolotto.dhlottery

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * 로또 6/45 구매 서비스 (Flutter PurchaseService 포트).
 *
 * 머니패스이므로 원본 purchase_service.dart와 의미상 1:1을 유지한다. GATE A 델타(§9) 반영:
 * - 회차계산은 KST 고정(`Asia/Seoul`), 일요일 `(6-weekday)%7` 음수 함정 → `Math.floorMod`.
 * - 추첨일은 회차에서 단일소스화(`firstRound + (round-1)주`)해 토 20:45 이후 회차/추첨일 불일치 제거.
 * - execBuy는 `postForm`(모든 값 문자열화).
 *
 * AuthService(로그인 게이트) / DhlotterySession(공유 세션·HTTP)은 생성자 주입.
 */
class PurchaseService(
    private val auth: AuthService,
    private val session: DhlotterySession,
) {

    /**
     * 로또 6/45 구매 실행.
     * @param autoGames 자동 게임 수
     * @param manualNumbers 수동 번호 리스트 (예: [[1,2,3,4,5,6], ...])
     */
    suspend fun purchase(
        autoGames: Int,
        manualNumbers: List<List<Int>> = emptyList(),
    ): PurchaseResult = withContext(Dispatchers.IO) {
        if (!auth.isLoggedIn) throw DhlotteryException("로그인이 필요합니다.")

        val totalGames = manualNumbers.size + autoGames
        if (totalGames < 1 || totalGames > 5) {
            throw DhlotteryException("게임 수는 1~5개여야 합니다. (현재: $totalGames)")
        }

        // 1. 구매 페이지 방문 (Dart: 리다이렉트 추적 안 함 → follow=false 기본값)
        session.get(session.base(ApiConstants.MAIN)).close()
        session.get(session.ol(ApiConstants.GAME645)).close()

        // 2. Direct IP 획득 (readySocket POST). JSON 자리에 HTML = 세션 만료 센티넬.
        val readyBody = session.post(session.ol(ApiConstants.READY_SOCKET), "")
            .use { it.body?.string().orEmpty() }
        if (!readyBody.trimStart().startsWith("{")) {
            throw DhlotteryException("구매 실패: 세션 만료 (readySocket HTML). 재로그인 필요.")
        }
        val directIp = JSONObject(readyBody).optString("ready_ip", "")
        if (directIp.isEmpty()) {
            throw DhlotteryException("구매 실패: Direct IP 없음. 응답: $readyBody")
        }

        // 3. 회차, 날짜 계산 (추첨일은 회차에서 단일소스화)
        val round = getCurrentRound()
        val dates = getDrawDates(round)
        val drawDateStr = DATE_FMT.format(dates.drawDate)
        val payLimitStr = DATE_FMT.format(dates.payLimitDate)

        // 4. 구매 요청 (execBuy). postForm으로 모든 값 문자열화.
        val param = buildParam(autoGames, manualNumbers)
        val buyData = mapOf(
            "round" to round.toString(),
            "direct" to directIp,
            "nBuyAmount" to (1000 * totalGames).toString(),
            "param" to param,
            "ROUND_DRAW_DATE" to drawDateStr,
            "WAMT_PAY_TLMT_END_DT" to payLimitStr,
            "gameCnt" to totalGames.toString(),
            "saleMdaDcd" to "10",
        )
        val buyBody = session.postForm(
            session.ol(ApiConstants.EXEC_BUY),
            buyData,
            mapOf(
                "Referer" to session.ol(ApiConstants.GAME645),
                "Origin" to session.olottoUrl,
            ),
        ).use { it.body?.string().orEmpty() }

        // API가 JSON 대신 HTML을 반환하면 로그인 세션 만료
        if (!buyBody.trimStart().startsWith("{")) {
            throw DhlotteryException("구매 실패: 세션 만료 (HTML 응답). 로그인을 다시 시도합니다.")
        }
        val response = JSONObject(buyBody)
        val result = response.optJSONObject("result") ?: JSONObject()
        val resultCode = optStrOrNull(result, "resultCode")
        if (resultCode != "100") {
            val msg = optStrOrNull(result, "resultMsg") ?: "알 수 없는 오류"
            throw DhlotteryException("구매 실패: $msg")
        }

        // 5. 번호 추출
        val arrGame = result.optJSONArray("arrGameChoiceNum")
        val lines = if (arrGame == null) emptyList<Any?>() else (0 until arrGame.length()).map { arrGame.get(it) }
        val numbers = parseNumbersFromResponse(lines)

        PurchaseResult(
            round = round,
            numbers = numbers,
            autoCount = autoGames,
            manualCount = manualNumbers.size,
            amount = totalGames * 1000,
        )
    }

    /** 추첨일·지급기한 (회차 단일소스). */
    data class DrawDates(val drawDate: LocalDate, val payLimitDate: LocalDate)

    companion object {
        private val KST = ZoneId.of("Asia/Seoul")

        /** 로또 1회차 기준일 (2002-12-07, 토요일). */
        private val FIRST_ROUND: LocalDate = LocalDate.of(2002, 12, 7)
        private val DATE_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy/MM/dd")

        /**
         * 현재 판매 중인 회차 계산 (KST 고정).
         *
         * Dart `%`는 항상 음이 아니지만 Kotlin/Java `%`는 부호가 피제수를 따른다 → 일요일에
         * `(6 - weekday) % 7`이 음수가 되어 회차가 어긋난다. `Math.floorMod`로 비음수 모듈로 강제.
         */
        fun getCurrentRound(now: ZonedDateTime = ZonedDateTime.now(KST)): Int {
            val today = now.toLocalDate()
            val dow = today.dayOfWeek.value // 월=1 .. 일=7 (Dart weekday와 동일)
            val daysUntilSaturday = Math.floorMod(6 - dow, 7).toLong()
            val thisSaturday = today.plusDays(daysUntilSaturday)

            val daysDiff = ChronoUnit.DAYS.between(FIRST_ROUND, thisSaturday)
            val weeksPassed = daysDiff / 7
            var round = (1 + weeksPassed).toInt()

            // 토요일 20:45(추첨시간) 이후면 다음 회차 표시
            if (dow == DayOfWeek.SATURDAY.value) {
                val drawTime = today.atTime(20, 45).atZone(KST)
                if (now.isAfter(drawTime)) round += 1
            }
            return round
        }

        /**
         * 추첨일·지급기한 계산. 추첨일을 회차에서 단일소스화한다(토 20:45 이후 회차/추첨일 불일치 제거).
         * FIRST_ROUND가 토요일이므로 drawDate는 항상 해당 회차의 토요일.
         */
        fun getDrawDates(round: Int): DrawDates {
            val drawDate = FIRST_ROUND.plusWeeks((round - 1).toLong())
            val payLimitDate = drawDate.plusDays(365)
            return DrawDates(drawDate, payLimitDate)
        }

        /**
         * 구매 파라미터 빌드 (org.json JSONArray 문자열).
         * 수동: genType="1", arrGameChoiceNum=정렬 후 2자리 0패딩 콤마결합 / 자동: genType="0", arrGameChoiceNum=null.
         * 슬롯은 A..E 최대 5개. (원본의 `alpabet` 오타 키 그대로 유지.)
         */
        fun buildParam(autoGames: Int, manualNumbers: List<List<Int>>): String {
            val slotNames = listOf("A", "B", "C", "D", "E")
            val arr = JSONArray()
            var slotIdx = 0

            // 수동 번호
            for (numbers in manualNumbers) {
                if (slotIdx >= slotNames.size) break
                val joined = numbers.sorted().joinToString(",") { it.toString().padStart(2, '0') }
                arr.put(
                    JSONObject()
                        .put("genType", "1")
                        .put("arrGameChoiceNum", joined)
                        .put("alpabet", slotNames[slotIdx]),
                )
                slotIdx++
            }

            // 자동 번호 (arrGameChoiceNum=null → JSONObject.NULL이라야 `null`로 직렬화)
            for (i in 0 until autoGames) {
                if (slotIdx >= slotNames.size) break
                arr.put(
                    JSONObject()
                        .put("genType", "0")
                        .put("arrGameChoiceNum", JSONObject.NULL)
                        .put("alpabet", slotNames[slotIdx]),
                )
                slotIdx++
            }

            return arr.toString()
        }

        /**
         * API 응답에서 번호 추출. 포맷: "A|01|02|04|27|39|443" (앞 2글자=슬롯, 마지막 1글자=모드).
         * substring(2, len-1)로 앞 "A|"와 끝 모드 1글자를 떼어 6개 번호만 남긴다.
         */
        fun parseNumbersFromResponse(arrGameChoiceNum: List<Any?>): List<List<Int>> {
            val allNumbers = mutableListOf<List<Int>>()
            for (line in arrGameChoiceNum) {
                val str = line.toString()
                if (str.length < 3) continue
                runCatching {
                    val nums = str.substring(2, str.length - 1).split("|").map { it.toInt() }
                    if (nums.size == 6 && nums.all { it in 1..45 }) {
                        allNumbers.add(nums)
                    }
                } // 파싱 실패는 원본처럼 무시하고 다음 줄로 (debugPrint은 생략)
            }
            return allNumbers
        }

        /** org.json 값을 Dart 대응(없음/JSON null → null)으로 변환. */
        private fun optStrOrNull(o: JSONObject, key: String): String? {
            val v = o.opt(key)
            return if (v == null || v == JSONObject.NULL) null else v.toString()
        }
    }
}

/** 구매 결과 모델 (Flutter PurchaseResult 포트). */
data class PurchaseResult(
    val round: Int,
    val numbers: List<List<Int>>,
    val autoCount: Int,
    val manualCount: Int,
    val amount: Int,
) {
    val totalGames: Int get() = autoCount + manualCount
}
