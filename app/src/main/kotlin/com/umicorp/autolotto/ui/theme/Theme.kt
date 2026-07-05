package com.umicorp.autolotto.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Lucky Gloss 테마 (Material 3 Expressive). 고정 크림/틸 스킴을 기본값으로 쓴다.
 *
 * dynamicColor 기본값 = false (벽지 기반 색을 끔). 이걸 켜면 리디자인이 안 보인다.
 * MainActivity는 파라미터 없이 AutoLottoTheme { }로 호출 → 고정 라이트 스킴.
 * ponytail: 다크 스킴도 제공하되 현재 앱은 라이트 위주. darkTheme는 시스템값 따라감.
 */

/** 둥근 모서리 셰이프 세트 — extraLarge 28 / large 22 / medium 16. */
private val LgShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** Expressive 타이포 — 디스플레이/타이틀은 굵게(딥네이비 잉크는 스킴 onSurface가 처리). */
private val LgTypography = Typography().run {
    copy(
        displayLarge = displayLarge.copy(fontWeight = FontWeight.Black),
        displayMedium = displayMedium.copy(fontWeight = FontWeight.Black),
        displaySmall = displaySmall.copy(fontWeight = FontWeight.ExtraBold),
        headlineLarge = headlineLarge.copy(fontWeight = FontWeight.ExtraBold),
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.Bold),
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.Bold),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.Bold),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

/** 히어로 카운트다운 숫자용 초대형 볼드 스타일(스킴에 없어 헬퍼로 노출). */
val CountdownNumberStyle = TextStyle(fontWeight = FontWeight.Black, fontSize = 56.sp)

@Composable
fun AutoLottoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor -> if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(
        colorScheme = colorScheme,
        shapes = LgShapes,
        typography = LgTypography,
        content = content,
    )
}
