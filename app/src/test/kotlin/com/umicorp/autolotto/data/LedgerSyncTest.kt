package com.umicorp.autolotto.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.time.LocalDate
import java.time.OffsetDateTime

class LedgerSyncTest {

    @get:Rule val tmp = TemporaryFolder()

    private val now = OffsetDateTime.parse("2026-11-22T10:00:00+09:00")
    private val today = LocalDate.parse("2026-11-22")
    private val store by lazy { LedgerStore(File(tmp.root, "ledger"), "645") }
    private var pauses = 0
    private fun sync(resolve: suspend (List<LedgerRow>) -> List<LedgerRow> = { it }) =
        LedgerSync(store, { now }, resolve, pause = { pauses++ })

    /** 받은 창을 기록하고, 창마다 키가 `prefix+창 끝`인 행 하나를 준다. [failAt]번째 호출(1부터)에서 실패한다. */
    private class Recorder(val prefix: String = "W", val failAt: Int = 0, val u: Boolean = false) : WindowFetcher {
        val windows = mutableListOf<Window>()
        override suspend fun fetch(w: Window): List<LedgerRow> {
            windows += w
            if (windows.size == failAt) throw IOException("boom")
            delay(1)
            return listOf(LedgerRow("$prefix${w.to}", w.to, 1200, 1, RowStatus.LOSE, 0, u = u))
        }
    }

    @Test fun run_firstTime_syncsThenPrefillsToJanuaryFirst() = runBlocking {
        val f = Recorder()
        sync().run("user1", f)
        assertEquals(24, f.windows.size)
        assertEquals(today, f.windows.first().to)
        assertEquals(LocalDate.parse("2021-01-01"), f.windows.last().from)
        assertTrue(store.load("user1").prefill!!.done)
    }

    // 회귀 방지: 첫 프리필이 동행복권에 연속 요청을 몰아 보내지 않게 프리필 창마다 쉰다.
    @Test fun run_pausesBeforeEachPrefillWindowOnly() = runBlocking {
        val f = Recorder()
        sync().run("user1", f)
        assertEquals(f.windows.size - 1, pauses) // 동기화 1창은 쉬지 않는다
        pauses = 0
        sync().run("user1", Recorder())
        assertEquals(0, pauses)
    }

    @Test fun run_secondTime_onlySyncs() = runBlocking {
        sync().run("user1", Recorder())
        val f = Recorder()
        sync().run("user1", f)
        assertEquals(1, f.windows.size)
    }

    @Test fun run_failureMidPrefill_keepsProgressAndResumes() = runBlocking {
        val f = Recorder(failAt = 5)
        assertThrows(IOException::class.java) { runBlocking { sync().run("user1", f) } }
        val saved = store.load("user1")
        assertEquals(f.windows.take(4).map { "W${it.to}" }.toSet(), saved.rows.map { it.k }.toSet())
        assertEquals(f.windows[3].from.minusDays(1), saved.prefill!!.nextEnd)
        assertEquals(LocalDate.parse("2021-01-01"), saved.prefill!!.floor)

        val again = Recorder()
        sync().run("user1", again)
        assertEquals(f.windows[4], again.windows[1])
    }

    @Test fun run_syncFailure_doesNotAdvanceSyncedAt() = runBlocking {
        val before = OffsetDateTime.parse("2026-11-20T10:00:00+09:00")
        store.save("user1", emptyLedger("645").copy(syncedAt = before))
        assertThrows(IOException::class.java) { runBlocking { sync().run("user1", Recorder(failAt = 1)) } }
        assertEquals(before, store.load("user1").syncedAt)
    }

    @Test fun run_concurrentRuns_noLostRows() = runBlocking {
        listOf(Recorder("A"), Recorder("B"))
            .map { f -> async(Dispatchers.Default) { sync().run("user1", f) } }
            .awaitAll()
        val keys = store.load("user1").rows.map { it.k }
        assertTrue(keys.any { it.startsWith("A") })
        assertTrue(keys.any { it.startsWith("B") })
    }

    @Test fun run_resolveApplied() = runBlocking {
        sync(resolve = { rows -> rows.map { it.copy(u = false) } }).run("user1", Recorder(u = true))
        sync(resolve = { rows -> rows.map { it.copy(u = false) } }).run("user1", Recorder(u = true))
        assertFalse(store.load("user1").rows.any { it.u })
    }
}
