package com.umicorp.autolotto.scheduler

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.umicorp.autolotto.data.SecureStore
import com.umicorp.autolotto.dhlottery.AuthService
import com.umicorp.autolotto.dhlottery.DhlotterySession
import com.umicorp.autolotto.dhlottery.PurchaseService
import org.json.JSONArray

/**
 * 자동구매 백그라운드 작업 (원본 `_onAutoPurchaseAlarm` + `_executeAutoPurchase` 포트).
 *
 * SecureStore에서 자격증명·설정·수동번호를 직접 읽고(원본 백그라운드 isolate 직접읽기 패턴 유지),
 * 로그인 → 구매 → 성공/실패 알림(원본 문구 그대로) → 끝에서 다음 주 알람 자가재등록.
 *
 * 네트워크는 Worker(코루틴, ~10분 한도)에서 수행 — onReceive 10초 제한 회피.
 */
class AutoPurchaseWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    private val ctx = applicationContext
    private val store = SecureStore(ctx)

    override suspend fun doWork(): Result {
        // 1) 실제 자동구매 (원본 _onAutoPurchaseAlarm의 try/catch + 메시지 매핑)
        try {
            executeAutoPurchase()
        } catch (e: Exception) {
            val msg = e.message ?: ""
            val body = when {
                msg.contains("[login]") -> "로그인에 실패했습니다. 아이디/비밀번호를 확인해주세요."
                // API 응답 메시지 그대로 전달 (주간구매금액 초과 등)
                msg.contains("구매 실패:") -> msg.replace("[purchase] ", "")
                else -> "자동 구매에 실패했습니다. 앱을 열어 상태를 확인해주세요."
            }
            Notifications.show(ctx, "⚠️ AutoLotto 오류", body, 99)
        }

        // 2) 다음 주 알람 재등록 (one-shot 체인) — 자동구매 활성 시에만
        try {
            if (store.getAutoEnabled()) AlarmScheduler(ctx).scheduleAutoPurchase()
        } catch (e: Exception) {
            Notifications.show(ctx, "⚠️ AutoLotto 오류", "알람 재등록에 실패했습니다.", 98)
        }

        return Result.success()
    }

    /** 원본 `_executeAutoPurchase` 1:1. 실패 시 `[step] 메시지`로 감싸 던진다(doWork의 매핑이 사용). */
    private suspend fun executeAutoPurchase() {
        var step = "init"
        try {
            step = "read_credentials"
            val cred = store.getCredentials()
            val userId = cred.userId
            val password = cred.password
            val autoEnabled = store.getAutoEnabled()
            val games = store.getAutoGames()

            if (!autoEnabled || userId == null || password == null) return
            if (games == 0) return

            step = "parse_numbers"
            val manualJson = store.getManualNumbers()
            val manualNumbers = mutableListOf<List<Int>>()
            var autoGames = 0
            try {
                val parsed = JSONArray(manualJson)
                for (i in 0 until parsed.length()) {
                    if (parsed.isNull(i)) continue                 // 미설정 슬롯 — 스킵
                    val g = parsed.optJSONArray(i) ?: continue
                    if (g.length() > 0) {                          // 수동 게임
                        manualNumbers.add((0 until g.length()).map { g.getInt(it) })
                    } else {                                       // 빈 배열 = 자동 게임
                        autoGames++
                    }
                }
            } catch (e: Exception) {
                manualNumbers.clear()
                autoGames = games                                  // 파싱 실패 시 전부 자동(원본 폴백)
            }

            if (autoGames == 0 && manualNumbers.isEmpty()) return

            step = "login"
            val session = DhlotterySession()
            val auth = AuthService(session)
            auth.login(userId, password)

            step = "purchase"
            val purchaseService = PurchaseService(auth, session)
            try {
                val result = purchaseService.purchase(autoGames = autoGames, manualNumbers = manualNumbers)

                // 구매 후 잔액 체크 (실패 무시 — 원본 catch (_) {})
                runCatching {
                    val postBalance = auth.getBalance()
                    BalanceAlert.checkAndNotify(ctx, postBalance)
                }

                step = "notify"
                val numbersText = result.numbers.mapIndexed { idx, nums ->
                    "${'A' + idx}: ${nums.joinToString(",")}"
                }.joinToString("\n")

                Notifications.show(
                    ctx,
                    "🎰 로또 자동 구매 완료!",
                    "제 ${result.round}회 · ${result.totalGames}게임\n$numbersText",
                    1,
                )
            } catch (purchaseError: Exception) {
                throw Exception(purchaseError.message ?: "$purchaseError")
            }
        } catch (e: Exception) {
            throw Exception("[$step] ${e.message ?: e}")
        }
    }
}
