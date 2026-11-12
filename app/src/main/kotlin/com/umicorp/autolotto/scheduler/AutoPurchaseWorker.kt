package com.umicorp.autolotto.scheduler

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.umicorp.autolotto.data.SecureStore
import com.umicorp.autolotto.dhlottery.AuthService
import com.umicorp.autolotto.dhlottery.DhlotterySession
import com.umicorp.autolotto.dhlottery.PurchaseService
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import java.io.IOException

/**
 * 구매 직렬화 락 — 예약 워커와 즉시 구매(AppContainer)가 같은 앱 프로세스에서 공유.
 * "자격증명 읽기~구매 실행~회차 기록"이 임계구역: 기록 전에 풀면 상대가 이전 회차 값을 읽는다.
 * 잔액 조회·알림은 락 밖.
 */
object PurchaseLock {
    val mutex = Mutex()
}

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

    // lazy: 생성(Keystore 접근) 실패가 doWork try 안에서 잡혀 재시도로 흡수되게 (crosscheck R2).
    private val store by lazy { SecureStore(ctx) }

    override suspend fun doWork(): Result {
        var result: Result = Result.success()

        // 1) 실제 자동구매 (원본 _onAutoPurchaseAlarm의 try/catch + 메시지 매핑)
        try {
            executeAutoPurchase()
        } catch (e: Exception) {
            val msg = e.message ?: ""
            // [purchase] 실패는 서버가 요청을 이미 처리했을 수 있어 재전송 금지(중복 결제 방지) → 즉시 알림.
            // 그 이전 단계(자격증명·로그인 등)는 일시 오류일 수 있어 재시도하고, 마지막 시도에만 알린다.
            val ambiguous = msg.startsWith("[purchase]") || msg.startsWith("[notify]")
            if (!ambiguous && runAttemptCount < MAX_ATTEMPTS - 1) {
                result = Result.retry()
            } else {
                val body = autoPurchaseFailureBody(msg, e.isConnectivityFailure())
                Notifications.show(ctx, "⚠️ AutoLotto 오류", body, 99)
            }
        }

        // 2) 다음 주 알람 재등록 (one-shot 체인) — 자동구매 활성 시에만. 멱등이라 재시도 회차에 또 호출돼도 안전.
        try {
            if (store.getAutoEnabled()) AlarmScheduler(ctx).scheduleAutoPurchase()
        } catch (e: Exception) {
            Notifications.show(ctx, "⚠️ AutoLotto 오류", "알람 재등록에 실패했습니다.", 98)
        }

        return result
    }

    /** 원본 `_executeAutoPurchase` 1:1. 실패 시 `[step] 메시지`로 감싸 던진다(doWork의 매핑이 사용). */
    private suspend fun executeAutoPurchase() {
        var step = "init"
        try {
            val session = DhlotterySession()
            val auth = AuthService(session)

            // 자격증명 읽기~회차 기록 = 즉시 구매·수동 로그인(계정 전환 커밋)과 공유하는
            // 임계구역(PurchaseLock). Worker 재실행·중복 알람·홈 즉시 구매·로그인 경합에도
            // 같은 회차를 두 번 사거나 기록이 다른 계정으로 어긋나지 않는다.
            step = "read_credentials"
            val result = PurchaseLock.mutex.withLock {
                val cred = store.getCredentials()
                val userId = cred.userId
                val password = cred.password
                val autoEnabled = store.getAutoEnabled()
                val games = store.getAutoGames()

                if (!autoEnabled || userId == null || password == null) return
                if (games == 0) return

                step = "round_guard"
                val currentRound = PurchaseService.getCurrentRound()
                if (store.getLastPurchasedRound() >= currentRound) return

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
                auth.login(userId, password)

                step = "purchase"
                val purchaseService = PurchaseService(auth, session)
                val r = try {
                    purchaseService.purchase(autoGames = autoGames, manualNumbers = manualNumbers)
                } catch (purchaseError: Exception) {
                    // cause 보존 — 알림 문구가 연결 실패와 서버 거절을 구분하는 근거다.
                    throw Exception(purchaseError.message ?: "$purchaseError", purchaseError)
                }

                // 성공 즉시 회차+계정 기록(commit) — 이후 재실행은 round_guard가 차단.
                // ponytail: 서버 처리~기록 사이 찰나에 킬되는 창은 남음 — 완전 차단은 구매내역 대조 필요.
                store.setLastPurchasedRound(r.round)
                store.setLastPurchaseOwner(userId)
                r
            }

            // 구매 후 잔액 체크 (락 밖, 실패 무시 — 원본 catch (_) {}).
            // 조회 실패(null)면 알림 없음 — 0으로 위장하면 허위 "잔액 부족"이 나가고 그날 진짜 알림이 막힌다.
            runCatching {
                auth.getBalance()?.let { BalanceAlert.checkAndNotify(ctx, it) }
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
                tab = Notifications.TAB_HISTORY,
            )
        } catch (e: Exception) {
            throw Exception("[$step] ${e.message ?: e}", e)  // cause 보존 — 알림 문구 판정에 쓴다
        }
    }

    private companion object {
        /** 총 시도 횟수(최초 1 + 재시도 2). 간격은 SchedulerReceivers의 backoff(15분 선형). */
        const val MAX_ATTEMPTS = 3
    }
}

/**
 * 원인 사슬에 [IOException]이 있으면 "동행복권에 닿지 못한 실패"(타임아웃·연결끊김·5xx·HTML).
 * step 라벨(`[login]` 등)만으로는 서버가 아픈 것과 자격증명이 틀린 것이 구분되지 않는다.
 * ponytail: cause 사이클 방어로 8단계에서 끊는다 — 워커가 무한루프에 빠지느니 오분류가 낫다.
 */
fun Throwable.isConnectivityFailure(): Boolean =
    generateSequence(this) { it.cause }.take(8).any { it is IOException }

/**
 * 자동구매 실패 알림 본문(순수). [connectivity] = [isConnectivityFailure] 판정 결과.
 *
 * 연결 실패를 "아이디/비밀번호를 확인해주세요"로 통보하면 사용자는 멀쩡한 계정을 의심하며
 * 손을 놓는다(실제 리포트). 그렇다고 "동행복권 장애"라 단정하지도 않는다 — 같은 타임아웃이
 * 사용자 폰이 오프라인일 때도 나므로, 확인 가능한 두 가지를 모두 제시한다.
 *
 * [connectivity]는 **`[login]` 단계에만** 적용한다. `[purchase]` 이후의 IOException은 구매가
 * 이미 접수됐을 수 있어 "연결 실패"로 뭉뚱그리면 안 되고, 기존 "앱을 열어 확인" 안내가 맞다.
 */
fun autoPurchaseFailureBody(msg: String, connectivity: Boolean): String = when {
    msg.contains("[login]") && connectivity ->
        "자동 구매 중 동행복권에 연결하지 못했습니다. 네트워크 상태와 동행복권 사이트의 점검 여부를 확인해주세요."
    msg.contains("[login]") -> "로그인에 실패했습니다. 아이디/비밀번호를 확인해주세요."
    // API 응답 메시지 그대로 전달 (주간구매금액 초과 등)
    msg.contains("구매 실패:") -> msg.replace("[purchase] ", "")
    else -> "자동 구매에 실패했습니다. 앱을 열어 상태를 확인해주세요."
}
