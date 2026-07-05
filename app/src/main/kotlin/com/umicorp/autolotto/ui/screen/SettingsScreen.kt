@file:OptIn(ExperimentalMaterial3Api::class)

package com.umicorp.autolotto.ui.screen

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.AddCard
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ConfirmationNumber
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MonetizationOn
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.umicorp.autolotto.BuildConfig
import com.umicorp.autolotto.R
import com.umicorp.autolotto.scheduler.ExactAlarmPermission
import com.umicorp.autolotto.ui.appViewModel
import com.umicorp.autolotto.ui.theme.LgAmber
import com.umicorp.autolotto.ui.theme.LgCtaNavyEnd
import com.umicorp.autolotto.ui.theme.LgGold
import com.umicorp.autolotto.ui.theme.LgGreen
import com.umicorp.autolotto.ui.theme.LgInk
import com.umicorp.autolotto.ui.theme.LgTeal
import com.umicorp.autolotto.ui.theme.LgTealInk
import com.umicorp.autolotto.ui.theme.MotionSpecs
import com.umicorp.autolotto.ui.util.formatNumber
import com.umicorp.autolotto.ui.util.launchActivitySafely
import com.umicorp.autolotto.ui.util.formatPurchaseSchedule
import com.umicorp.autolotto.ui.util.localizedDayNames
import com.umicorp.autolotto.ui.vm.SettingsViewModel
import com.umicorp.autolotto.ui.vm.SettingsViewModel.LoginState
import kotlinx.coroutines.launch

/**
 * 설정 화면 (원본 settings_screen.dart 1:1).
 *
 * 섹션: 계정(로그인/로그아웃·잔액) · 자동구매(on/off·요일·시각·배터리최적화) · 알림(구매/결과/잔액부족·권한) ·
 * 앱정보(버전·언어·오픈소스·초기화) · 후원. 모든 영속 쓰기와 알람 스케줄은 [SettingsViewModel]→AppContainer가
 * 단일 출처로 처리(플로우+SecureStore 양방향). 화면은 상태 구독 + 액션 호출 + 다이얼로그/스낵바만 담당.
 *
 * 룩은 Lucky Gloss(M3 Expressive): 크림 배경 + 프로스트 [SectionCard] + [GlossyIconTile] 캔디 아이콘 +
 * ui.theme Lg* 토큰. 시그니처 모션 = 자동구매/잔액알림 토글 시 종속 행 animateContentSize 스프링 확장.
 * 권한 섹션(POST_NOTIFICATIONS·정확알람)은 원본엔 없지만 네이티브 백그라운드
 * 신뢰성을 위해 추가(DESIGN §11에서 Slice 5 UI로 보류했던 런타임 권한 유도).
 */
@Composable
fun SettingsScreen(modifier: Modifier = Modifier, onNavigateToNumbers: () -> Unit) {
    val vm: SettingsViewModel = appViewModel()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val isLoggedIn by vm.isLoggedIn.collectAsState()
    val balance by vm.balance.collectAsState()
    val loggedInUserId by vm.loggedInUserId.collectAsState()
    val autoEnabled by vm.autoEnabled.collectAsState()
    val autoGames by vm.autoGames.collectAsState()
    val day by vm.autoPurchaseDay.collectAsState()
    val hour by vm.autoPurchaseHour.collectAsState()
    val minute by vm.autoPurchaseMinute.collectAsState()
    val alertEnabled by vm.balanceAlertEnabled.collectAsState()
    val threshold by vm.balanceAlertThreshold.collectAsState()
    val language by vm.language.collectAsState()
    val loginState by vm.loginState.collectAsState()

    // 알림 토글(원본 _purchaseNoti/_resultNoti) — 화면-로컬, 비영속(원본과 동일하게 표시용).
    var purchaseNoti by remember { mutableStateOf(true) }
    var resultNoti by remember { mutableStateOf(true) }

    // 다이얼로그 토글.
    var showLogin by remember { mutableStateOf(false) }
    var showReset by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showThreshold by remember { mutableStateOf(false) }

    // 로그인 결과 → 스낵바(원본 _login의 try/catch 스낵바). consume 후 Idle 복귀.
    LaunchedEffect(loginState) {
        val msg = when (loginState) {
            LoginState.Success -> context.getString(R.string.snackbarLoginSuccess)
            LoginState.InvalidCredentials -> "❌ " + context.getString(R.string.errorInvalidCredentials)
            LoginState.Error -> "❌ " + context.getString(R.string.snackbarLoginFailed)
            else -> null
        }
        if (msg != null) {
            // show 먼저, consume 나중 — consume이 loginState(=LaunchedEffect 키)를 바꿔 스낵바 코루틴을 취소하지 않도록.
            snackbar.showSnackbar(msg)
            vm.consumeLoginState()
        }
    }

    fun toast(resId: Int) = scope.launch { snackbar.showSnackbar(context.getString(resId)) }

    Scaffold(
        modifier = modifier.creamPageBackground(),
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            CenterAlignedTopAppBar(
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent),
                title = {
                    Text(
                        stringResource(R.string.settingsTitle),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                    )
                },
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
            // ===== 계정 =====
            // 섹션 이모지는 R.string에 이미 포함 — 코드에서 덧붙이면 중복 표시
            SettingsSection(stringResource(R.string.sectionAccount)) {
                ListItem(
                    colors = transparentListColors(),
                    leadingContent = { SettingIcon(Icons.Rounded.Person, LgCtaNavyEnd) },
                    headlineContent = { Text(stringResource(R.string.dhLotteryAccount), fontWeight = FontWeight.Bold) },
                    supportingContent = {
                        Text(
                            if (isLoggedIn) loggedInUserId ?: stringResource(R.string.statusLoggedIn)
                            else stringResource(R.string.statusLoginRequired),
                        )
                    },
                    trailingContent = {
                        TextButton(onClick = {
                            if (isLoggedIn) {
                                vm.logout()
                                toast(R.string.snackbarLogoutSuccess)
                            } else {
                                showLogin = true
                            }
                        }) {
                            Text(stringResource(if (isLoggedIn) R.string.buttonLogout else R.string.buttonLogin))
                        }
                    },
                )
                HorizontalDivider()
                ListItem(
                    colors = transparentListColors(),
                    leadingContent = { SettingIcon(Icons.Rounded.AccountBalanceWallet, LgTeal) },
                    headlineContent = { Text(stringResource(R.string.balanceTitle), fontWeight = FontWeight.Bold) },
                    supportingContent = {
                        Text(
                            "₩${formatNumber(balance)}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    },
                    trailingContent = {
                        if (isLoggedIn) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilledTonalIconButton(onClick = { vm.refreshBalance() }) {
                                    Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.cdRefresh), tint = MaterialTheme.colorScheme.primary)
                                }
                                FilledIconButton(
                                    onClick = { openUrl(context, URL_CHARGE) },
                                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = LgTealInk, contentColor = Color.White),
                                ) {
                                    Icon(Icons.Rounded.AddCard, contentDescription = stringResource(R.string.chargeNow))
                                }
                            }
                        }
                    },
                )
            }
            Spacer(Modifier.height(24.dp))

            // ===== 자동 구매 =====
            SettingsSection(stringResource(R.string.sectionAutoPurchase)) {
                SwitchRow(
                    title = stringResource(R.string.settingEnableAutoPurchase),
                    subtitle = if (!isLoggedIn) {
                        { Text(stringResource(R.string.hintLoginRequired), color = MaterialTheme.colorScheme.error) }
                    } else null,
                    checked = autoEnabled,
                    enabled = isLoggedIn,
                    onChange = { v ->
                        vm.setAutoEnabled(v)
                        // 원본 _checkBatteryOptimization: 활성화 시 배터리 최적화 제외 유도(미제외일 때만).
                        if (v && !isIgnoringBatteryOptimizations(context)) requestIgnoreBatteryOptimizations(context)
                    },
                )
                if (autoEnabled) {
                    HorizontalDivider()
                    ListItem(
                        modifier = Modifier.clickable { onNavigateToNumbers() },
                        colors = transparentListColors(),
                        leadingContent = { SettingIcon(Icons.Rounded.ConfirmationNumber, LgGold) },
                        headlineContent = { Text(stringResource(R.string.gamesConfigured, autoGames), fontWeight = FontWeight.Bold) },
                        supportingContent = {
                            Text(
                                if (autoGames > 0) stringResource(R.string.hintChangeInNumberTab)
                                else stringResource(R.string.hintSetupGamesInNumberTab),
                            )
                        },
                        trailingContent = { Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                    )
                    HorizontalDivider()
                    // 구매 요일(원본 DropdownButton<int>). day: 1=월 .. 7=일.
                    val dayNames = localizedDayNames()
                    ListItem(
                        colors = transparentListColors(),
                        leadingContent = { SettingIcon(Icons.Rounded.CalendarToday, LgGreen) },
                        headlineContent = { Text(stringResource(R.string.settingPurchaseDay), fontWeight = FontWeight.Bold) },
                        trailingContent = {
                            InlineDropdown(
                                selected = day,
                                options = (1..7).toList(),
                                label = { stringResource(R.string.dayFormat, dayNames[it - 1]) },
                                onSelect = { d -> if (!vm.setPurchaseDay(d)) toast(R.string.errorInvalidPurchaseTime) },
                            )
                        },
                    )
                    HorizontalDivider()
                    // 구매 시간(원본 showTimePicker).
                    ListItem(
                        colors = transparentListColors(),
                        leadingContent = { SettingIcon(Icons.Rounded.AccessTime, LgCtaNavyEnd) },
                        headlineContent = { Text(stringResource(R.string.settingPurchaseTime), fontWeight = FontWeight.Bold) },
                        trailingContent = {
                            TextButton(onClick = { showTimePicker = true }) {
                                Text(
                                    "%02d:%02d".format(hour, minute),
                                    fontWeight = FontWeight.SemiBold,
                                    style = MaterialTheme.typography.titleMedium,
                                )
                            }
                        },
                    )
                    HorizontalDivider()
                    // 배터리 최적화 제외(원본 _requestBatteryOptimization).
                    ListItem(
                        modifier = Modifier.clickable {
                            if (isIgnoringBatteryOptimizations(context)) toast(R.string.snackbarBatteryAlreadyExcluded)
                            else requestIgnoreBatteryOptimizations(context)
                        },
                        colors = transparentListColors(),
                        leadingContent = { SettingIcon(Icons.Rounded.BatterySaver, LgAmber) },
                        headlineContent = { Text(stringResource(R.string.settingBatteryOptimization), fontWeight = FontWeight.Bold) },
                        supportingContent = { Text(stringResource(R.string.hintBatteryOptimization)) },
                        trailingContent = { Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                    )
                }
            }
            Spacer(Modifier.height(24.dp))

            // ===== 알림 =====
            SettingsSection(stringResource(R.string.sectionNotifications)) {
                SwitchRow(
                    title = stringResource(R.string.settingPurchaseNoti),
                    subtitle = { Text(formatPurchaseSchedule(day, hour, minute)) },
                    checked = purchaseNoti,
                    onChange = { purchaseNoti = it },
                )
                HorizontalDivider()
                SwitchRow(
                    title = stringResource(R.string.settingResultNoti),
                    subtitle = { Text(stringResource(R.string.notificationResultTime)) },
                    checked = resultNoti,
                    onChange = { resultNoti = it },
                )
                HorizontalDivider()
                // 잔액 부족 알림(원본 _buildBalanceAlertSection).
                SwitchRow(
                    title = stringResource(R.string.balanceAlertTitle),
                    subtitle = { Text(stringResource(R.string.balanceAlertDesc)) },
                    checked = alertEnabled,
                    onChange = { vm.setBalanceAlertEnabled(it) },
                )
                if (alertEnabled) {
                    HorizontalDivider()
                    val presets = listOf(5000, 10000, 20000)
                    ListItem(
                        colors = transparentListColors(),
                        leadingContent = { SettingIcon(Icons.Rounded.MonetizationOn, LgAmber) },
                        headlineContent = { Text(stringResource(R.string.balanceThreshold), fontWeight = FontWeight.Bold) },
                        trailingContent = {
                            InlineDropdown(
                                selected = if (threshold in presets) threshold else -1,
                                options = presets + -1,
                                label = {
                                    when {
                                        it != -1 -> "₩${formatNumber(it)}"
                                        threshold in presets -> stringResource(R.string.thresholdCustom)
                                        else -> "₩${formatNumber(threshold)}"
                                    }
                                },
                                onSelect = { v -> if (v == -1) showThreshold = true else vm.setBalanceAlertThreshold(v) },
                            )
                        },
                    )
                }
                HorizontalDivider()
                // 권한 상태 + 유도(POST_NOTIFICATIONS / 정확 알람).
                PermissionRows()
            }
            Spacer(Modifier.height(24.dp))

            // ===== 앱 정보 =====
            SettingsSection(stringResource(R.string.sectionAppInfo)) {
                ListItem(
                    colors = transparentListColors(),
                    leadingContent = { SettingIcon(Icons.Rounded.Info, LgTeal) },
                    headlineContent = { Text(stringResource(R.string.settingVersion), fontWeight = FontWeight.Bold) },
                    supportingContent = { Text(BuildConfig.VERSION_NAME) }, // 단일 소스: build.gradle.kts versionName
                )
                HorizontalDivider()
                ListItem(
                    colors = transparentListColors(),
                    leadingContent = { SettingIcon(Icons.Rounded.Language, LgTealInk) },
                    headlineContent = { Text(stringResource(R.string.settingLanguage), fontWeight = FontWeight.Bold) },
                    supportingContent = { Text(languageLabel(language)) },
                    trailingContent = {
                        InlineDropdown(
                            selected = language,
                            options = listOf("system", "ko", "en", "ja"),
                            label = { languageLabel(it) },
                            onSelect = { vm.setLanguage(it) },
                        )
                    },
                )
                HorizontalDivider()
                ListItem(
                    modifier = Modifier.clickable { openUrl(context, URL_OPENSOURCE) },
                    colors = transparentListColors(),
                    leadingContent = { SettingIcon(Icons.Rounded.Code, LgInk) },
                    headlineContent = { Text(stringResource(R.string.settingOpenSource), fontWeight = FontWeight.Bold) },
                    supportingContent = { Text("github.com/umi-corp/autolotto") },
                    trailingContent = { Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                )
                HorizontalDivider()
                ListItem(
                    modifier = Modifier.clickable { showReset = true },
                    colors = transparentListColors(),
                    leadingContent = { SettingIcon(Icons.Rounded.DeleteOutline, MaterialTheme.colorScheme.error) },
                    headlineContent = {
                        Text(stringResource(R.string.settingResetData), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                    },
                )
            }
            Spacer(Modifier.height(24.dp))

            // ===== 후원 =====
            SettingsSection(stringResource(R.string.sectionDonation)) {
                ListItem(
                    modifier = Modifier.clickable { openUrl(context, URL_DONATION) },
                    colors = transparentListColors(),
                    leadingContent = { Text("☕", style = MaterialTheme.typography.headlineSmall) },
                    headlineContent = { Text(stringResource(R.string.donationTitle), fontWeight = FontWeight.Bold) },
                    trailingContent = { Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                )
            }
            Spacer(Modifier.height(40.dp))
        }
    }

    // ===== 다이얼로그 =====
    if (showLogin) {
        LoginDialog(
            onDismiss = { showLogin = false },
            onLogin = { id, pw -> vm.login(id, pw) },
        )
    }
    if (showReset) {
        ResetDialog(
            onDismiss = { showReset = false },
            onConfirm = { vm.resetAll(); showReset = false },
        )
    }
    if (showTimePicker) {
        TimePickerDialog(
            initialHour = hour,
            initialMinute = minute,
            onDismiss = { showTimePicker = false },
            onConfirm = { h, m ->
                showTimePicker = false
                if (!vm.setPurchaseTime(h, m)) toast(R.string.errorPurchaseTimeRestriction)
            },
        )
    }
    if (showThreshold) {
        ThresholdDialog(
            initial = threshold,
            onDismiss = { showThreshold = false },
            onConfirm = { v -> vm.setBalanceAlertThreshold(v); showThreshold = false },
        )
    }
}

/**
 * Lucky Gloss 섹션: 이모지+볼드 [SectionHeader] + 프로스트 [SectionCard](여러 ListItem 묶음).
 * 시그니처 모션: 내부 Column에 animateContentSize — 자동구매 on/off·잔액알림 on/off 시
 * 종속 행들이 스프링으로 펼쳐지고 접힌다.
 */
@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    SectionHeader(title, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
    SectionCard(contentPadding = PaddingValues(vertical = 4.dp)) {
        Column(Modifier.animateContentSize(animationSpec = MotionSpecs.gentle())) { content() }
    }
}

/** ListItem 컨테이너 투명 — 부모 카드색이 비치게(섹션 묶음 룩). */
@Composable
private fun transparentListColors() = ListItemDefaults.colors(containerColor = Color.Transparent)

/** 광택 그라디언트 아이콘 타일(Lucky Gloss) — 행별 [tint]로 캔디 컬러 구분. */
@Composable
private fun SettingIcon(icon: ImageVector, tint: Color = LgTeal) = GlossyIconTile(icon = icon, tint = tint)

/** 스위치 행(원본 SwitchListTile) — 행 전체 탭으로도 토글. */
@Composable
private fun SwitchRow(
    title: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    subtitle: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
) {
    ListItem(
        modifier = if (enabled) Modifier.clickable { onChange(!checked) } else Modifier,
        colors = transparentListColors(),
        headlineContent = { Text(title, fontWeight = FontWeight.Bold) },
        supportingContent = subtitle,
        trailingContent = {
            Switch(checked = checked, onCheckedChange = if (enabled) onChange else null, enabled = enabled)
        },
    )
}

/** 인라인 드롭다운(원본 trailing DropdownButton). 트리거 = 라벨+▾, 항목은 DropdownMenu. */
@Composable
private fun <T> InlineDropdown(
    selected: T,
    options: List<T>,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }) {
            Text(label(selected), fontWeight = FontWeight.SemiBold)
            Icon(Icons.Rounded.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { opt ->
                DropdownMenuItem(
                    text = { Text(label(opt)) },
                    onClick = { expanded = false; onSelect(opt) },
                )
            }
        }
    }
}

/**
 * 권한 상태 + 유도(원본엔 없음 — 네이티브 백그라운드 신뢰성).
 * 알림 권한(POST_NOTIFICATIONS, API33+) · 정확 알람(SCHEDULE_EXACT_ALARM, API31+).
 * 상태는 시스템 설정 다녀온 뒤 onResume에서 재조회.
 */
@Composable
private fun PermissionRows() {
    val context = LocalContext.current
    var notifGranted by remember { mutableStateOf(notificationsEnabled(context)) }
    var exactGranted by remember { mutableStateOf(ExactAlarmPermission.canScheduleExactAlarms(context)) }

    LifecycleResumeEffect(Unit) {
        notifGranted = notificationsEnabled(context)
        exactGranted = ExactAlarmPermission.canScheduleExactAlarms(context)
        onPauseOrDispose { }
    }

    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notifGranted = granted || notificationsEnabled(context)
    }

    PermissionRow(
        icon = Icons.Rounded.NotificationsActive,
        title = stringResource(R.string.permissionNotification),
        desc = stringResource(R.string.permissionNotificationDesc),
        granted = notifGranted,
        onRequest = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                openAppNotificationSettings(context)
            }
        },
    )
    HorizontalDivider()
    PermissionRow(
        icon = Icons.Rounded.Alarm,
        title = stringResource(R.string.permissionExactAlarm),
        desc = stringResource(R.string.permissionExactAlarmDesc),
        granted = exactGranted,
        onRequest = { context.launchActivitySafely(ExactAlarmPermission.requestIntent(context)) },
    )
}

@Composable
private fun PermissionRow(icon: ImageVector, title: String, desc: String, granted: Boolean, onRequest: () -> Unit) {
    ListItem(
        colors = transparentListColors(),
        leadingContent = { SettingIcon(icon) },
        headlineContent = { Text(title, fontWeight = FontWeight.Bold) },
        supportingContent = { Text(desc) },
        trailingContent = {
            if (granted) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.permissionGranted), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                }
            } else {
                Button(onClick = onRequest) { Text(stringResource(R.string.buttonGrant)) }
            }
        },
    )
}

@Composable
private fun LoginDialog(onDismiss: () -> Unit, onLogin: (String, String) -> Unit) {
    var id by remember { mutableStateOf("") }
    var pw by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialogLoginTitle)) },
        text = {
            Column {
                OutlinedTextField(
                    value = id,
                    onValueChange = { id = it },
                    label = { Text(stringResource(R.string.inputUserId)) },
                    leadingIcon = { Icon(Icons.Rounded.Person, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = pw,
                    onValueChange = { pw = it },
                    label = { Text(stringResource(R.string.inputPassword)) },
                    leadingIcon = { Icon(Icons.Rounded.Lock, contentDescription = null) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = id.isNotBlank() && pw.isNotBlank(),
                onClick = { onLogin(id.trim(), pw.trim()); onDismiss() },
            ) { Text(stringResource(R.string.buttonLogin)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.buttonCancel)) } },
    )
}

@Composable
private fun ResetDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialogResetTitle)) },
        text = { Text(stringResource(R.string.dialogResetMessage)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.buttonReset2), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.buttonCancel)) } },
    )
}

@Composable
private fun ThresholdDialog(initial: Int, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    var text by remember { mutableStateOf(initial.toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.thresholdInputTitle)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.filter(Char::isDigit) },
                label = { Text(stringResource(R.string.thresholdInputHint)) },
                prefix = { Text("₩ ") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = {
                val v = text.toIntOrNull()
                if (v != null && v > 0) onConfirm(v) else onDismiss()
            }) { Text(stringResource(R.string.buttonConfirm)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.buttonCancel)) } },
    )
}

/** M3 시간 선택 다이얼로그(원본 showTimePicker, 24시간제). */
@Composable
private fun TimePickerDialog(initialHour: Int, initialMinute: Int, onDismiss: () -> Unit, onConfirm: (Int, Int) -> Unit) {
    val state = rememberTimePickerState(initialHour = initialHour, initialMinute = initialMinute, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onConfirm(state.hour, state.minute) }) { Text(stringResource(R.string.buttonConfirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.buttonCancel)) } },
        text = { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { TimePicker(state = state) } },
    )
}

/** 언어 코드 → 표시 라벨(원본 _buildLanguageTile). */
@Composable
private fun languageLabel(code: String): String = when (code) {
    "ko" -> stringResource(R.string.languageKo)
    "en" -> stringResource(R.string.languageEn)
    "ja" -> stringResource(R.string.languageJa)
    else -> stringResource(R.string.languageSystem)
}

// ===== 플랫폼 헬퍼(원본 url_launcher / battery MethodChannel 대응) =====

private fun openUrl(context: Context, url: String) =
    context.launchActivitySafely(Intent(Intent.ACTION_VIEW, Uri.parse(url)))

private fun notificationsEnabled(context: Context): Boolean =
    NotificationManagerCompat.from(context).areNotificationsEnabled()

private fun openAppNotificationSettings(context: Context) {
    context.launchActivitySafely(
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
    )
}

private fun isIgnoringBatteryOptimizations(context: Context): Boolean {
    val pm = context.getSystemService(PowerManager::class.java)
    return pm?.isIgnoringBatteryOptimizations(context.packageName) == true
}

// ponytail: ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS는 Play 정책 경고(BatteryLife) 대상이나 원본 동작과 1:1.
@SuppressLint("BatteryLife")
private fun requestIgnoreBatteryOptimizations(context: Context) {
    context.launchActivitySafely(
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}")),
    )
}

private const val URL_CHARGE = "https://www.dhlottery.co.kr/mypage/mndpChrg"
private const val URL_OPENSOURCE = "https://github.com/umi-corp/autolotto"
private const val URL_DONATION = "https://buymeacoffee.com/umicorp"
