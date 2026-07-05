package com.umicorp.autolotto.scheduler

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * AlarmTimes 순수 시간계산 테스트 (원본 `SchedulerService._nextDateTime` 패리티).
 * 요일 경계 · 당일 시각 전/후 · floorMod 음수 모듈로 함정 · 토 21:00 고정.
 *
 * KST(Asia/Seoul, DST 없음)로 고정해 plusDays(벽시계 유지)가 결정적이게 한다.
 */
class AlarmTimesTest {

    private val KST = ZoneId.of("Asia/Seoul")
    private fun kst(y: Int, mo: Int, d: Int, h: Int, mi: Int) = ZonedDateTime.of(y, mo, d, h, mi, 0, 0, KST)
    private fun millis(z: ZonedDateTime) = z.toInstant().toEpochMilli()

    // ---------- 모듈로 함정 (DESIGN §2-3) ----------

    @Test
    fun `sunday scheduling saturday uses floorMod not negative modulo`() {
        val now = kst(2026, 6, 28, 10, 0)
        assertEquals(DayOfWeek.SUNDAY, now.dayOfWeek) // 가정 자체 검증
        // 순진한 (6-7)%7 = -1 이면 어제(과거)로 잡힌다. floorMod(-1,7)=6 → 다음 토(7/4).
        val result = AlarmTimes.nextAutoPurchaseMillis(6, 9, 0, now)
        assertEquals(millis(kst(2026, 7, 4, 9, 0)), result)
        assertTrue("음수 모듈로였다면 과거가 됨", result > millis(now))
    }

    // ---------- 당일 시각 전/후 경계 ----------

    @Test
    fun `same day before target fires today`() {
        val now = kst(2026, 7, 1, 8, 0)
        val day = now.dayOfWeek.value
        assertEquals(millis(kst(2026, 7, 1, 9, 0)), AlarmTimes.nextAutoPurchaseMillis(day, 9, 0, now))
    }

    @Test
    fun `same day after target rolls to next week`() {
        val now = kst(2026, 7, 1, 10, 0)
        val day = now.dayOfWeek.value
        // 같은 요일·이미 지난 시각 → daysUntil 0→7 (다음 주)
        assertEquals(millis(kst(2026, 7, 8, 9, 0)), AlarmTimes.nextAutoPurchaseMillis(day, 9, 0, now))
    }

    @Test
    fun `exactly at target time is not after so fires now`() {
        val now = kst(2026, 7, 1, 9, 0)
        val day = now.dayOfWeek.value
        // isAfter는 strict → 정확히 동시각이면 오늘로 발화(원본 DateTime.isAfter와 동일)
        assertEquals(millis(now), AlarmTimes.nextAutoPurchaseMillis(day, 9, 0, now))
    }

    @Test
    fun `upcoming weekday maps within seven days`() {
        val now = kst(2026, 6, 28, 10, 0) // 일요일
        // 다음 월요일(6/29)
        assertEquals(millis(kst(2026, 6, 29, 9, 0)), AlarmTimes.nextAutoPurchaseMillis(1, 9, 0, now))
    }

    // ---------- 토요일 21:00 고정 (결과확인) ----------

    @Test
    fun `next saturday 21 from sunday is upcoming saturday`() {
        val now = kst(2026, 6, 28, 10, 0) // 일요일
        assertEquals(millis(kst(2026, 7, 4, 21, 0)), AlarmTimes.nextSaturday21Millis(now))
    }

    @Test
    fun `next saturday 21 same saturday before 21 fires today`() {
        val now = kst(2026, 7, 4, 20, 0)
        assertEquals(DayOfWeek.SATURDAY, now.dayOfWeek)
        assertEquals(millis(kst(2026, 7, 4, 21, 0)), AlarmTimes.nextSaturday21Millis(now))
    }

    @Test
    fun `next saturday 21 same saturday after 21 rolls to next saturday`() {
        val now = kst(2026, 7, 4, 21, 30)
        assertEquals(millis(kst(2026, 7, 11, 21, 0)), AlarmTimes.nextSaturday21Millis(now))
    }

    @Test
    fun `next saturday 21 exactly 21 fires now`() {
        val now = kst(2026, 7, 4, 21, 0)
        assertEquals(millis(now), AlarmTimes.nextSaturday21Millis(now))
    }
}
