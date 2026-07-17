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
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.mutableIntStateOf
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
import com.umicorp.autolotto.ui.util.formatNumber
import com.umicorp.autolotto.ui.util.formatPurchaseSchedule
import com.umicorp.autolotto.ui.vm.NumberViewModel
import com.umicorp.autolotto.ui.vm.NumberViewModel.InstantState
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
    val isLoggedIn by vm.isLoggedIn.collectAsState()
    val lastRound by vm.lastPurchasedRound.collectAsState()
    val instantState by vm.instantState.collectAsState()

    // 편집용 로컬 상태(원본 _NumberScreenState).
    val games = remember { mutableStateListOf<List<Int>?>(null, null, null, null, null) }
    var initialized by remember { mutableStateOf(false) }
    var currentSlot by remember { mutableStateOf(0) }
    var isAuto by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(emptySet<Int>()) }
    var saved by remember { mutableStateOf(true) }

    // 저장된 5슬롯을 1회 하이드레이트(원본 initState._loadSavedGames). 실데이터 도착 시 잠근다.
    // ponytail: 전부 미설정이면 둘 다 all-null이라 구분 불가 → 그 경우만 반복 복사(무해, 이미 all-null).
    // 슬롯 진입 시 저장 상태를 편집 상태로 동기화 — 토글(자동/수동)과 수동 볼 선택 표시까지 (사용자 피드백).
    fun syncEditorToSlot() {
        isAuto = games[currentSlot]?.isEmpty() == true
        selected = games[currentSlot]?.toSet() ?: emptySet()
    }

    LaunchedEffect(loaded) {
        if (!initialized) {
            for (i in 0 until 5) games[i] = loaded.getOrNull(i)
            currentSlot = (0 until 5).firstOrNull { games[it] == null } ?: 0
            syncEditorToSlot()
            if (loaded.any { it != null }) initialized = true
        }
    }

    val configuredCount = games.count { it != null }
    val context = LocalContext.current
    val schedule = formatPurchaseSchedule(day, hour, minute)
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    // 확정 버튼 팝(눌림 0.92 → 오버슛 복귀) — 확정이 접수됐다는 즉각 피드백 (사용자 피드백).
    val confirmPop = remember { Animatable(1f) }

    // 즉시 구매: 저장된 게임 0개면 설정·저장 안내(이미 이 화면이므로 이동 없이 스낵바).
    // 스낵바는 scope로 분리 — dismiss가 상태를 바꿔 이 이펙트가 재시작(=취소)돼도 표시가 살아남는다.
    LaunchedEffect(instantState) {
        if (instantState is InstantState.NeedsSetup) {
            scope.launch { snackbar.showSnackbar(context.getString(R.string.instantNeedsSetup)) }
            vm.dismissInstant()
        }
    }
    InstantPurchaseDialogs(instantState, vm)

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
        saved = false
        if (currentSlot < 4) currentSlot++
        syncEditorToSlot()
        haptic.performHapticFeedback(HapticFeedbackType.Confirm)
        scope.launch { confirmPop.snapTo(0.92f); confirmPop.animateTo(1f, MotionSpecs.bouncy()) }
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
                // 같은 슬롯 재탭은 무시 — 재동기화로 편집 중 선택이 리셋되는 것 방지 (P6).
                onSelect = { if (it != currentSlot) { currentSlot = it; syncEditorToSlot() } },
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
            // 토글은 보기 전환일 뿐 — 확정 전엔 선택을 파괴하지 않는다(자동↔수동 왕복 보존, P5).
            ModeToggle(
                isAuto = isAuto,
                onChange = { auto -> isAuto = auto },
            )
            Spacer(Modifier.height(16.dp))

            if (!isAuto) {
                // 확정 전 선택은 저장 대상이 아니므로 saved는 건드리지 않는다(P4).
                NumberGrid(selected = selected, onToggle = { n ->
                    selected = when {
                        n in selected -> selected - n
                        selected.size < 6 -> selected + n
                        else -> selected
                    }
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
                    modifier = Modifier.weight(2f).graphicsLayer {
                        scaleX = confirmPop.value
                        scaleY = confirmPop.value
                    },
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
                onRemove = { i ->
                    games[i] = null
                    saved = false
                    // 현재 슬롯을 지우면 편집기(토글·그리드)도 함께 비운다(P1).
                    if (i == currentSlot) syncEditorToSlot()
                },
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
            // 0게임(구매 안 함)도 유효한 저장 — 비활성화하면 전부 삭제가 저장소에 반영될 길이 없다(P2).
            Button(
                onClick = {
                    val count = games.count { it != null }
                    vm.saveConfig(games.toList())
                    saved = true
                    scope.launch {
                        snackbar.showSnackbar(
                            if (count == 0) context.getString(R.string.snackbarSaveEmpty)
                            else context.getString(R.string.snackbarSaveSuccess, count, schedule),
                        )
                    }
                },
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
                    when {
                        saved -> stringResource(R.string.buttonSaveDone)
                        configuredCount == 0 -> stringResource(R.string.buttonSaveEmpty)
                        else -> stringResource(R.string.buttonSaveGames, configuredCount)
                    },
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(12.dp))

            // 즉시 구매 — 저장된 슬롯으로 지금 결제(설정 → 저장 → 구매 흐름). 미저장 변경은 저장 유도.
            InstantPurchaseCta(
                isLoggedIn = isLoggedIn,
                purchasedThisRound = lastRound >= vm.currentRound,
                saleOpen = vm.isSaleOpenNow(),
                onTap = {
                    // 저장 상태 요구는 첫 구매만 — 추가 구매(자동 N게임)는 저장 슬롯을 쓰지 않는다.
                    if (!saved && lastRound < vm.currentRound) {
                        scope.launch { snackbar.showSnackbar(context.getString(R.string.instantNeedsSetup)) }
                    } else {
                        vm.onInstantTap()
                    }
                },
            )
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
                .background(Color.Black.copy(alpha = 0.30f))
                // 연출 중 아래 컨트롤로 터치가 통과하지 않도록 스크림이 입력을 소비(P3).
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {},
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
                // 확정 시 체크가 바운스로 팝인 — 상단에서도 확정 접수가 보이게.
                AnimatedVisibility(
                    configured,
                    enter = scaleIn(MotionSpecs.bouncy()) + fadeIn(),
                    exit = scaleOut() + fadeOut(),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Rounded.Check,
                            null,
                            tint = if (isCurrent) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(2.dp))
                    }
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

/** 즉시 구매 프라이머리 CTA. 비활성 사유(로그인 > 판매시간)를 라벨로 표시, 구매완료 회차엔 "추가 구매". */
@Composable
private fun InstantPurchaseCta(
    isLoggedIn: Boolean,
    purchasedThisRound: Boolean,
    saleOpen: Boolean,
    onTap: () -> Unit,
) {
    val disabledLabel = when {
        !isLoggedIn -> stringResource(R.string.hintLoginRequired)
        !saleOpen -> stringResource(R.string.instantNotSaleTime)
        else -> null
    }
    CtaButton(onClick = onTap, enabled = disabledLabel == null) {
        Text(
            disabledLabel ?: stringResource(
                if (purchasedThisRound) R.string.buttonExtraPurchase else R.string.buttonInstantPurchase,
            ),
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** 즉시 구매 다이얼로그 — InstantState별 확인/게임수선택/진행/결과/에러. */
@Composable
private fun InstantPurchaseDialogs(state: InstantState, vm: NumberViewModel) {
    when (state) {
        is InstantState.ConfirmingFirst -> AlertDialog(
            onDismissRequest = { vm.dismissInstant() },
            title = { Text(stringResource(R.string.instantConfirmTitle)) },
            text = {
                Text(
                    stringResource(
                        R.string.instantConfirmBody,
                        state.round, state.games, formatNumber(state.games * 1000),
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = { vm.confirmFirst() }) { Text(stringResource(R.string.buttonConfirm)) }
            },
            dismissButton = {
                TextButton(onClick = { vm.dismissInstant() }) { Text(stringResource(R.string.buttonCancel)) }
            },
        )
        is InstantState.PickingExtra -> {
            var games by remember { mutableIntStateOf(1) }
            AlertDialog(
                onDismissRequest = { vm.dismissInstant() },
                title = { Text(stringResource(R.string.extraPickTitle)) },
                text = {
                    Column {
                        Text(stringResource(R.string.extraPickBody), style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            (1..5).forEach { n ->
                                FilterChip(selected = games == n, onClick = { games = n }, label = { Text("$n") })
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(
                            stringResource(
                                R.string.instantConfirmBody,
                                state.round, games, formatNumber(games * 1000),
                            ),
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { vm.confirmExtra(games) }) { Text(stringResource(R.string.buttonConfirm)) }
                },
                dismissButton = {
                    TextButton(onClick = { vm.dismissInstant() }) { Text(stringResource(R.string.buttonCancel)) }
                },
            )
        }
        is InstantState.InProgress -> AlertDialog(
            onDismissRequest = {},                                  // 진행 중 닫기 금지
            title = { Text(stringResource(R.string.instantConfirmTitle)) },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.instantInProgress))
                }
            },
            confirmButton = {},
        )
        is InstantState.Success -> AlertDialog(
            onDismissRequest = { vm.dismissInstant() },
            title = { Text(stringResource(R.string.instantSuccessTitle)) },
            text = {
                Column {
                    Text(stringResource(R.string.instantSuccessBody, state.result.round, state.result.totalGames))
                    Spacer(Modifier.height(12.dp))
                    state.result.numbers.forEach { game ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(vertical = 2.dp),
                        ) {
                            game.forEach { n -> LottoBall(n, size = 28.dp) }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { vm.dismissInstant() }) { Text(stringResource(R.string.buttonConfirm)) }
            },
        )
        is InstantState.AlreadyPurchased -> InstantNoticeDialog(
            title = stringResource(R.string.instantConfirmTitle),
            text = stringResource(R.string.instantAlreadyPurchased),
            onDismiss = { vm.dismissInstant() },
        )
        is InstantState.SaleClosed -> InstantNoticeDialog(
            title = stringResource(R.string.instantConfirmTitle),
            text = stringResource(R.string.instantNotSaleTime),
            onDismiss = { vm.dismissInstant() },
        )
        is InstantState.RoundChanged -> InstantNoticeDialog(
            title = stringResource(R.string.instantConfirmTitle),
            text = stringResource(R.string.instantRoundChanged),
            onDismiss = { vm.dismissInstant() },
        )
        is InstantState.Error -> InstantNoticeDialog(
            title = stringResource(R.string.instantErrorTitle),
            text = if (state.unknown) stringResource(R.string.instantUnknownResult)
            else state.message ?: stringResource(R.string.instantErrorFallback),
            onDismiss = { vm.dismissInstant() },
        )
        InstantState.Idle, InstantState.NeedsSetup -> Unit
    }
}

/** 공지성 다이얼로그 공통 스켈레톤(제목·본문·확인 버튼) — 상태별 문구만 다르다. */
@Composable
private fun InstantNoticeDialog(title: String, text: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.buttonConfirm)) }
        },
    )
}
