package com.umicorp.autolotto.scheduler

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException

/**
 * 자동구매 실패 알림이 연결 실패를 자격증명 탓으로 통보하지 않아야 한다.
 *
 * 사용자 리포트의 "로그인이 안 되는 것처럼 보인다"의 정체 — 동행복권 무응답(타임아웃)인데
 * 알림은 "아이디/비밀번호를 확인해주세요"였고, 사용자는 멀쩡한 계정을 의심하며 손을 놓았다.
 */
class AutoPurchaseFailureBodyTest {

    @Test
    fun `timeout during login is not reported as a credential problem`() {
        val e = Exception("[login] timeout", SocketTimeoutException("timeout"))
        val body = autoPurchaseFailureBody(e.message.orEmpty(), e.isConnectivityFailure())

        assertFalse(body, body.contains("비밀번호"))
        assertTrue(body, body.contains("연결하지 못했습니다"))
    }

    @Test
    fun `real credential rejection still says to check the credentials`() {
        val e = Exception("[login] INVALID_CREDENTIALS")   // cause 없음 = 네트워크 실패 아님
        val body = autoPurchaseFailureBody(e.message.orEmpty(), e.isConnectivityFailure())

        assertEquals("로그인에 실패했습니다. 아이디/비밀번호를 확인해주세요.", body)
    }

    @Test
    fun `connectivity verdict is limited to the login step`() {
        // [purchase] 이후의 IOException은 구매가 접수됐을 수 있어 "연결 실패"로 뭉뚱그리면 안 된다.
        val e = Exception("[purchase] read timed out", SocketTimeoutException("timeout"))
        val body = autoPurchaseFailureBody(e.message.orEmpty(), e.isConnectivityFailure())

        assertFalse(body, body.contains("연결하지 못했습니다"))
        assertEquals("자동 구매에 실패했습니다. 앱을 열어 상태를 확인해주세요.", body)
    }

    @Test
    fun `server rejection message is passed through`() {
        val e = Exception("[purchase] 구매 실패: 주간 구매금액을 초과했습니다")
        val body = autoPurchaseFailureBody(e.message.orEmpty(), e.isConnectivityFailure())

        assertEquals("구매 실패: 주간 구매금액을 초과했습니다", body)
    }

    @Test
    fun `connectivity failure is detected through the cause chain`() {
        assertTrue(Exception("wrapped", Exception("inner", IOException("boom"))).isConnectivityFailure())
        assertTrue(IOException("direct").isConnectivityFailure())
        assertFalse(Exception("plain").isConnectivityFailure())
    }

    @Test
    fun `cause cycle does not hang the detector`() {
        // 방어 코드 확인 — 순환 cause에도 유한 시간에 판정한다.
        val a = Exception("a")
        val b = Exception("b", a)
        a.initCause(b)
        assertFalse(a.isConnectivityFailure())
    }
}
