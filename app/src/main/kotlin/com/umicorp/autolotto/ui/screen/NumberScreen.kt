@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.umicorp.autolotto.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Casino
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.umicorp.autolotto.R
import com.umicorp.autolotto.ui.appViewModel
import com.umicorp.autolotto.ui.theme.LgAmber
import com.umicorp.autolotto.ui.theme.LgTeal
import com.umicorp.autolotto.ui.theme.MotionSpecs
import com.umicorp.autolotto.ui.util.formatPurchaseSchedule
import com.umicorp.autolotto.ui.vm.NumberViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 자동 구매 번호 설정 (원본 number_screen.dart 1:1) — Lucky Gloss 리디자인.
 *
 * 5슬롯 모델(null=미설정 / emptyList=자동 / [6수동번호]). 슬롯별 수동/자동 토글, 1~45 그리드(6개 제한),
 * 게임 요약, 저장 → SecureStore manual_numbers(JSON 5슬롯). 인코딩은 AppContainer.saveManualGames와 동일.
 *
 * 시그니처 모션: 공 프레스/선택 스프링(LottoBall 내장) + 수동/자동 토글 인디케이터 bouncy 슬라이드.
 */
@Composable
fun NumberScreen(modifier: Modifier = Modifier) {
    val vm: NumberViewModel = appViewModel()
    val autoEnabled by vm.autoEnabled.collectAsState()
    val loaded by vm.games.collectAsState()
    val day by vm.autoPurchaseDay.collectAsState()
    val hour by vm.autoPurchaseHour.collectAsState()
    val minute by vm.autoPurchaseMinute.collectAsState()

    // 편집용 로컬 상태(원본 _NumberScreenState).
    val games = remember { mutableStateListOf<List<Int>?>(null, null, null, null, null) }
    var initialized by remember { mutableStateOf(false) }
    var currentSlot by remember { mutableStateOf(0) }
    var isAuto by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(emptySet<Int>()) }
    var saved by remember { mutableStateOf(true) }

    // 저장된 5슬롯을 1회 하이드레이트(원본 initState._loadSavedGames). 실데이터 도착 시 잠근다.
    // ponytail: 전부 미설정이면 둘 다 all-null이라 구분 불가 → 그 경우만 반복 복사(무해, 이미 all-null).
    LaunchedEffect(loaded) {
        if (!initialized) {
            for (i in 0 until 5) games[i] = loaded.getOrNull(i)
            currentSlot = (0 until 5).firstOrNull { games[it] == null } ?: 0
            if (loaded.any { it != null }) initialized = true
        }
    }

    val configuredCount = games.count { it != null }
    val context = LocalContext.current
    val schedule = formatPurchaseSchedule(day, hour, minute)
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    // 전부 자동: 주사위 텀블 연출(~0.95s) 후 실제 적용 — 즉시 적용은 밋밋(사용자 피드백).
    var rollingAllAuto by remember { mutableStateOf(false) }
    LaunchedEffect(rollingAllAuto) {
        if (rollingAllAuto) {
            delay(950)
            for (i in 0 until 5) games[i] = emptyList()
            selected = emptySet(); isAuto = true; saved = false
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            rollingAllAuto = false
        }
    }

    fun confirmSlot() {
        if (!isAuto && selected.size != 6) return
        games[currentSlot] = if (isAuto) emptyList() else selected.sorted()
        selected = emptySet()
        saved = false
        if (currentSlot < 4) {
            currentSlot++
            isAuto = false
        }
    }

    Box(modifier) {
    Scaffold(
        modifier = Modifier.fillMaxSize().creamPageBackground(),
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        stringResource(R.string.numberSetupTitle),
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
        ) {
            if (!autoEnabled) {
                TonalCard(accent = LgAmber, contentPadding = PaddingValues(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Rounded.WarningAmber,
                            null,
                            tint = LgAmber,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.bannerEnableAutoPurchase),
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
            TonalCard(accent = LgTeal, contentPadding = PaddingValues(16.dp)) {
                Text(
                    stringResource(R.string.numberSetupInstruction),
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Spacer(Modifier.height(16.dp))

            SlotTabs(
                games = games,
                currentSlot = currentSlot,
                onSelect = { currentSlot = it; selected = emptySet(); isAuto = false },
            )
            Spacer(Modifier.height(4.dp))

            // 전부 자동
            val allAuto = games.all { it != null && it.isEmpty() }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(
                    onClick = { rollingAllAuto = true },
                    enabled = !allAuto && !rollingAllAuto,
                ) {
                    Icon(Icons.Rounded.AutoAwesome, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.buttonAllAuto), fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(4.dp))

            // 수동/자동 토글 — bouncy 스프링 슬라이드 인디케이터.
            ModeToggle(
                isAuto = isAuto,
                onChange = { auto ->
                    isAuto = auto
                    if (auto) selected = emptySet()
                },
            )
            Spacer(Modifier.height(16.dp))

            if (!isAuto) {
                NumberGrid(selected = selected, onToggle = { n ->
                    selected = when {
                        n in selected -> selected - n
                        selected.size < 6 -> selected + n
                        else -> selected
                    }
                    saved = false
                })
                Spacer(Modifier.height(12.dp))
                SelectedNumbers(selected)
            } else {
                AutoSlotCard()
            }
            Spacer(Modifier.height(24.dp))

            // 확정 / 초기화
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = { selected = emptySet(); games[currentSlot] = null; isAuto = false; saved = false },
                    modifier = Modifier.weight(1f).height(56.dp),
                    shape = CircleShape,
                ) { Text(stringResource(R.string.buttonReset), fontWeight = FontWeight.SemiBold) }
                CtaButton(
                    onClick = { confirmSlot() },
                    enabled = isAuto || selected.size == 6,
                    modifier = Modifier.weight(2f),
                ) {
                    Text(
                        stringResource(R.string.buttonConfirmGame, ('A' + currentSlot).toString()),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Spacer(Modifier.height(24.dp))

            GameSummary(
                games = games,
                currentSlot = currentSlot,
                onRemove = { i -> games[i] = null; saved = false },
            )
            Spacer(Modifier.height(16.dp))

            // 저장 — 저장 완료/대기 색 전환을 부드럽게(animateColorAsState).
            val saveContainer by animateColorAsState(
                if (saved) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                label = "saveContainer",
            )
            val saveContent by animateColorAsState(
                if (saved) MaterialTheme.colorScheme.onTertiary else MaterialTheme.colorScheme.onPrimary,
                label = "saveContent",
            )
            Button(
                onClick = {
                    val count = games.count { it != null }
                    vm.saveConfig(games.toList())
                    saved = true
                    scope.launch {
                        snackbar.showSnackbar(context.getString(R.string.snackbarSaveSuccess, count, schedule))
                    }
                },
                enabled = configuredCount > 0,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = saveContainer,
                    contentColor = saveContent,
                ),
            ) {
                Icon(if (saved) Icons.Rounded.CheckCircle else Icons.Rounded.Save, null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    if (saved) stringResource(R.string.buttonSaveDone)
                    else stringResource(R.string.buttonSaveGames, configuredCount),
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
    // 스캐폴드 위에 겹치는 풀스크린 오버레이(스크림+주사위 텀블) — 같은 Box 자식이라 최상단에 그려진다.
    DiceRollOverlay(visible = rollingAllAuto)
    }
}

/** 전부 자동 주사위 텀블 오버레이 — 스크림 위에서 주사위 타일이 회전·바운스하며 착지(~0.95s). */
@Composable
private fun DiceRollOverlay(visible: Boolean) {
    AnimatedVisibility(visible, enter = fadeIn(), exit = fadeOut()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.30f)),
            contentAlignment = Alignment.Center,
        ) {
            val rotation = remember { Animatable(-540f) }
            val scale = remember { Animatable(0.3f) }
            LaunchedEffect(Unit) {
                launch { scale.animateTo(1f, MotionSpecs.bouncy()) }
                rotation.animateTo(0f, tween(900, easing = FastOutSlowInEasing))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.graphicsLayer {
                        rotationZ = rotation.value
                        scaleX = scale.value
                        scaleY = scale.value
                    },
                ) { GlossyIconTile(icon = Icons.Rounded.Casino, tint = LgTeal, size = 88.dp) }
                Spacer(Modifier.height(16.dp))
                Text(
                    stringResource(R.string.rollingAllAuto),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}

/** A~E 게임 슬롯 칩 — 흰 필 + 설정 완료 체크, 선택 슬롯은 틸 컨테이너 틴트. */
@Composable
private fun SlotTabs(
    games: List<List<Int>?>,
    currentSlot: Int,
    onSelect: (Int) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        for (i in 0 until 5) {
            val isCurrent = i == currentSlot
            val configured = games[i] != null
            val bg by animateColorAsState(
                if (isCurrent) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainerLowest,
                animationSpec = MotionSpecs.gentle(),
                label = "slotBg",
            )
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clip(CircleShape)
                    .background(bg)
                    .border(
                        1.dp,
                        if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                        else MaterialTheme.colorScheme.outlineVariant,
                        CircleShape,
                    )
                    .clickable { onSelect(i) },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (configured) {
                    Icon(
                        Icons.Rounded.Check,
                        null,
                        tint = if (isCurrent) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(2.dp))
                }
                Text(
                    ('A' + i).toString(),
                    color = if (isCurrent) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleSmall,
                )
            }
        }
    }
}

/** 수동/자동 세그먼트 토글 — 민트 인디케이터가 bouncy 스프링으로 슬라이드. */
@Composable
private fun ModeToggle(isAuto: Boolean, onChange: (Boolean) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(CircleShape)
            .background(scheme.surfaceContainerLowest)
            .border(1.dp, scheme.outlineVariant, CircleShape),
    ) {
        val half = maxWidth / 2
        val indicatorX by animateDpAsState(
            targetValue = if (isAuto) half else 0.dp,
            animationSpec = MotionSpecs.bouncy(),
            label = "modeIndicator",
        )
        Box(
            Modifier
                .offset(x = indicatorX)
                .width(half)
                .fillMaxHeight()
                .padding(4.dp)
                .clip(CircleShape)
                .background(
                    Brush.horizontalGradient(
                        listOf(lerp(LgTeal, Color.White, 0.80f), lerp(LgTeal, Color.White, 0.60f)),
                    ),
                )
                .border(1.dp, LgTeal.copy(alpha = 0.35f), CircleShape),
        )
        Row(Modifier.fillMaxSize()) {
            ModeLabel(
                text = stringResource(R.string.modeManual), // 이모지는 R.string에 포함
                active = !isAuto,
                onClick = { onChange(false) },
                modifier = Modifier.weight(1f),
            )
            ModeLabel(
                text = stringResource(R.string.modeAuto),
                active = isAuto,
                onClick = { onChange(true) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ModeLabel(text: String, active: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val color by animateColorAsState(
        if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = MotionSpecs.gentle(),
        label = "modeLabel",
    )
    Box(
        modifier
            .fillMaxHeight()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = color, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
    }
}

/** 1~45 글로시 볼 6열 그리드 — 탭 시 LottoBall 내장 프레스/오버슛 스프링. */
@Composable
private fun NumberGrid(selected: Set<Int>, onToggle: (Int) -> Unit) {
    SectionCard(contentPadding = PaddingValues(14.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val gap = 8.dp
            val ballSize = (maxWidth - gap * 5) / 6
            FlowRow(
                maxItemsInEachRow = 6,
                horizontalArrangement = Arrangement.spacedBy(gap),
                verticalArrangement = Arrangement.spacedBy(gap),
            ) {
                for (n in 1..45) {
                    LottoBall(
                        n = n,
                        size = ballSize,
                        selected = n in selected,
                        onClick = { onToggle(n) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SelectedNumbers(selected: Set<Int>) {
    SectionCard(contentPadding = PaddingValues(16.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.selectionCount, selected.size),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.width(8.dp))
            selected.sorted().forEach {
                LottoBall(it, size = 36.dp, modifier = Modifier.padding(end = 6.dp))
            }
        }
    }
}

@Composable
private fun AutoSlotCard() {
    SectionCard(contentPadding = PaddingValues(32.dp)) {
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            GlossyIconTile(
                icon = Icons.Rounded.Casino,
                tint = LgTeal,
                size = 56.dp,
            )
            Spacer(Modifier.height(14.dp))
            Text(
                stringResource(R.string.autoNumberTitle),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.autoNumberSubtitle),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun GameSummary(
    games: List<List<Int>?>,
    currentSlot: Int,
    onRemove: (Int) -> Unit,
) {
    SectionCard {
        SectionHeader(stringResource(R.string.gameSummaryTitle))
        Spacer(Modifier.height(8.dp))
        for (i in 0 until 5) {
            val g = games[i]
            val confirmed = g != null
            val selecting = g == null && i == currentSlot
            val label = when {
                g == null -> if (selecting) stringResource(R.string.gameSummarySelecting)
                else stringResource(R.string.gameSummaryNotSet)
                g.isEmpty() -> stringResource(R.string.gameSummaryAuto)
                else -> g.joinToString(", ")
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(
                            if (confirmed) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        ('A' + i).toString(),
                        color = if (confirmed) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                Spacer(Modifier.width(12.dp))
                if (selecting) {
                    // "지금 설정 중" 라이브 펄스 닷 — 정적 말줄임표("선택 중...") 대체(사용자 피드백).
                    val pulse by rememberInfiniteTransition(label = "nowPulse").animateFloat(
                        initialValue = 0.25f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(tween(650), RepeatMode.Reverse),
                        label = "nowPulseAlpha",
                    )
                    Box(
                        Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(LgTeal.copy(alpha = pulse)),
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    label,
                    modifier = Modifier.weight(1f),
                    color = when {
                        selecting -> LgTeal
                        confirmed -> MaterialTheme.colorScheme.onSurface
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (confirmed || selecting) FontWeight.SemiBold else FontWeight.Normal,
                )
                if (confirmed) {
                    // 터치 타깃 확대(18dp→40dp) + contentDescription(접근성).
                    IconButton(onClick = { onRemove(i) }, modifier = Modifier.size(40.dp)) {
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.cdRemoveGame),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}
