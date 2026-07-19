package com.umicorp.autolotto.util

import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 하이드레이션 1회 게이트 — 성공 1회·실패 재시도·동시 호출 직렬화(스플래시+AppRoot 중복 호출 대비). */
class GatesTest {

    @Test
    fun `success runs once - later calls are no-ops`() = runTest {
        val gate = OnceGate()
        var runs = 0
        gate.run { runs++ }
        gate.run { runs++ }
        assertEquals(1, runs)
        assertTrue(gate.done)
    }

    @Test
    fun `failure does not latch - next call retries and can succeed`() = runTest {
        val gate = OnceGate()
        var attempts = 0
        runCatching { gate.run { attempts++; error("keystore transient") } }
        assertFalse(gate.done)
        gate.run { attempts++ }
        assertEquals(2, attempts)
        assertTrue(gate.done)
    }

    @Test
    fun `concurrent calls are serialized - block executes exactly once`() = runTest {
        val gate = OnceGate()
        var runs = 0
        val a = async { gate.run { runs++ } }
        val b = async { gate.run { runs++ } }
        a.await()
        b.await()
        assertEquals(1, runs)
    }
}
