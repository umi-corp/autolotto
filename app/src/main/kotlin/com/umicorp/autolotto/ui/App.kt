package com.umicorp.autolotto.ui

import android.Manifest
import android.content.pm.PackageManager
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ConfirmationNumber
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.umicorp.autolotto.ui.theme.MotionSpecs
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.umicorp.autolotto.R
import com.umicorp.autolotto.appContainer
import com.umicorp.autolotto.update.AppUpdater
import com.umicorp.autolotto.update.UpdateInfo
import com.umicorp.autolotto.ui.screen.HistoryScreen
import com.umicorp.autolotto.ui.screen.HomeScreen
import com.umicorp.autolotto.ui.screen.NumberScreen
import com.umicorp.autolotto.ui.screen.SettingsScreen
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * 앱 루트: 스플래시 → AppShell. (원본 main.dart → SplashScreen → AppShell.)
 * 언어는 [LocalizedApp]이 MainActivity에서 감싼다.
 */
@Composable
fun AppRoot() {
    val container = LocalContext.current.appContainer
    var showSplash by rememberSaveable { mutableStateOf(true) }
    if (showSplash) {
        SplashScreen(container, onFinished = { showSplash = false })
    } else {
        AppShell()
    }
}

private data class TabItem(val labelRes: Int, val icon: ImageVector)

/**
 * 메인 셸: M3 NavigationBar 4탭. Slice 5b에서 본문을 실제 화면으로 교체.
 * 알림 권한(POST_NOTIFICATIONS, API33+)은 셸 진입 시 요청(원본 home initState 타이밍).
 */
@Composable
fun AppShell() {
    RequestNotificationPermission()

    val tabs = listOf(
        TabItem(R.string.nav_home, Icons.Rounded.Home),
        TabItem(R.string.nav_numbers, Icons.Rounded.ConfirmationNumber),
        TabItem(R.string.nav_history, Icons.Rounded.History),
        TabItem(R.string.nav_settings, Icons.Rounded.Settings),
    )
    val pagerState = rememberPagerState(pageCount = { tabs.size })
    val scope = rememberCoroutineScope()

    // 사이드로드 인앱 업데이트: 셸 진입 시 1회 확인 → 새 버전이면 다이얼로그.
    val container = LocalContext.current.appContainer
    val updateInfo by container.updateInfo.collectAsState()
    LaunchedEffect(Unit) { runCatching { container.checkForUpdate() } }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            FloatingPillNav(
                tabs = tabs,
                selectedIndex = pagerState.currentPage,
                onSelect = { i -> scope.launch { pagerState.animateScrollToPage(i) } },
            )
        },
    ) { padding ->
        // 원본 PageView와 동치: 4탭 모두 keep-alive + 스와이프. 하단 네비 높이만 예약(상단 인셋은 각 화면 TopAppBar가 처리).
        val screenModifier = Modifier
            .fillMaxSize()
            .padding(bottom = padding.calculateBottomPadding())
        HorizontalPager(
            state = pagerState,
            beyondViewportPageCount = tabs.size,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            when (page) {
                0 -> HomeScreen(
                    modifier = screenModifier,
                    onNavigateToNumbers = { scope.launch { pagerState.animateScrollToPage(1) } },
                )
                1 -> NumberScreen(modifier = screenModifier)
                2 -> HistoryScreen(modifier = screenModifier)
                else -> SettingsScreen(
                    modifier = screenModifier,
                    onNavigateToNumbers = { scope.launch { pagerState.animateScrollToPage(1) } },
                )
            }
        }
    }

    updateInfo?.let { UpdateDialog(it) }
}

/**
 * 새 버전 안내 다이얼로그(사이드로드). "업데이트" → 설치권한 확인 → APK 다운로드 → 시스템 인스톨러.
 * 다운로드 중엔 진행바 표시 + 버튼 잠금.
 */
@Composable
private fun UpdateDialog(info: UpdateInfo) {
    val context = LocalContext.current
    val container = context.appContainer
    val progress by container.updateProgress.collectAsState()
    val scope = rememberCoroutineScope()
    val downloading = progress != null

    AlertDialog(
        onDismissRequest = { if (!downloading) container.dismissUpdate() },
        title = { Text(stringResource(R.string.updateAvailableTitle, info.versionName), fontWeight = FontWeight.Bold) },
        text = {
            Column {
                if (info.notes.isNotBlank()) {
                    Text(
                        markdownLite(info.notes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (downloading) {
                    Spacer(Modifier.height(16.dp))
                    LinearProgressIndicator(
                        progress = { progress ?: 0f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(stringResource(R.string.updateDownloading), style = MaterialTheme.typography.labelMedium)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !downloading,
                onClick = {
                    if (!AppUpdater.canInstall(context)) {
                        // 최초 1회: "이 출처의 앱 설치" 허용 화면으로 유도(허용 후 다시 탭).
                        context.startActivity(AppUpdater.installPermissionIntent(context))
                    } else {
                        scope.launch {
                            val file = container.downloadUpdate(context)
                            if (file != null) AppUpdater.install(context, file)
                        }
                    }
                },
            ) { Text(stringResource(R.string.buttonUpdateNow), fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            if (!downloading) {
                TextButton(onClick = { container.dismissUpdate() }) {
                    Text(stringResource(R.string.buttonUpdateLater))
                }
            }
        },
    )
}

/** [markdownLite]의 인라인 `**굵게**` 처리. 짝이 안 맞으면 원문 그대로. */
private fun AnnotatedString.Builder.appendInlineBold(text: String) {
    var rest = text
    while (true) {
        val s = rest.indexOf("**")
        val e = if (s >= 0) rest.indexOf("**", s + 2) else -1
        if (s < 0 || e < 0) { append(rest); return }
        append(rest.substring(0, s))
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(rest.substring(s + 2, e)) }
        rest = rest.substring(e + 2)
    }
}

/**
 * 릴리스 노트용 초경량 마크다운 렌더링: `#` 제목(굵게), `- ` 목록(•), `**굵게**`만 지원.
 * GitHub 릴리스 body가 이 부분집합을 벗어나면 해당 문법은 원문 그대로 보인다. (테스트 대상)
 */
internal fun markdownLite(src: String): AnnotatedString = buildAnnotatedString {
    src.trim().lines().forEachIndexed { i, raw ->
        if (i > 0) append('\n')
        val line = raw.trimEnd()
        when {
            line.startsWith("#") ->
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(line.trimStart('#').trim()) }
            line.startsWith("- ") -> { append("•  "); appendInlineBold(line.removePrefix("- ")) }
            else -> appendInlineBold(line)
        }
    }
}

/**
 * 플로팅 알약(pill) 하단 네비. M3 NavigationBar 대신 크림 위에 떠 있는 둥근 pill.
 * 4탭(홈/번호/내역/설정) + 선택 시 틸 인디케이터 pill + 아이콘 스프링 바운스. 라벨은 stringResource 그대로.
 */
@Composable
private fun FloatingPillNav(
    tabs: List<TabItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 10.dp),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
            shadowElevation = 12.dp,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                tabs.forEachIndexed { i, tab ->
                    NavPillItem(tab = tab, selected = i == selectedIndex, onClick = { onSelect(i) })
                }
            }
        }
    }
}

@Composable
private fun RowScope.NavPillItem(
    tab: TabItem,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.14f else 1f,
        animationSpec = MotionSpecs.bouncy(),
        label = "navScale",
    )
    val tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    val indicator = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
    Column(
        modifier = Modifier
            .weight(1f)
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(percent = 50))
                .background(indicator)
                .padding(horizontal = 18.dp, vertical = 5.dp),
        ) {
            Icon(
                tab.icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier
                    .size(24.dp)
                    .graphicsLayer { scaleX = scale; scaleY = scale },
            )
        }
        Spacer(Modifier.height(3.dp))
        Text(
            text = stringResource(tab.labelRes),
            color = tint,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        )
    }
}

/** API33+에서 알림 권한 미허용 시 1회 요청. 미만 버전은 권한 개념 없음(no-op). */
@Composable
private fun RequestNotificationPermission() {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

/**
 * 앱 언어 적용 (원본 `appLocaleProvider` + MaterialApp.locale 대응).
 *
 * AppCompat 없이 minSdk 31에서 동작하도록 Compose CompositionLocal로 로케일을 덮어쓴다:
 * 선택 언어로 만든 Configuration 컨텍스트를 LocalContext/LocalConfiguration에 제공 → stringResource가
 * 해당 로케일로 해석된다. [language]="system"이면 시스템 로케일 그대로.
 */
@Composable
fun LocalizedApp(language: String, content: @Composable () -> Unit) {
    val base = LocalContext.current
    val localized = remember(language, base) {
        if (language == "system") {
            base
        } else {
            val locale = Locale.forLanguageTag(language)
            val config = Configuration(base.resources.configuration).apply { setLocale(locale) }
            val cfg = base.createConfigurationContext(config)
            // base(=Activity)를 baseContext로 유지하는 ContextWrapper. 이래야 findOwner(ActivityResultRegistry/
            // ViewModelStore)와 startActivity가 ContextWrapper 체인을 따라 Activity를 찾는다(크래시 방지).
            // resources/assets만 로케일 적용본으로 덮어쓴다.
            object : ContextWrapper(base) {
                override fun getResources(): Resources = cfg.resources
                override fun getAssets() = cfg.assets
            }
        }
    }
    CompositionLocalProvider(
        LocalContext provides localized,
        LocalConfiguration provides localized.resources.configuration,
        content = content,
    )
}

/** 화면에서 앱 스코프 컨테이너 팩토리로 ViewModel 획득(Slice 5b 화면용). */
@Composable
inline fun <reified VM : ViewModel> appViewModel(): VM =
    viewModel(factory = LocalContext.current.appContainer.viewModelFactory)
