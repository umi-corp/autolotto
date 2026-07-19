package com.umicorp.autolotto

import com.umicorp.autolotto.ui.vm.SettingsViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 즉시 구매 순수 로직: 슬롯 분해(splitSlots) + 판매시간 게이트 경계 + 구매 게이트(purchaseGate). */
class InstantPurchaseLogicTest {

    @Test
    fun `splitSlots - 빈 리스트는 자동, 채워진 리스트는 수동, null은 제외`() {
        val slots = listOf(
            listOf(1, 2, 3, 4, 5, 6),  // 수동
            emptyList(),                // 자동
            null,                       // 미설정
            emptyList(),                // 자동
            null,                       // 미설정
        )
        val (auto, manual) = splitSlots(slots)
        assertEquals(2, auto)
        assertEquals(listOf(listOf(1, 2, 3, 4, 5, 6)), manual)
    }

    @Test
    fun `tapSlotsOutcome - null은 저장소 오류, 0게임은 설정 유도, 그 외 진행`() {
        assertEquals(TapSlotsOutcome.STORE_ERROR, tapSlotsOutcome(null))                       // 읽기 실패 ≠ 미설정
        assertEquals(TapSlotsOutcome.NEEDS_SETUP, tapSlotsOutcome(List(5) { null }))
        assertEquals(TapSlotsOutcome.PROCEED, tapSlotsOutcome(listOf(emptyList(), null, null, null, null)))
        assertEquals(TapSlotsOutcome.PROCEED, tapSlotsOutcome(listOf(listOf(1, 2, 3, 4, 5, 6), null, null, null, null)))
    }

    @Test
    fun `parseManualGames - 슬롯 구분(null·자동·수동)과 5슬롯 절단`() {
        val parsed = parseManualGames("""[[1,2,3,4,5,6],[],null,[7,8,9,10,11,12],null,[13]]""")
        assertEquals(5, parsed.size)                          // 6번째 슬롯은 절단
        assertEquals(listOf(1, 2, 3, 4, 5, 6), parsed[0])
        assertTrue(parsed[1]!!.isEmpty())                     // 자동
        assertEquals(null, parsed[2])                         // 미설정
        assertEquals(listOf(7, 8, 9, 10, 11, 12), parsed[3])
    }

    @Test
    fun `parseManualGames - 기본값 빈 배열은 전부 미설정, malformed는 throw`() {
        assertTrue(parseManualGames("[]").all { it == null })
        val ex = runCatching { parseManualGames("not-json") }.exceptionOrNull()
        assertTrue(ex != null)                                // 호출부가 읽기 실패(null)로 매핑
    }

    @Test
    fun `splitSlots - 전부 null이면 0게임`() {
        val (auto, manual) = splitSlots(List(5) { null })
        assertEquals(0, auto)
        assertTrue(manual.isEmpty())
    }

    @Test
    fun `판매시간 게이트 - 토요일은 19시대까지, 20시부터 정지`() {
        assertTrue(SettingsViewModel.isValidPurchaseTime(day = 6, hour = 19))
        assertFalse(SettingsViewModel.isValidPurchaseTime(day = 6, hour = 20))
    }

    @Test
    fun `판매시간 게이트 - 매일 06시 전 정지, 06시부터 판매`() {
        assertFalse(SettingsViewModel.isValidPurchaseTime(day = 3, hour = 5))
        assertTrue(SettingsViewModel.isValidPurchaseTime(day = 3, hour = 6))
        assertTrue(SettingsViewModel.isValidPurchaseTime(day = 7, hour = 23)) // 일요일 밤 판매
    }

    @Test
    fun `purchaseGate - 회차 불일치는 모드 무관 중단`() {
        assertEquals(
            PurchaseGate.ROUND_CHANGED,
            purchaseGate(extra = false, recordedRound = 0, currentRound = 1200, expectedRound = 1199, saleOpen = true),
        )
        assertEquals(
            PurchaseGate.ROUND_CHANGED,
            purchaseGate(extra = true, recordedRound = 1200, currentRound = 1201, expectedRound = 1200, saleOpen = true),
        )
    }

    @Test
    fun `purchaseGate - 첫 구매만 회차 가드, 추가 구매는 통과`() {
        assertEquals(
            PurchaseGate.ALREADY_PURCHASED,
            purchaseGate(extra = false, recordedRound = 1200, currentRound = 1200, expectedRound = 1200, saleOpen = true),
        )
        assertEquals(
            PurchaseGate.PROCEED,
            purchaseGate(extra = true, recordedRound = 1200, currentRound = 1200, expectedRound = 1200, saleOpen = true),
        )
        assertEquals(
            PurchaseGate.PROCEED,
            purchaseGate(extra = false, recordedRound = 1199, currentRound = 1200, expectedRound = 1200, saleOpen = true),
        )
    }

    @Test
    fun `purchaseGate - 락 대기 중 판매 종료면 모드 무관 중단`() {
        assertEquals(
            PurchaseGate.SALE_CLOSED,
            purchaseGate(extra = true, recordedRound = 1200, currentRound = 1200, expectedRound = 1200, saleOpen = false),
        )
        assertEquals(
            PurchaseGate.SALE_CLOSED,
            purchaseGate(extra = false, recordedRound = 0, currentRound = 1200, expectedRound = 1200, saleOpen = false),
        )
    }
}
