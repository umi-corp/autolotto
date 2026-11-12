package com.umicorp.autolotto.support

import okhttp3.HttpUrl
import java.security.SecureRandom
import java.util.Base64

/** 문의 게시판 연동의 순수 규칙 — docs/DESIGN-support-board.md. */

private val secureRandom = SecureRandom()

/** 기기 키: 32바이트 → base64url(패딩 없음, 43자). [fill]은 테스트용 바이트 주입. */
fun newSupportKey(fill: (ByteArray) -> Unit = secureRandom::nextBytes): String =
    Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32).also(fill))

const val SUPPORT_HOST = "support.umicorp.kr"
const val SUPPORT_SERVICE = "autolotto"

// 서버도 같은 값에서 자른다(company 스펙 #5).
private const val MAX_PARAM_CHARS = 100

private val BOARD_LANGS = setOf("ko", "ja", "en")

/** 앱 언어("system"이면 시스템 언어) 중 게시판이 아는 ko·ja·en만. null이면 게시판이 Accept-Language로 정한다. */
fun boardLang(appLanguage: String, systemLanguage: String): String? =
    (if (appLanguage == "system") systemLanguage else appLanguage).takeIf { it in BOARD_LANGS }

fun deviceLabel(manufacturer: String, model: String, release: String): String =
    "$manufacturer $model / Android $release"

/** 키는 fragment에만 둔다 — 서버 접속 기록에 남지 않는다. */
fun supportBoardUrl(lang: String?, appVersion: String, device: String, key: String): String =
    HttpUrl.Builder()
        .scheme("https")
        .host(SUPPORT_HOST)
        .addPathSegment(SUPPORT_SERVICE)
        .apply { if (lang != null) addQueryParameter("lang", lang) }
        .addQueryParameter("app_version", appVersion.take(MAX_PARAM_CHARS))
        .addQueryParameter("device", device.take(MAX_PARAM_CHARS))
        .fragment("k=$key")
        .build()
        .toString()

/** 상태 API 응답. */
data class SupportStatus(val unread: Boolean, val waiting: Boolean)

/** [dot]은 "문의" 줄의 새 답변 점, [checkNeeded]는 영속되는 "확인 필요". */
data class SupportState(val dot: Boolean, val checkNeeded: Boolean)

/** 문의하지 않은 사용자의 통신을 늘리지 않는다(스펙 #5 User Story 42, 43). */
fun shouldCheckSupport(key: String?, checkNeeded: Boolean): Boolean = key != null && checkNeeded

/** [status]가 null(네트워크 실패·404 등)이면 그대로 둔다. */
fun nextSupportState(state: SupportState, status: SupportStatus?): SupportState = when (status) {
    null -> state
    else -> SupportState(dot = status.unread, checkNeeded = status.unread || status.waiting)
}
