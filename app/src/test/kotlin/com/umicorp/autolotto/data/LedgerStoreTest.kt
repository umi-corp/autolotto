package com.umicorp.autolotto.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.LocalDate

class LedgerStoreTest {

    @get:Rule val tmp = TemporaryFolder()

    private val dir get() = File(tmp.root, "ledger")

    private fun ledger(vararg keys: String) = emptyLedger("645").copy(
        rows = keys.map { LedgerRow(it, LocalDate.parse("2026-11-21"), 1251, 5, RowStatus.LOSE, 0) },
    )

    private fun mainFile(userId: String) = File(dir, "${accountKey(userId)}.json")

    @Test fun save_thenLoad_roundTrips() {
        val store = LedgerStore(dir, "645")
        val l = ledger("A", "B")
        store.save("user1", l)
        assertEquals(l, store.load("user1"))
    }

    @Test fun fileName_hidesUserId() {
        LedgerStore(dir, "645").save("user1", ledger("A"))
        assertFalse(dir.list()!!.any { "user1" in it })
        assertEquals(16, accountKey("user1").length)
    }

    @Test fun accounts_isolated() {
        val store = LedgerStore(dir, "645")
        store.save("A", ledger("X"))
        assertEquals(emptyLedger("645"), store.load("B"))
    }

    @Test fun load_corruptMain_fallsBackToPrev() {
        val store = LedgerStore(dir, "645")
        val l1 = ledger("A")
        store.save("user1", l1)
        store.save("user1", ledger("A", "B"))
        mainFile("user1").writeText("{")
        assertEquals(l1, store.load("user1"))
    }

    @Test fun load_bothCorrupt_returnsEmpty() {
        val store = LedgerStore(dir, "645")
        store.save("user1", ledger("A"))
        store.save("user1", ledger("A", "B"))
        dir.listFiles()!!.forEach { it.writeText("x") }
        assertEquals(emptyLedger("645"), store.load("user1"))
    }

    @Test fun load_productMismatch_returnsEmpty() {
        LedgerStore(dir, "720").save("user1", emptyLedger("720").copy(rows = ledger("A").rows))
        assertEquals(emptyLedger("645"), LedgerStore(dir, "645").load("user1"))
    }

    @Test fun clearAll_removesEverything() {
        val store = LedgerStore(dir, "645")
        store.save("user1", ledger("A"))
        store.clearAll()
        assertFalse(dir.exists())
    }
}
