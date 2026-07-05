package com.umicorp.autolotto.dhlottery

/**
 * 동행복권 API 상수. host(베이스 URL)는 [DhlotterySession]이 붙인다 —
 * 테스트에서 MockWebServer URL을 주입할 수 있도록 경로만 상수로 둔다.
 */
object ApiConstants {
    const val DEFAULT_BASE = "https://www.dhlottery.co.kr"
    const val DEFAULT_OLOTTO = "https://ol.dhlottery.co.kr"

    // www 경로
    const val LOGIN_PAGE = "/login"
    const val RSA_MODULUS = "/login/selectRsaModulus.do"
    const val LOGIN = "/login/securityLoginCheck.do"
    const val MAIN = "/main"
    const val BALANCE = "/mypage/selectUserMndp.do"
    const val MYPAGE_LEDGER = "/mypage/mylotteryledger" // 원장 페이지(.do 아님) — 내역 방문/Referer용
    const val PURCHASE_HISTORY = "/mypage/selectMyLotteryledger.do"
    const val TICKET_DETAIL = "/mypage/lotto645TicketDetail.do"
    const val WINNING_NUMBER = "/lt645/selectPstLt645Info.do"

    // ol 경로
    const val GAME645 = "/olotto/game/game645.do"
    const val READY_SOCKET = "/olotto/game/egovUserReadySocket.json"
    const val EXEC_BUY = "/olotto/game/execBuy.do"

    val DEFAULT_HEADERS: Map<String, String> = mapOf(
        "User-Agent" to "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36",
        "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
        "Accept-Language" to "ko-KR,ko;q=0.9,en-US;q=0.8,en;q=0.7",
        "Connection" to "keep-alive",
    )
}
