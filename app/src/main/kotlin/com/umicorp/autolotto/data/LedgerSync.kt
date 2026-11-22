package com.umicorp.autolotto.data

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.OffsetDateTime

/** 결과확인 워커와 내역 화면이 같은 프로세스에서 원장을 읽고-고쳐-쓴다. 그 구간을 직렬화한다. */
object LedgerLock {
    val mutex = Mutex()
}

class LedgerSync(
    private val store: LedgerStore,
    private val now: () -> OffsetDateTime,
    private val resolve: suspend (List<LedgerRow>) -> List<LedgerRow> = { it },
) {
    /** 동기화 창 → resolve → 프리필 순서. 창마다 저장하므로 IOException이 나도 받은 창은 남는다. */
    suspend fun run(userId: String, fetcher: WindowFetcher, onProgress: (Ledger) -> Unit = {}): Ledger =
        LedgerLock.mutex.withLock {
            val at = now()
            val today = at.atZoneSameInstant(LEDGER_ZONE).toLocalDate()
            var l = store.load(userId)
            for (w in syncWindows(l, today)) {
                l = l.copy(rows = applyWindow(l.rows, fetcher.fetch(w)))
                store.save(userId, l)
            }
            // 프리필 하한은 처음 시작한 날 기준으로 고정한다. 첫 프리필 창 전에 저장해 둔다.
            var p = l.prefill ?: initialPrefill(today)
            l = l.copy(syncedAt = at, rows = resolve(l.rows), prefill = p)
            store.save(userId, l)
            onProgress(l)
            while (true) {
                val w = nextPrefillWindow(p) ?: break
                p = advancePrefill(p, w)
                l = l.copy(rows = applyWindow(l.rows, fetcher.fetch(w)), prefill = p)
                store.save(userId, l)
                onProgress(l)
            }
            l
        }
}
