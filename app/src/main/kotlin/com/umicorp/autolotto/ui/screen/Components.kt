package com.umicorp.autolotto.ui.screen

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umicorp.autolotto.ui.theme.MotionSpecs
import com.umicorp.autolotto.ui.theme.ctaGradient
import com.umicorp.autolotto.ui.theme.heroGradient
import com.umicorp.autolotto.ui.util.ballColor

/**
 * 로또 번호 공 (홈·번호·내역 공유). Lucky Gloss "캔디" 룩: 방사형 하이라이트 + 소프트 그림자 + 림.
 * 공개 API는 100% 기존 그대로 (n, size, selected, dimmed, bordered, onClick) — 화면 수정 불필요.
 *
 * - [selected]=false: 미선택(번호 그리드) — 중립 톤(surfaceVariant), 흐린 광택.
 * - [dimmed]=true: 추첨 후 비당첨(내역) — ballColor 흐림.
 * - [bordered]=true: 보너스 — 강조 림.
 * - onClick != null: 탭 시 프레스 스프링(0.92 → overshoot 1.12 → settle), 선택 시 elevation 상승.
 *
 * ponytail: 공 숫자색 흰색은 ballColor 브랜드 처리의 일부(토큰 예외 = ballColor와 한 묶음).
 */
@Composable
fun LottoBall(
    n: Int,
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
    selected: Boolean = true,
    dimmed: Boolean = false,
    bordered: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val neutral = MaterialTheme.colorScheme.surfaceVariant
    val base by animateColorAsState(
        targetValue = when {
            !selected -> neutral
            dimmed -> lerp(ballColor(n), neutral, 0.55f)
            else -> ballColor(n)
        },
        animationSpec = MotionSpecs.gentle(),
        label = "ballBase",
    )
    val fg = when {
        !selected -> MaterialTheme.colorScheme.onSurfaceVariant
        dimmed -> Color.White.copy(alpha = 0.7f)
        else -> Color.White
    }

    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    // 프레스 = 0.92로 눌림 → 놓으면 bouncy 스프링이 1.0으로 돌며 overshoot(≈1.12) 발생.
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = MotionSpecs.bouncy(),
        label = "ballScale",
    )
    val elevation by animateDpAsState(
        targetValue = if (selected && !dimmed) 5.dp else 1.dp,
        animationSpec = MotionSpecs.gentle(),
        label = "ballElev",
    )

    val highlight = lerp(base, Color.White, 0.65f)
    val rim = lerp(base, Color.Black, if (selected && !dimmed) 0.28f else 0.12f)
    // 강조 림은 볼 색상과 같은 계열(진한 톤) — 파랑볼→진파랑, 노랑볼→진노랑 (사용자 피드백)
    val bonusRim = lerp(ballColor(n), Color.Black, 0.38f)

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(elevation, CircleShape, clip = false)
            .clip(CircleShape)
            .drawBehind {
                val w = this.size.width
                val h = this.size.height
                // 캔디 방사형 셰이딩: 좌상단 하이라이트 → base → 하단 어두운 림.
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(highlight, base, rim),
                        center = Offset(w * 0.35f, h * 0.30f),
                        radius = w * 0.85f,
                    ),
                )
                // 상단 스페큘러 하이라이트.
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color.White.copy(alpha = 0.55f), Color.Transparent),
                        center = Offset(w * 0.36f, h * 0.24f),
                        radius = w * 0.28f,
                    ),
                )
            }
            .then(
                when {
                    bordered -> Modifier.border(2.5.dp, bonusRim, CircleShape)
                    !selected -> Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                    else -> Modifier
                }
            )
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interaction,
                        indication = null,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = n.toString(),
            color = fg,
            fontWeight = FontWeight.Bold,
            fontSize = (size.value * 0.36f).sp,
        )
    }
}

/** 라운드 카드(넉넉한 여백) — 화면 공용. 프로스트 표면(surfaceContainerLowest). */
@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(20.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(contentPadding), content = content)
    }
}

/**
 * 홈 카운트다운용 유기적 "리퀴드" 히어로 (blue→teal→orange 그라디언트).
 * [title]은 상단 라벨(예: "제 1232회 추첨까지"), [content]에 큰 숫자 슬롯을 넣는다. 텍스트는 흰색 기준.
 */
@Composable
fun GradientBlobHero(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    // 좌우 비대칭 라운드 = 유기적 blob 느낌.
    val blob = RoundedCornerShape(topStart = 64.dp, topEnd = 44.dp, bottomStart = 44.dp, bottomEnd = 64.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(10.dp, blob, clip = false)
            .clip(blob)
            .background(heroGradient())
            .padding(horizontal = 24.dp, vertical = 28.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = title,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            content()
        }
    }
}

/**
 * 컬러 드렌치 카드 (예치금=틸, 자동구매=민트). [accent] 그라디언트로 꽉 채운다. 텍스트/아이콘 흰색 기준.
 * [onClick] 지정 시 클릭 가능(예: 예치금 카드 → 상세).
 */
@Composable
fun ColorDrenchedCard(
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = MaterialTheme.shapes.large
    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(4.dp, shape, clip = false)
            .clip(shape)
            .background(Brush.linearGradient(listOf(lerp(accent, Color.White, 0.12f), lerp(accent, Color.Black, 0.18f))))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(20.dp),
    ) {
        Column(content = content)
    }
}

/** 톤얼 카드 — [accent]를 옅게 깐 부드러운 컨테이너(색 드렌치 대신 은은한 강조). 텍스트는 onSurface. */
@Composable
fun TonalCard(
    accent: Color,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(20.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = MaterialTheme.shapes.large
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(lerp(accent, MaterialTheme.colorScheme.surface, 0.82f))
            .border(1.dp, accent.copy(alpha = 0.22f), shape)
            .padding(contentPadding),
        content = content,
    )
}

/** 광택 라운드 스퀘어 아이콘 타일 (설정 행 등). [tint] 그라디언트 위에 흰 아이콘. */
@Composable
fun GlossyIconTile(
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    contentDescription: String? = null,
) {
    val shape = RoundedCornerShape(size.value * 0.32f)
    Box(
        modifier = modifier
            .size(size)
            .shadow(2.dp, shape, clip = false)
            .clip(shape)
            .background(Brush.linearGradient(listOf(lerp(tint, Color.White, 0.28f), tint))),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(size * 0.56f),
        )
    }
}

/** StatusPill 톤: 확인 대기 / 낙첨 / 당첨. */
enum class PillTone { Pending, Lose, Win, Neutral }

/** 상태 토널 필. [tone]에 따라 색을 고른다. 텍스트는 스킴 파생. */
@Composable
fun StatusPill(
    text: String,
    tone: PillTone,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val (bg, fg) = when (tone) {
        PillTone.Win -> scheme.secondaryContainer to scheme.onSecondaryContainer
        PillTone.Lose -> scheme.surfaceVariant to scheme.onSurfaceVariant
        PillTone.Pending -> scheme.primaryContainer to scheme.onPrimaryContainer
        PillTone.Neutral -> scheme.surfaceVariant to scheme.onSurfaceVariant
    }
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(bg)
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Text(
            text = text,
            color = fg,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/** 볼드 섹션 헤더 (이모지 헤더 대체). 화면이 넘기는 이모지도 그대로 표시. */
@Composable
fun SectionHeader(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        modifier = modifier.padding(vertical = 4.dp),
        color = MaterialTheme.colorScheme.onSurface,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.ExtraBold,
    )
}

/** 딥네이비 CTA 그라디언트 버튼 (핵심 액션: 번호 설정/구매). 라벨/아이콘 흰색. */
@Composable
fun CtaButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) {
    val shape = CircleShape
    Row(
        modifier = modifier
            .fillMaxWidth()
            .shadow(if (enabled) 6.dp else 0.dp, shape, clip = false)
            .clip(shape)
            .background(if (enabled) ctaGradient() else Brush.linearGradient(listOf(Color(0xFFB9B4A8), Color(0xFFB9B4A8))))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 18.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** 은은한 크림 페이지 배경(부드러운 피치 blob 느낌). 스크린 루트에 붙일 수 있는 헬퍼. */
@Composable
fun Modifier.creamPageBackground(): Modifier {
    val scheme = MaterialTheme.colorScheme
    return this.background(
        Brush.verticalGradient(
            listOf(
                lerp(scheme.background, Color(0xFFF7D9B8), 0.10f),
                scheme.background,
            ),
        ),
    )
}
