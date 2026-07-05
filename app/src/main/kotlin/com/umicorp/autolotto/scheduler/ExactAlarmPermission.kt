package com.umicorp.autolotto.scheduler

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

/**
 * 정확 알람(SCHEDULE_EXACT_ALARM) 런타임 권한 헬퍼.
 *
 * API 31+(minSdk 31)에서 사용자가 "정확한 알람" 권한을 끄면 `setExactAndAllowWhileIdle`이
 * SecurityException을 던진다. [AlarmScheduler]가 예약 전에 [canScheduleExactAlarms]로 게이트하고,
 * 설정 화면(다음 슬라이스)이 [requestIntent]로 시스템 권한 화면을 연다.
 */
object ExactAlarmPermission {

    /** 정확 알람을 예약할 수 있는가. API 31 미만은 항상 가능(권한 개념 없음). */
    fun canScheduleExactAlarms(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val am = context.getSystemService(AlarmManager::class.java)
        return am?.canScheduleExactAlarms() == true
    }

    /** 정확 알람 권한 요청 화면 Intent(앱 지정). 호출부에서 `startActivity`. */
    fun requestIntent(context: Context): Intent =
        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))
}
