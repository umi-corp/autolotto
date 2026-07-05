package com.umicorp.autolotto.ui.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent

/**
 * Activity가 아닌 Context에서 startActivity 호출 시의 크래시를 막는 안전 런처.
 *
 * 언어를 system이 아닌 값으로 바꾸면 [com.umicorp.autolotto.ui.LocalizedApp]이 `createConfigurationContext`로
 * 만든 **비-Activity Context**를 LocalContext로 주입한다. 그 컨텍스트로 `startActivity`를
 * `FLAG_ACTIVITY_NEW_TASK` 없이 호출하면 AndroidRuntimeException으로 죽는다(충전/오픈소스/후원/배터리/정확알람).
 *
 * 실제 Activity를 찾으면 그대로(태스크 유지), 못 찾으면 NEW_TASK를 붙여 실행한다. 처리할 액티비티가
 * 없으면(예: 일부 기기의 배터리 최적화 인텐트) 조용히 무시한다.
 */
fun Context.launchActivitySafely(intent: Intent) {
    val activity = findActivity()
    try {
        if (activity != null) {
            activity.startActivity(intent)
        } else {
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    } catch (e: Exception) {
        // 처리 가능한 Activity가 없음 — 원본 url_launcher/Intent도 동일 상황에선 아무 동작 안 함.
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
