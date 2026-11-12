package com.umicorp.autolotto.support

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** docs/DESIGN-support-board.md "새 답변 확인" 표의 행마다 하나. */
class SupportPollTest {

    @Test
    fun `no key means no check`() {
        assertFalse(shouldCheckSupport(key = null, checkNeeded = true))
    }

    @Test
    fun `check-needed off means no check`() {
        assertFalse(shouldCheckSupport(key = "k", checkNeeded = false))
    }

    @Test
    fun `check-needed on with a key means check`() {
        assertTrue(shouldCheckSupport(key = "k", checkNeeded = true))
    }

    @Test
    fun `unread shows the dot and keeps checking`() {
        assertEquals(
            SupportState(dot = true, checkNeeded = true),
            nextSupportState(SupportState(dot = false, checkNeeded = true), SupportStatus(unread = true, waiting = false)),
        )
    }

    @Test
    fun `nothing unread or waiting clears the dot and stops checking`() {
        assertEquals(
            SupportState(dot = false, checkNeeded = false),
            nextSupportState(SupportState(dot = true, checkNeeded = true), SupportStatus(unread = false, waiting = false)),
        )
    }

    @Test
    fun `waiting without unread clears the dot but keeps checking`() {
        assertEquals(
            SupportState(dot = false, checkNeeded = true),
            nextSupportState(SupportState(dot = true, checkNeeded = true), SupportStatus(unread = false, waiting = true)),
        )
    }

    @Test
    fun `failed check changes nothing`() {
        val state = SupportState(dot = true, checkNeeded = true)
        assertEquals(state, nextSupportState(state, status = null))
    }
}
