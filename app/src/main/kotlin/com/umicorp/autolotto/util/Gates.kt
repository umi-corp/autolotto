package com.umicorp.autolotto.util

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 1회 성공 게이트 — 프로세스 스코프 하이드레이션용 (crosscheck R1: rememberSaveable 복원으로
 * 스플래시가 스킵돼도 AppRoot가 재호출하는 구조에서, 성공은 1회만·실패는 재시도 가능해야 한다).
 *
 * 동시 호출은 직렬화되고, [run]의 block이 성공(정상 반환)하면 이후 호출은 no-op.
 * block이 던지면 done으로 기록하지 않아 다음 호출이 재시도한다. 예외는 호출자에게 전파.
 */
class OnceGate {
    private val mutex = Mutex()

    @Volatile
    var done = false
        private set

    suspend fun run(block: suspend () -> Unit) {
        mutex.withLock {
            if (done) return
            block()
            done = true
        }
    }
}
