package com.umicorp.autolotto.scheduler

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.umicorp.autolotto.data.SecureStore
import com.umicorp.autolotto.data.mergePurchasesByRound
import com.umicorp.autolotto.dhlottery.AuthService
import com.umicorp.autolotto.dhlottery.DhlotterySession
import com.umicorp.autolotto.dhlottery.HistoryService
import com.umicorp.autolotto.dhlottery.PurchaseService
import com.umicorp.autolotto.dhlottery.ResultService

/**
 * 당첨확인 백그라운드 작업 (원본 `_onCheckResultAlarm` + `_executeCheckResult` 포트).
 *
 * 당첨번호 조회(로그인 불필요) → 로그인 → 구매이력 조회 → 게임별 당첨/낙첨 알림(원본 문구) →
 * 끝에서 결과확인 알람(1002, 고정 토 21:00) 자가재등록.
 */
class CheckResultWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    private val ctx = applicationContext
    private val store = SecureStore(ctx)

    override suspend fun doWork(): Result {
        val lastAttempt = runAttemptCount >= MAX_ATTEMPTS - 1
        // 결과 미게시·미추첨 반영 지연·일시 네트워크 오류는 재시도(backoff 15분 선형)로 흡수하고,
        // 시도 소진 시에만 실패를 알린다 — 침묵 종료·낙첨 오보 방지.
        val done = try {
            executeCheckResult(lastAttempt)
        } catch (e: Exception) {
            if (lastAttempt) Notifications.show(ctx, "⚠️ AutoLotto 오류", "당첨 결과 확인에 실패했습니다.", 99)
            lastAttempt
        }

        // 다음 주 알람 재등록 — 자동구매 활성 시에만. 멱등이라 재시도 회차에 또 호출돼도 안전.
        try {
            if (store.getAutoEnabled()) AlarmScheduler(ctx).scheduleCheckResult()
        } catch (e: Exception) {
            Notifications.show(ctx, "⚠️ AutoLotto 오류", "결과확인 알람 재등록 실패: ${e.message ?: e}", 98)
        }

        return if (done) Result.success() else Result.retry()
    }

    /** 원본 `_executeCheckResult` 기반. @return true=완결, false=미확정(재시도 필요). */
    private suspend fun executeCheckResult(lastAttempt: Boolean): Boolean {
        if (!store.getAutoEnabled()) return true

        // 당첨번호 조회 (로그인 불필요 — 자체 세션). 실패/미게시 시 null → 재시도.
        val winning = ResultService().getWinningNumbers() ?: return false
        // 직전 추첨 회차(현재 판매 회차-1)보다 오래된 결과 = 아직 미게시 → 재시도. 지난 회차 재통지 방지.
        if (winning.round < PurchaseService.getCurrentRound() - 1) return false
        val winningLine = "당첨번호: ${winning.numbers.joinToString(", ")} + ${winning.bonus}"

        // 로그인 → 구매이력 조회 → 매칭 결과 알림
        try {
            val cred = store.getCredentials()
            val userId = cred.userId
            val password = cred.password
            if (userId == null || password == null) throw Exception("no_credentials")

            val session = DhlotterySession()
            val auth = AuthService(session)
            auth.login(userId, password)

            val history = HistoryService(session)
            // 회차당 주문이 여러 건일 수 있음(예약 + 즉시/추가 구매) — 첫 주문만 보면 나머지
            // 게임의 당첨이 누락돼 낙첨 오보가 난다. 병합해 회차 전체 게임으로 판정.
            // count=10 근거: 온라인 주간 한도 5게임 → 회차당 주문 최대 5건(주문당 ≥1게임),
            // 결과확인은 판매정지 창(토 21:00~) 내 실행이라 대상 회차 주문이 항상 최신이다.
            val purchases = mergePurchasesByRound(history.fetchRecentPurchases(count = 10))
            val purchase = purchases.firstOrNull { it.round == winning.round }
                ?: throw Exception("no_matching_purchase")
            // 추첨 결과가 티켓에 아직 반영되지 않았으면(drawed=false, gameRanks=pending)
            // '낙첨'으로 오보하지 않고 재시도한다.
            if (!purchase.checked) throw Exception("not_drawn_yet")

            // 게임별 결과 텍스트 생성
            val rankNames = mapOf(
                "rank1" to "1등", "rank2" to "2등", "rank3" to "3등",
                "rank4" to "4등", "rank5" to "5등", "nowin" to "낙첨",
            )

            val gameLines = purchase.numbers.mapIndexed { i, nums ->
                val numsStr = nums.joinToString(",")
                val rank = purchase.gameRanks?.getOrNull(i) ?: "nowin"
                val rankText = rankNames[rank] ?: "낙첨"
                "${'A' + i}: $numsStr → $rankText"
            }

            val isWinner = purchase.rank != null && purchase.rank != "nowin"
            val title = if (isWinner) "🎉 제 ${winning.round}회 당첨!!!" else "😔 제 ${winning.round}회 낙첨..."

            var body = "$winningLine\n\n${gameLines.joinToString("\n")}"
            if (isWinner && purchase.prize > 0) {
                body += "\n\n총 당첨금: ₩${Notifications.formatThousands(purchase.prize)}"
            }

            Notifications.show(ctx, title, body, 2, tab = Notifications.TAB_HISTORY)
            return true
        } catch (e: Exception) {
            // 미구매(no_matching_purchase)는 재시도 무의미 → 즉시 당첨번호만 알리고 완결(기존 폴백).
            // 그 외(미추첨·로그인·이력 조회 실패)는 재시도하고, 마지막 시도에만 같은 폴백을 보낸다.
            if (e.message != "no_matching_purchase" && !lastAttempt) return false
            Notifications.show(
                ctx,
                "🎱 제 ${winning.round}회 당첨번호",
                "${winning.numbers.joinToString(", ")} + ${winning.bonus}",
                2,
                tab = Notifications.TAB_HISTORY,
            )
            return true
        }
    }

    private companion object {
        /** 총 시도 횟수(최초 1 + 재시도 3) — 토 21:00 기준 15분 선형 backoff로 ~22:30까지 커버. */
        const val MAX_ATTEMPTS = 4
    }
}
