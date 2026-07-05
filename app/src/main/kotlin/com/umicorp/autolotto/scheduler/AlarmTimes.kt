package com.umicorp.autolotto.scheduler

import java.time.DayOfWeek
import java.time.ZonedDateTime

/**
 * 알람 발생 시각 순수 계산 (java.time만 사용 — Android 의존 없음 → JVM 단위테스트 가능).
 *
 * 원본 `SchedulerService._nextDateTime(weekday, hour, minute)`의 1:1 포트.
 * weekday는 Dart와 동일하게 1=월 .. 7=일.
 *
 * 🔴 모듈로 함정(DESIGN §2-3): Dart `%`는 항상 음이 아니지만 Kotlin/Java `%`는 부호가 피제수를
 *    따른다. 일요일(now=7)에 토요일(6) 알람을 잡으면 순진한 `(6-7)%7=-1`이 과거가 된다.
 *    `Math.floorMod`로 비음수 모듈로를 강제해 Dart `(weekday-now.weekday)%7`과 일치시킨다.
 */
object AlarmTimes {

    /**
     * 다음 자동구매 시각(epoch millis). [now]의 존(zone)에서 벽시계 [hour]:[minute]에 맞춘다.
     *
     * 원본 로직:
     *   target = 오늘 hour:minute
     *   daysUntil = (day - now.weekday) mod 7   // 비음수
     *   if (daysUntil==0 && now가 target보다 뒤) daysUntil = 7   // isAfter는 strict
     *   target += daysUntil 일
     *
     * ponytail: KST는 DST가 없어 plusDays(벽시계 유지)와 Dart의 Duration(days:) 결과가 동일하다.
     *           DST 존에서만 미세 차이 — 앱은 한국 기기(KST) 대상이므로 무시한다.
     */
    fun nextAutoPurchaseMillis(day: Int, hour: Int, minute: Int, now: ZonedDateTime): Long {
        val targetToday = now.toLocalDate().atTime(hour, minute).atZone(now.zone)
        var daysUntil = Math.floorMod(day - now.dayOfWeek.value, 7)
        if (daysUntil == 0 && now.isAfter(targetToday)) daysUntil = 7
        return targetToday.plusDays(daysUntil.toLong()).toInstant().toEpochMilli()
    }

    /** 다음 토요일 21:00(epoch millis). 원본의 결과확인 고정 시각(토 21:00). Dart saturday=6. */
    fun nextSaturday21Millis(now: ZonedDateTime): Long =
        nextAutoPurchaseMillis(DayOfWeek.SATURDAY.value, 21, 0, now)
}
