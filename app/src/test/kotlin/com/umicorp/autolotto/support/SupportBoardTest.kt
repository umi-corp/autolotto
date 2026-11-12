package com.umicorp.autolotto.support

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URI
import java.net.URLDecoder

class SupportBoardTest {

    @Test
    fun `key is 32 random bytes as unpadded base64url`() {
        // 기대값은 Python base64.urlsafe_b64encode 로 따로 계산했다.
        assertEquals(
            "-_v7-_v7-_v7-_v7-_v7-_v7-_v7-_v7-_v7-_v7-_s",
            newSupportKey { it.fill(0xFB.toByte()) },
        )
        assertEquals(
            "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8",
            newSupportKey { b -> b.indices.forEach { b[it] = it.toByte() } },
        )
        assertTrue(Regex("[A-Za-z0-9_-]{43}").matches(newSupportKey()))
    }

    // 빌더(OkHttp HttpUrl)와 다른 파서로 되읽어 인코딩을 확인한다.
    private fun queryOf(url: String): Map<String, String> =
        URI(url).rawQuery.split('&').associate { pair ->
            val (k, v) = pair.split('=', limit = 2)
            URLDecoder.decode(k, "UTF-8") to URLDecoder.decode(v, "UTF-8")
        }

    @Test
    fun `board url carries version and device in query and key only in fragment`() {
        val key = "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8"
        val url = supportBoardUrl(
            lang = "ja",
            appVersion = "1.1.11",
            device = "A&B #1 / Android 15",
            key = key,
        )
        val uri = URI(url)
        assertEquals("https", uri.scheme)
        assertEquals("support.umicorp.kr", uri.host)
        assertEquals("/autolotto", uri.path)
        assertEquals(
            mapOf("lang" to "ja", "app_version" to "1.1.11", "device" to "A&B #1 / Android 15"),
            queryOf(url),
        )
        assertEquals("k=$key", uri.fragment)
        assertTrue(!uri.rawQuery.contains(key))
    }

    @Test
    fun `board url omits lang when unresolved`() {
        val url = supportBoardUrl(lang = null, appVersion = "1.1.11", device = "d", key = "k")
        assertEquals(setOf("app_version", "device"), queryOf(url).keys)
    }

    @Test
    fun `board url caps app_version and device at 100 chars`() {
        val url = supportBoardUrl(lang = "ko", appVersion = "9".repeat(150), device = "가".repeat(150), key = "k")
        assertEquals("9".repeat(100), queryOf(url)["app_version"])
        assertEquals("가".repeat(100), queryOf(url)["device"])
    }

    @Test
    fun `board lang follows app setting then system and keeps only ko ja en`() {
        assertEquals("ko", boardLang(appLanguage = "ko", systemLanguage = "en"))
        assertEquals("en", boardLang(appLanguage = "en", systemLanguage = "ko"))
        assertEquals("ja", boardLang(appLanguage = "system", systemLanguage = "ja"))
        assertNull(boardLang(appLanguage = "system", systemLanguage = "fr"))
    }

    @Test
    fun `device label is maker model and android release`() {
        assertEquals("Lenovo TB321FU / Android 15", deviceLabel("Lenovo", "TB321FU", "15"))
    }
}
