package com.umicorp.autolotto.data

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

/** 파일 이름에 아이디를 쓰지 않기 위한 계정 해시(SHA-256 hex 앞 16자). */
fun accountKey(userId: String): String =
    MessageDigest.getInstance("SHA-256").digest(userId.toByteArray())
        .joinToString("") { "%02x".format(it) }
        .take(16)

class LedgerStore(private val dir: File, private val product: String) {

    private fun main(userId: String) = File(dir, "${accountKey(userId)}.json")
    private fun prev(userId: String) = File(dir, "${accountKey(userId)}.prev.json")

    /** 본 파일 → 직전 사본 → 빈 원장 순서로 읽는다. */
    fun load(userId: String): Ledger =
        sequenceOf(main(userId), prev(userId))
            .filter { it.exists() }
            .firstNotNullOfOrNull { f ->
                try {
                    parseLedger(f.readText(), product)
                } catch (_: IllegalArgumentException) {
                    null
                }
            }
            ?: emptyLedger(product)

    fun save(userId: String, ledger: Ledger) {
        dir.mkdirs()
        val main = main(userId)
        if (main.exists()) Files.copy(main.toPath(), prev(userId).toPath(), StandardCopyOption.REPLACE_EXISTING)
        val tmp = File(dir, "${main.name}.tmp")
        tmp.writeText(ledger.toJson())
        Files.move(tmp.toPath(), main.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    }

    fun clearAll() {
        dir.deleteRecursively()
    }
}
