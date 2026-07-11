@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.umicorp.autolotto.ui.screen

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.umicorp.autolotto.R
import com.umicorp.autolotto.data.Purchase
import com.umicorp.autolotto.ui.appViewModel
import com.umicorp.autolotto.ui.theme.LgGold
import com.umicorp.autolotto.ui.theme.LgTeal
import com.umicorp.autolotto.ui.theme.MotionSpecs
import com.umicorp.autolotto.ui.util.formatNumber
import com.umicorp.autolotto.ui.util.localizedRank
import com.umicorp.autolotto.ui.vm.HistoryViewModel
import kotlinx.coroutines.delay

/**
 * 구매/당첨 기록 (원본 history_screen.dart 1:1) — Lucky Gloss 리디자인.
 *
 * 로그인 상태면 dhlottery에서 최근 구매내역 라이브 조회. 회차별 프로스트 카드(틴트 헤더 + StatusPill),
 * 게임 A~E = 글로시 캔디 볼 6개 + 행별 상태 필. 낙첨 회차는 볼 dimmed로 은은하게 뮤트.
 * 시그니처 모션: 카드 스태거 등장(fade + rise, MotionSpecs.staggerDelay).
 */
@Composable
fun HistoryScreen(modifier: Modifier = Modifier) {
    val vm: HistoryViewModel = appViewModel()
    val purchases by vm.purchases.collectAsState()
    val loading by vm.loading.collectAsState()
    val loadingMore by vm.loadingMore.collectAsState()
    val canLoadMore by vm.canLoadMore.collectAsState()
    val error by vm.error.collectAsState()
    val isLoggedIn by vm.isLoggedIn.collectAsState()

    Scaffold(
        modifier = modifier.creamPageBackground(),
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.historyTitle), fontWeight = FontWeight.ExtraBold) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent),
                actions = {
                    IconButton(onClick = { vm.loadHistory() }, enabled = !loading) {
                        Icon(
                            Icons.Rounded.Refresh,
                            contentDescription = stringResource(R.string.cdRefresh),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                },
            )
        },
    ) { inner ->
        // 로딩→빈화면→목록 전환을 부드럽게(표준 Crossfade — 1.4.0엔 Expressive 컴포넌트 미수록).
        val phase = when {
            loading && purchases.isEmpty() -> 0
            purchases.isEmpty() -> 1
            else -> 2
        }
        Box(Modifier.fillMaxSize().padding(inner)) {
            Crossfade(targetState = phase, label = "historyState") { p ->
                when (p) {
                    0 -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }

                    1 -> EmptyState(
                        isLoggedIn = isLoggedIn,
                        error = error,
                        // 최근 3개월이 비어도 이전 창에 내역이 있을 수 있음 → 빈 화면에서도 더 보기
                        canLoadMore = isLoggedIn && canLoadMore,
                        loadingMore = loadingMore,
                        onLoadMore = vm::loadMore,
                    )

                    else -> PullToRefreshBox(
                        isRefreshing = loading,
                        onRefresh = { vm.loadHistory() },
                    ) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(20.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            itemsIndexed(purchases) { i, item -> HistoryCard(item, index = i) }
                            if (canLoadMore) {
                                item { LoadMoreButton(loading = loadingMore, onClick = vm::loadMore) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyState(
    isLoggedIn: Boolean,
    error: String?,
    canLoadMore: Boolean = false,
    loadingMore: Boolean = false,
    onLoadMore: () -> Unit = {},
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        GlossyIconTile(Icons.Rounded.History, tint = LgTeal, size = 64.dp)
        Spacer(Modifier.height(16.dp))
        Text(
            if (isLoggedIn) stringResource(R.string.historyNoRecords)
            else stringResource(R.string.historyLoginToLoad),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        if (error != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.historyLoadError, error),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (canLoadMore) {
            Spacer(Modifier.height(8.dp))
            LoadMoreButton(loading = loadingMore, onClick = onLoadMore)
        }
    }
}

/** 목록 하단/빈 화면 공용 "더 보기" — 로드 중엔 같은 자리에서 스피너로 교체. */
@Composable
private fun LoadMoreButton(loading: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth().height(48.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
        } else {
            TextButton(onClick = onClick) {
                Text(
                    stringResource(R.string.historyLoadMore),
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.ExtraBold,
                )
            }
        }
    }
}

@Composable
private fun HistoryCard(item: Purchase, index: Int) {
    val winner = item.rank != null && item.rank != "nowin"
    val dateStr = "%04d-%02d-%02d".format(item.date.year, item.date.monthValue, item.date.dayOfMonth)

    // 시그니처 모션: 스태거 등장 (fade + rise). ponytail: 스크롤로 재진입 시 재생됨 — 짧고 은은해 허용.
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(MotionSpecs.staggerDelay(index).toLong())
        shown = true
    }
    val appear by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = MotionSpecs.emphasized(),
        label = "cardAppear",
    )

    val shape = MaterialTheme.shapes.large
    // 헤더 틴트: 당첨=골드(진하게), 대기=골드(옅게), 낙첨=틸 민트(뮤트).
    val headerAccent = if (item.checked && !winner) LgTeal else LgGold
    val headerBg = lerp(headerAccent, MaterialTheme.colorScheme.surfaceContainerLowest, if (winner) 0.70f else 0.86f)

    SectionCard(
        modifier = Modifier
            .graphicsLayer {
                alpha = appear
                translationY = (1f - appear) * 24.dp.toPx()
            }
            .then(if (winner) Modifier.border(2.dp, LgGold, shape) else Modifier),
        contentPadding = PaddingValues(0.dp),
    ) {
        // 헤더: 제 {n}회 + 날짜 + StatusPill
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(headerBg)
                .padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.roundLabel, item.round),
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    dateStr,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            when {
                !item.checked -> StatusPill(stringResource(R.string.statusPending), PillTone.Pending)
                winner -> StatusPill(
                    stringResource(R.string.rankWithEmoji, localizedRank(item.rank ?: "nowin")),
                    PillTone.Win,
                )
                else -> StatusPill(stringResource(R.string.statusNoWin), PillTone.Lose)
            }
        }

        // 본문: 게임 A~E — 글로시 볼 + 행별 상태
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item.numbers.forEachIndexed { i, nums ->
                GameRow(
                    letter = ('A' + i).toString(),
                    nums = nums,
                    winningNumbers = item.winningNumbers,
                    bonusNumber = item.bonusNumber,
                    checked = item.checked,
                    gameRank = item.gameRanks?.getOrNull(i),
                )
            }
        }

        // 푸터: 당첨금 (골드 밴드)
        if (winner) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(lerp(LgGold, MaterialTheme.colorScheme.surfaceContainerLowest, 0.70f))
                    .padding(14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(R.string.prizeLabel, formatNumber(item.prize)),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.ExtraBold,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}

/** 게임 1줄: 라벨 + 글로시 볼(당첨 강조/흐림/보너스 림) + 상태 필. FlowRow로 좁은 화면 줄바꿈. */
@Composable
private fun GameRow(
    letter: String,
    nums: List<Int>,
    winningNumbers: List<Int>?,
    bonusNumber: Int?,
    checked: Boolean,
    gameRank: String?,
) {
    val hasResult = checked && winningNumbers != null
    val gameWinner = gameRank != null && gameRank != "nowin" && gameRank != "pending"
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.width(24.dp).height(32.dp), contentAlignment = Alignment.CenterStart) {
            Text(
                letter,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.ExtraBold,
                style = MaterialTheme.typography.labelLarge,
            )
        }
        nums.forEach { n ->
            val isMatch = winningNumbers?.contains(n) == true
            val isBonusMatch = bonusNumber != null && bonusNumber == n
            LottoBall(
                n = n,
                size = 32.dp,
                dimmed = hasResult && !(isMatch || isBonusMatch),
                // 맞은 번호는 홈 보너스 볼과 동일한 강조 림 — dimmed 해제만으론 구분이 흐림(사용자 피드백)
                bordered = hasResult && (isMatch || isBonusMatch),
            )
        }
        if (gameRank != null) {
            Box(Modifier.height(32.dp), contentAlignment = Alignment.Center) {
                StatusPill(
                    text = if (gameWinner) stringResource(R.string.rankWithEmoji, localizedRank(gameRank))
                    else localizedRank(gameRank),
                    tone = when {
                        gameWinner -> PillTone.Win
                        gameRank == "pending" -> PillTone.Pending
                        else -> PillTone.Lose
                    },
                )
            }
        }
    }
}
