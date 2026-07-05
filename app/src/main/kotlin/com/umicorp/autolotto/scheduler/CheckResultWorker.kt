package com.umicorp.autolotto.scheduler

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.umicorp.autolotto.data.SecureStore
import com.umicorp.autolotto.dhlottery.AuthService
import com.umicorp.autolotto.dhlottery.DhlotterySession
import com.umicorp.autolotto.dhlottery.HistoryService
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
        try {
            executeCheckResult()
        } catch (e: Exception) {
            Notifications.show(ctx, "⚠️ AutoLotto 오류", "당첨 결과 확인에 실패했습니다.", 99)
        }

        // 다음 주 알람 재등록 — 자동구매 활성 시에만
        try {
            if (store.getAutoEnabled()) AlarmScheduler(ctx).scheduleCheckResult()
        } catch (e: Exception) {
            Notifications.show(ctx, "⚠️ AutoLotto 오류", "결과확인 알람 재등록 실패: ${e.message ?: e}", 98)
        }

        return Result.success()
    }

    /** 원본 `_executeCheckResult` 1:1. */
    private suspend fun executeCheckResult() {
        if (!store.getAutoEnabled()) return

        // 당첨번호 조회 (로그인 불필요 — 자체 세션). 실패 시 null → 종료.
        val winning = ResultService().getWinningNumbers() ?: return
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
            val purchases = history.fetchRecentPurchases(count = 5)
            val purchase = purchases.firstOrNull { it.round == winning.round }
                ?: throw Exception("no_matching_purchase")

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

            Notifications.show(ctx, title, body, 2)
        } catch (e: Exception) {
            // 로그인/이력 조회 실패 시 기존처럼 당첨번호만 알림
            Notifications.show(
                ctx,
                "🎱 제 ${winning.round}회 당첨번호",
                "${winning.numbers.joinToString(", ")} + ${winning.bonus}",
                2,
            )
        }
    }
}
