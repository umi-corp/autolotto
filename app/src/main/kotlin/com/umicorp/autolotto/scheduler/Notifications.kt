package com.umicorp.autolotto.scheduler

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.graphics.BitmapFactory
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.umicorp.autolotto.R
import com.umicorp.autolotto.data.SecureStore
import java.time.LocalDate
import java.util.Locale

/**
 * 백그라운드 알림 (원본 `SchedulerService.showNotification` 포트).
 *
 * 문구는 전부 하드코딩 한국어 — 백그라운드(Worker)에는 BuildContext/리소스 로케일이 없어서
 * 원본 `scheduler_service.dart`/`balance_alert_service.dart`와 동일하게 한국어로 고정한다(DESIGN §3).
 * 채널은 원본 `autolotto_channel`("AutoLotto 알림", importance HIGH)과 1:1.
 *
 * 알림 ID(원본과 동일): 자동구매 성공=1, 결과확인=2, 잔액부족=50, 재등록실패=98, 오류=99.
 */
object Notifications {

    /** 브랜드 틸 — 작은 아이콘·앱 이름 액센트 색상 (아이콘 아트 자체는 기존 ic_launcher 유지). */
    private const val BRAND_TEAL = 0xFF0F8B8D.toInt()

    private const val CHANNEL_ID = "autolotto_channel"
    private const val CHANNEL_NAME = "AutoLotto 알림"
    private const val CHANNEL_DESC = "로또 구매/당첨 알림"

    /**
     * 알림 표시. 본문이 여러 줄이므로 BigTextStyle로 전체 노출(원본 flutter_local_notifications가
     * 멀티라인 body를 그대로 보여준 동작과 동등).
     *
     * POST_NOTIFICATIONS(API33+) 미허용 시 시스템이 조용히 무시한다 — 권한 요청 UI는 다음 슬라이스(설정/홈).
     */
    fun show(context: Context, title: String, body: String, id: Int) {
        ensureChannel(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher) // 원본도 @mipmap/ic_launcher를 알림 아이콘으로 사용
            .setColor(BRAND_TEAL) // 작은 아이콘/앱 이름 액센트에 브랜드 틸 적용
            .setColorized(false)
            .setLargeIcon(BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher))
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(id, notification)
    }

    private fun ensureChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH).apply {
            description = CHANNEL_DESC
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /** 천단위 콤마 — 원본 `_formatPrize`/`_formatNumber`의 수동 콤마 삽입과 동일(로케일 고정 콤마). */
    fun formatThousands(n: Long): String = String.format(Locale.US, "%,d", n)
}

/**
 * 잔액 부족 체크·알림 (원본 `BalanceAlertService.checkAndNotify` 포트).
 *
 * 임계값 비교 + 같은 날 중복 방지(last_date) + 부족 시 알림. 백그라운드(구매 후 잔액 체크)와
 * 앱 내(자동로그인 직후) 양쪽에서 호출된다 — 원본과 동일.
 */
object BalanceAlert {

    private const val NOTIF_ID = 50

    /**
     * 잔액을 체크하고 임계값 이하면 알림(같은 날 1회). 실패는 원본처럼 조용히 삼킨다.
     * @param balance 현재 잔액
     * @param enabled null이면 SecureStore에서 읽음
     * @param threshold null이면 SecureStore에서 읽음
     */
    fun checkAndNotify(context: Context, balance: Int, enabled: Boolean? = null, threshold: Int? = null) {
        try {
            val store = SecureStore(context)
            val isEnabled = enabled ?: store.getBalanceAlertEnabled()
            if (!isEnabled) return

            val thresh = threshold ?: store.getBalanceAlertThreshold()
            if (balance > thresh) return

            // 같은 날 중복 알림 방지 (yyyy-MM-dd)
            val today = LocalDate.now().toString()
            if (store.getBalanceAlertLastDate() == today) return
            store.setBalanceAlertLastDate(today)

            Notifications.show(
                context,
                title = "💰 잔액 부족",
                body = "예치금 잔액이 ${Notifications.formatThousands(balance.toLong())}원입니다. 충전이 필요합니다.",
                id = NOTIF_ID,
            )
        } catch (_: Exception) {
            // 원본 debugPrint 후 무시와 동일 (백그라운드 알림 실패가 본 작업을 막지 않도록)
        }
    }
}
