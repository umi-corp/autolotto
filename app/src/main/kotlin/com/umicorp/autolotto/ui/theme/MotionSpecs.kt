package com.umicorp.autolotto.ui.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

/**
 * Lucky Gloss 모션 프리미티브 (Expressive). 화면·컴포넌트가 공유해 일관된 스프링을 쓴다.
 * 공 선택 스펙은 목업 기준: dampingRatio ~0.45, medium stiffness (overshoot 후 settle).
 */
object MotionSpecs {
    /** 통통 튀는 오버슈트 스프링 (공 탭/선택 바운스). */
    fun <T> bouncy() = spring<T>(
        dampingRatio = 0.45f,
        stiffness = Spring.StiffnessMedium,
    )

    /** 부드러운 정착 스프링 (탭 전환/인디케이터 이동). */
    fun <T> gentle() = spring<T>(
        dampingRatio = 0.75f,
        stiffness = Spring.StiffnessMediumLow,
    )

    /** 페이드/등장용 트윈. */
    fun <T> emphasized() = tween<T>(durationMillis = 320)

    /** 스태거 등장에서 index당 지연(ms) — 화면에서 delayMillis 계산에 사용. */
    const val StaggerStepMs = 60

    /** index번째 요소의 등장 지연(ms). 상한을 둬 마지막 요소도 금방 뜬다. */
    fun staggerDelay(index: Int): Int = (index * StaggerStepMs).coerceAtMost(360)
}
