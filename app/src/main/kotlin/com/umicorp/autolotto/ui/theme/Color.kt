package com.umicorp.autolotto.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Lucky Gloss 디자인 토큰 (Material 3 Expressive). 벽지 dynamic color 대신 고정 스킴을 쓴다.
 * 로또 공 색은 여기 없다 — 항상 com.umicorp.autolotto.ui.util.ballColor(n) 재사용.
 */

// 캔버스 / 표면
val LgCream = Color(0xFFF6F1E7)          // 배경 크림
val LgSurface = Color(0xFFFFFFFF)        // 프로스트 카드
val LgSurfaceLow = Color(0xFFFCFAF5)     // 낮은 표면
val LgSurfaceVariant = Color(0xFFECE6D9) // 중립 표면(미선택 공 등)

// 브랜드
val LgTeal = Color(0xFF0F8B8D)           // primary
val LgTealInk = Color(0xFF0B6E70)        // deep teal accent
val LgGold = Color(0xFFF5B301)           // secondary
val LgGreen = Color(0xFF6FBF3B)          // tertiary
val LgAmber = Color(0xFFE9930B)          // warning

// 잉크 / 텍스트
val LgInk = Color(0xFF1E2A44)            // onSurface / 헤딩·본문 딥네이비
val LgMuted = Color(0xFF6B7280)          // 보조 텍스트
val LgOutline = Color(0xFFD6CDBB)        // 크림 톤 외곽선
val LgError = Color(0xFFBA1A1A)

// CTA 딥네이비 그라디언트 (execBuy 등 핵심 버튼)
val LgCtaNavyStart = Color(0xFF16233F)
val LgCtaNavyEnd = Color(0xFF24406B)

/** 딥네이비 CTA 그라디언트 브러시 (좌상→우하). onPrimary=흰색. */
fun ctaGradient(): Brush = Brush.linearGradient(listOf(LgCtaNavyStart, LgCtaNavyEnd))

/** 홈 카운트다운 히어로용 liquid 그라디언트 (blue→teal→orange). */
fun heroGradient(): Brush = Brush.linearGradient(
    0.0f to Color(0xFF1E4B8F),
    0.45f to LgTeal,
    1.0f to Color(0xFFE9930B),
)

val LightColors = lightColorScheme(
    primary = LgTeal,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB9E7E7),
    onPrimaryContainer = LgTealInk,
    secondary = LgGold,
    onSecondary = Color(0xFF3A2E00),
    secondaryContainer = Color(0xFFFDECC0),
    onSecondaryContainer = Color(0xFF3A2E00),
    tertiary = LgGreen,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD8EFC2),
    onTertiaryContainer = Color(0xFF20330A),
    error = LgError,
    onError = Color.White,
    background = LgCream,
    onBackground = LgInk,
    surface = LgCream,
    onSurface = LgInk,
    surfaceVariant = LgSurfaceVariant,
    onSurfaceVariant = LgMuted,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = LgSurfaceLow,
    surfaceContainer = Color(0xFFF1EBDD),
    surfaceContainerHigh = Color(0xFFEBE4D4),
    surfaceContainerHighest = Color(0xFFE5DDCB),
    outline = LgOutline,
    outlineVariant = Color(0xFFE4DCCB),
)

// 다크: Lucky Gloss 팔레트를 어둡게 변주(테마 일관성 유지).
val DarkColors = darkColorScheme(
    primary = Color(0xFF62D6D6),
    onPrimary = Color(0xFF00373A),
    primaryContainer = LgTealInk,
    onPrimaryContainer = Color(0xFFB9E7E7),
    secondary = LgGold,
    onSecondary = Color(0xFF3A2E00),
    tertiary = Color(0xFF9BD97A),
    onTertiary = Color(0xFF10300A),
    background = Color(0xFF15161B),
    onBackground = Color(0xFFEDE6D6),
    surface = Color(0xFF15161B),
    onSurface = Color(0xFFEDE6D6),
    surfaceVariant = Color(0xFF3A3A40),
    onSurfaceVariant = Color(0xFFC7C2B6),
    outline = Color(0xFF6E6A5E),
)
