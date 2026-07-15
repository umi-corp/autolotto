@file:OptIn(ExperimentalMaterial3Api::class)

package com.umicorp.autolotto.ui.screen

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.AddCard
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.PauseCircleOutline
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.umicorp.autolotto.R
import com.umicorp.autolotto.ui.appViewModel
import com.umicorp.autolotto.ui.theme.CountdownNumberStyle
import com.umicorp.autolotto.ui.theme.LgAmber
import com.umicorp.autolotto.ui.theme.LgGold
import com.umicorp.autolotto.ui.theme.LgGreen
import com.umicorp.autolotto.ui.theme.LgMuted
import com.umicorp.autolotto.ui.theme.LgTeal
import com.umicorp.autolotto.ui.theme.MotionSpecs
import com.umicorp.autolotto.ui.util.formatNumber
import com.umicorp.autolotto.ui.util.formatPurchaseSchedule
import com.umicorp.autolotto.ui.util.launchActivitySafely
import com.umicorp.autolotto.ui.vm.HomeViewModel
import kotlinx.coroutines.delay
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDateTime

/**
 * 홈 화면 — Lucky Gloss (Material 3 Expressive) 리디자인.
 * 상태·콜백·문자열 리소스는 원본 1:1: 추첨 카운트다운(리퀴드 히어로) · 지난 회차 당첨번호(광택 공,
 * 스태거 팝인) · 예치금 잔액(틸 컬러 드렌치) · 자동구매 상태(그린 톤얼) · 네이비 CTA.
 */
@Composable
fun HomeScreen(modifier: Modifier = Modifier, onNavigateToNumbers: () -> Unit) {
    val vm: HomeViewModel = appViewModel()
    val isLoggedIn by vm.isLoggedIn.collectAsState()
    val balance by vm.balance.collectAsState()
    val autoEnabled by vm.autoEnabled.collectAsState()
    val day by vm.autoPurchaseDay.collectAsState()
    val hour by vm.autoPurchaseHour.collectAsState()
    val minute by vm.autoPurchaseMinute.collectAsState()
    val alertEnabled by vm.balanceAlertEnabled.collectAsState()
    val threshold by vm.balanceAlertThreshold.collectAsState()
    val winning by vm.winning.collectAsState()
    val loadingNumbers by vm.loadingNumbers.collectAsState()

    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.appTitle), fontWeight = FontWeight.ExtraBold) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent),
                actions = {
                    Icon(
                        imageVector = if (isLoggedIn) Icons.Rounded.CloudDone else Icons.Rounded.CloudOff,
                        contentDescription = stringResource(
                            if (isLoggedIn) R.string.statusLoggedIn else R.string.statusLoginRequired,
                        ),
                        tint = if (isLoggedIn) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 16.dp),
                    )
                },
            )
        },
    ) { inner ->
        PullToRefreshBox(
            isRefreshing = loadingNumbers,
            onRefresh = { vm.refreshAll() },
            modifier = Modifier.padding(inner),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .creamPageBackground()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
            ) {
                CountdownHero(vm.currentRound)
                Spacer(Modifier.height(24.dp))
                WinningNumbers(winning, loadingNumbers)
                Spacer(Modifier.height(24.dp))
                BalanceCard(balance, alertEnabled, threshold)
                Spacer(Modifier.height(16.dp))
                AutoStatusCard(autoEnabled, day, hour, minute)
                Spacer(Modifier.height(20.dp))
                CtaButton(onClick = onNavigateToNumbers) {
                    Text(
                        stringResource(R.string.buttonSetupNumbers),
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

/** 다음 토요일 20:45까지 남은 시간(기기 로컬시각, 원본 _updateCountdown 1:1). */
private fun durationUntilDraw(now: LocalDateTime): Duration {
    var date = now.toLocalDate()
    while (date.dayOfWeek != DayOfWeek.SATURDAY) date = date.plusDays(1)
    var draw = date.atTime(20, 45)
    if (now.isAfter(draw)) draw = draw.plusWeeks(1)
    return Duration.between(now, draw)
}

@Composable
private fun CountdownHero(round: Int) {
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            delay(1000)
        }
    }
    val left = durationUntilDraw(now)
    val days = left.toDays()
    val hours = left.toHours() % 24
    val mins = left.toMinutes() % 60
    val secs = left.seconds % 60

    GradientBlobHero(title = stringResource(R.string.countdownTitle, round)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.Top,
        ) {
            CountdownUnit(days.toString(), stringResource(R.string.countdownDays))
            CountdownUnit(hours.toString(), stringResource(R.string.countdownHours))
            CountdownUnit(mins.toString(), stringResource(R.string.countdownMinutes))
            CountdownUnit(
                secs.toString().padStart(2, '0'),
                stringResource(R.string.countdownSeconds),
                animated = true,
            )
        }
    }
}

/** 히어로 카운트다운 숫자 슬롯. [animated]=true(초)면 틱마다 AnimatedContent 슬라이드-페이드. */
@Composable
private fun CountdownUnit(value: String, label: String, animated: Boolean = false) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (animated) {
            AnimatedContent(
                targetState = value,
                transitionSpec = {
                    (slideInVertically(MotionSpecs.emphasized()) { it / 2 } + fadeIn(MotionSpecs.emphasized()))
                        .togetherWith(
                            slideOutVertically(MotionSpecs.emphasized()) { -it / 2 } +
                                fadeOut(MotionSpecs.emphasized()),
                        )
                },
                label = "secondsTick",
            ) { v ->
                Text(v, color = Color.White, style = CountdownNumberStyle)
            }
        } else {
            Text(value, color = Color.White, style = CountdownNumberStyle)
        }
        Text(
            label,
            color = Color.White.copy(alpha = 0.8f),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/** 스태거 팝인 래퍼 — [key]가 바뀌면(새 회차 로드) index 순서대로 bouncy 스케일 등장. */
@Composable
private fun PopIn(index: Int, key: Any, content: @Composable () -> Unit) {
    val scale = remember(key) { Animatable(0f) }
    LaunchedEffect(key) {
        delay(MotionSpecs.staggerDelay(index).toLong())
        scale.animateTo(1f, MotionSpecs.bouncy())
    }
    Box(
        Modifier.graphicsLayer {
            scaleX = scale.value
            scaleY = scale.value
        },
    ) { content() }
}

@Composable
private fun WinningNumbers(winning: com.umicorp.autolotto.data.WinningResult?, loading: Boolean) {
    Column {
        SectionHeader(
            // 이모지는 R.string에 이미 포함(🏆 …) — 코드에서 덧붙이면 중복 표시
            text = if (winning != null) stringResource(R.string.winningNumbersWithRound, winning.round)
            else stringResource(R.string.winningNumbersPrevious),
        )
        Spacer(Modifier.height(8.dp))
        SectionCard {
            // 로딩→당첨번호→에러 전환을 부드럽게(표준 Crossfade — 1.4.0엔 Expressive 컴포넌트 미수록).
            val phase = if (loading) 0 else if (winning != null) 1 else 2
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Crossfade(targetState = phase, label = "winningNumbers") { p ->
                    when (p) {
                        0 -> CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                        )
                        1 -> winning?.let { w ->
                            // 좁은 기기(유효 폭 ≤400dp, 예: 갤럭시 기본 384dp)에서 38dp 고정 볼이
                            // 카드 폭을 넘어 보너스 볼이 잘림 — 가용 폭에 맞춰 볼 크기 축소.
                            // 하한 없음: 극단 폭(분할 화면 등)에서도 잘리는 대신 계속 줄어든다.
                            BoxWithConstraints {
                                val plusWidth = 20.dp
                                val balls = w.numbers.size + 1 // 본번호 + 보너스
                                val ball = ((maxWidth - plusWidth - 6.dp * balls) / balls)
                                    .coerceIn(0.dp, 38.dp)
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    w.numbers.forEachIndexed { i, n ->
                                        PopIn(index = i, key = w.round) { LottoBall(n, size = ball) }
                                    }
                                    // 고정 폭 슬롯 — 폰트 스케일이 커져도 산식이 예약한 폭과 어긋나지 않게
                                    Box(Modifier.width(plusWidth), contentAlignment = Alignment.Center) {
                                        Text(
                                            "+",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                        )
                                    }
                                    PopIn(index = w.numbers.size, key = w.round) {
                                        LottoBall(w.bonus, size = ball, bordered = true)
                                    }
                                }
                            }
                        }
                        else -> Text(
                            stringResource(R.string.winningNumbersLoadError),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BalanceCard(balance: Int, alertEnabled: Boolean, threshold: Int) {
    val context = LocalContext.current
    val isLow = alertEnabled && balance <= threshold && balance > 0
    ColorDrenchedCard(accent = if (isLow) LgAmber else LgTeal) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GlossyIconTile(
                icon = Icons.Rounded.AccountBalanceWallet,
                tint = LgGold,
                size = 44.dp,
            )
            Spacer(Modifier.width(14.dp))
            Column {
                Text(
                    stringResource(R.string.balanceTitle),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White.copy(alpha = 0.85f),
                )
                Text(
                    "₩${formatNumber(balance)}",
                    style = MaterialTheme.typography.headlineMedium,
                    // ponytail: Black(900)은 ₩ 폴백 글리프가 합성볼드로 겹쳐 그려짐(취소선처럼 보임) — Bold로 회피
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
            }
        }
        if (isLow) {
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = {
                    context.launchActivitySafely(
                        Intent(Intent.ACTION_VIEW, Uri.parse("https://www.dhlottery.co.kr/mypage/mndpChrg")),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.7f)),
            ) {
                Icon(Icons.Rounded.AddCard, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.chargeNow), fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun AutoStatusCard(autoEnabled: Boolean, day: Int, hour: Int, minute: Int) {
    val subtitle = if (autoEnabled) stringResource(
        R.string.autoPurchaseSchedule,
        formatPurchaseSchedule(day, hour, minute),
    ) else stringResource(R.string.enableInSettings)
    val accent = if (autoEnabled) LgGreen else LgMuted
    TonalCard(accent = accent) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GlossyIconTile(
                icon = if (autoEnabled) Icons.Rounded.Autorenew else Icons.Rounded.PauseCircleOutline,
                tint = accent,
                size = 44.dp,
            )
            Spacer(Modifier.width(14.dp))
            Column {
                Text(
                    if (autoEnabled) stringResource(R.string.autoPurchaseEnabled)
                    else stringResource(R.string.autoPurchaseDisabled),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
