package com.umicorp.autolotto.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.BackoffPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * 알람/부팅 BroadcastReceiver들.
 *
 * onReceive는 ~10초 제한이라 login+purchase(네트워크)가 초과할 수 있다 → 리시버는 즉시 **expedited
 * WorkManager OneTimeWorkRequest**를 enqueue하고 끝낸다. 실제 작업과 다음 주 자가재등록은 Worker에서.
 * (원본 android_alarm_manager_plus의 JobIntentService 모델을 네이티브 정석으로 교체 — DESIGN §3/§9.)
 */

/** 자동구매 알람(1001) → AutoPurchaseWorker enqueue. */
class AutoPurchaseReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        enqueueExpedited<AutoPurchaseWorker>(context, "auto_purchase")
    }
}

/** 결과확인 알람(1002) → CheckResultWorker enqueue. */
class CheckResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        enqueueExpedited<CheckResultWorker>(context, "check_result")
    }
}

/**
 * 부팅 완료 / 앱 업데이트(APK 교체) → 알람 일괄 복원.
 *
 * 안드로이드는 재부팅뿐 아니라 **패키지 교체(업데이트) 시에도 그 앱의 AlarmManager 알람을 취소**한다.
 * 사이드로드 업데이트 후 알람이 조용히 죽는 문제를 MY_PACKAGE_REPLACED 수신으로 즉시 재무장해 막는다.
 * (앱 실행 시에도 AppContainer.loadSettings가 rescheduleAll을 호출 — 이중 안전망.)
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED ->
                // 업데이트 직후는 Keystore가 불안정할 수 있는 창구 — 리시버 크래시 방지.
                // 재무장 실패해도 앱 실행 시 loadSettings의 rescheduleAll이 이중 안전망.
                runCatching { AlarmScheduler(context.applicationContext).rescheduleAll() }
        }
    }
}

private inline fun <reified W : androidx.work.ListenableWorker> enqueueExpedited(context: Context, uniqueName: String) {
    val request = OneTimeWorkRequestBuilder<W>()
        // 할당량 소진 시 일반 작업으로 강등(예외 없이). minSdk 31이라 expedited는 포그라운드 알림 불필요.
        .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
        // Result.retry() 간격: 15분 선형(15/30/45…). 결과 미게시·일시 네트워크 오류 커버.
        .setBackoffCriteria(BackoffPolicy.LINEAR, 15, TimeUnit.MINUTES)
        .build()
    // unique + KEEP: 알람 중복 전달·재부팅 경합 시 같은 작업이 병렬로 두 번 돌지 않게 (중복 결제 방지 1차 방어)
    WorkManager.getInstance(context.applicationContext)
        .enqueueUniqueWork(uniqueName, ExistingWorkPolicy.KEEP, request)
}
