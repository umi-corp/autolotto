package com.umicorp.autolotto.dhlottery

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 커스텀 쿠키 저장소의 서브도메인 전파 규칙 검증 — 이 앱 머니패스의 핵심 메커니즘. */
class DomainCookieStoreTest {

    @Test
    fun `Domain dot cookie propagates across www and ol subdomains`() {
        val store = DomainCookieStore()
        store.store(
            listOf("JSESSIONID=abc123; Domain=.dhlottery.co.kr; Path=/; HttpOnly"),
            "www.dhlottery.co.kr",
        )
        assertTrue(store.cookieHeader("www.dhlottery.co.kr").contains("JSESSIONID=abc123"))
        // 표준 CookieJar라면 막혔을 www → ol 전파가 여기선 동작해야 함
        assertTrue(store.cookieHeader("ol.dhlottery.co.kr").contains("JSESSIONID=abc123"))
    }

    @Test
    fun `host-only cookie is not sent to a different subdomain`() {
        val store = DomainCookieStore()
        store.store(listOf("WMONID=xyz; Path=/"), "www.dhlottery.co.kr")
        assertTrue(store.cookieHeader("www.dhlottery.co.kr").contains("WMONID=xyz"))
        assertFalse(store.cookieHeader("ol.dhlottery.co.kr").contains("WMONID=xyz"))
    }

    @Test
    fun `malformed set-cookie without a name is skipped`() {
        val store = DomainCookieStore()
        store.store(listOf("=bad; Path=/", "GOOD=1"), "www.dhlottery.co.kr")
        val header = store.cookieHeader("www.dhlottery.co.kr")
        assertTrue(header.contains("GOOD=1"))
        assertFalse(header.contains("bad"))
    }

    @Test
    fun `clear empties the store`() {
        val store = DomainCookieStore()
        store.store(listOf("JSESSIONID=abc; Domain=.dhlottery.co.kr"), "www.dhlottery.co.kr")
        store.clear()
        assertTrue(store.cookieHeader("www.dhlottery.co.kr").isEmpty())
        assertFalse(store.hasCookie("JSESSIONID"))
    }

    @Test
    fun `snapshot is a deep copy - later mutations do not leak into it`() {
        val store = DomainCookieStore()
        store.store(listOf("JSESSIONID=old; Domain=.dhlottery.co.kr"), "www.dhlottery.co.kr")
        val snap = store.snapshot()

        // 스냅샷 후 원본을 변조(clear + 새 쿠키) — 스냅샷으로 복원하면 변조 전 상태여야 함
        store.clear()
        store.store(listOf("JSESSIONID=new; Domain=.dhlottery.co.kr"), "www.dhlottery.co.kr")
        store.restore(snap)

        assertTrue(store.cookieHeader("www.dhlottery.co.kr").contains("JSESSIONID=old"))
        assertFalse(store.cookieHeader("www.dhlottery.co.kr").contains("JSESSIONID=new"))
    }

    @Test
    fun `restore replaces entirely - cookies from a failed attempt do not survive`() {
        val store = DomainCookieStore()
        store.store(listOf("JSESSIONID=valid; Domain=.dhlottery.co.kr"), "www.dhlottery.co.kr")
        val snap = store.snapshot()

        // 실패한 로그인 시도 중 유입된 잡쿠키 — 복원 후 남아 있으면 안 됨(전체 교체)
        store.store(listOf("WMONID=stray; Path=/"), "www.dhlottery.co.kr")
        store.restore(snap)

        assertTrue(store.hasCookie("JSESSIONID"))
        assertFalse(store.hasCookie("WMONID"))
    }
}
