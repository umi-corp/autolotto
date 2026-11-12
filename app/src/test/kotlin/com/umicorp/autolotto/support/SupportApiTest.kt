package com.umicorp.autolotto.support

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class SupportApiTest {

    private lateinit var server: MockWebServer
    private val key = "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8"

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() = server.shutdown()

    private fun api() = SupportApi(baseUrl = server.url("/").toString().trimEnd('/'))

    private fun json(body: String) = MockResponse().setResponseCode(200)
        .setHeader("Content-Type", "application/json").setBody(body)

    @Test
    fun `sends the key only as a header and parses the status`() = runBlocking {
        server.enqueue(json("""{"unread":true,"waiting":false}"""))

        assertEquals(SupportStatus(unread = true, waiting = false), api().status(key))

        val req = server.takeRequest()
        assertEquals("GET", req.method)
        assertEquals("/api/autolotto/status", req.path)
        assertEquals(key, req.getHeader("X-Support-Key"))
    }

    @Test
    fun `parses waiting`() = runBlocking {
        server.enqueue(json("""{"unread":false,"waiting":true}"""))
        assertEquals(SupportStatus(unread = false, waiting = true), api().status(key))
    }

    @Test
    fun `closed service 404 is a failure`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(404))
        assertNull(api().status(key))
    }

    @Test
    fun `server error is a failure`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(503))
        assertNull(api().status(key))
    }

    @Test
    fun `body without the fields is a failure, not all-false`() = runBlocking {
        server.enqueue(json("{}"))
        assertNull(api().status(key))
    }

    @Test
    fun `non-json body is a failure`() = runBlocking {
        server.enqueue(json("<html>maintenance</html>"))
        assertNull(api().status(key))
    }

    @Test
    fun `network failure is a failure`() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        assertNull(api().status(key))
    }
}
