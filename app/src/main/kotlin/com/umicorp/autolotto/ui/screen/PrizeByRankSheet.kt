@file:OptIn(ExperimentalMaterial3Api::class)

package com.umicorp.autolotto.ui.screen

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umicorp.autolotto.R
import com.umicorp.autolotto.appContainer
import com.umicorp.autolotto.data.DrawPrizes
import com.umicorp.autolotto.data.RankPrize
import com.umicorp.autolotto.data.myPrizeGames
import com.umicorp.autolotto.data.prizeAfterTax
import com.umicorp.autolotto.data.prizePage
import com.umicorp.autolotto.dhlottery.PurchaseService
import com.umicorp.autolotto.ui.appViewModel
import com.umicorp.autolotto.ui.theme.LgGold
import com.umicorp.autolotto.ui.util.formatNumber
import com.umicorp.autolotto.ui.util.launchActivitySafely
import com.umicorp.autolotto.ui.util.localizedRank
import com.umicorp.autolotto.ui.vm.HistoryViewModel
import kotlinx.coroutines.launch

@Composable
internal fun PrizeByRankLink(onClick: () -> Unit) {
    HorizontalDivider()
    Box(
        Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Text(
            stringResource(R.string.prizeByRankLink),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
internal fun PrizeByRankSheet(
    startRound: Int,
    showAfterTax: Boolean,
    onShowAfterTax: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val container = LocalContext.current.appContainer
    val history: HistoryViewModel = appViewModel()
    val purchases by history.purchases.collectAsState()
    val loggedIn by history.isLoggedIn.collectAsState()
    val latest = remember(startRound) { maxOf(startRound, PurchaseService.latestCompletedRound()) }
    val pager = rememberPagerState(initialPage = prizePage(startRound, latest)) { latest }
    val scope = rememberCoroutineScope()
    val round = pager.currentPage + 1
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().fillMaxHeight().padding(horizontal = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.prizeByRankTitle),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 17.sp,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                PagerStep("‹", round > 1, stringResource(R.string.roundLabel, round - 1)) {
                    scope.launch { pager.animateScrollToPage(prizePage(round - 1, latest)) }
                }
                Text(stringResource(R.string.roundLabel, round), fontWeight = FontWeight.Bold)
                PagerStep("›", round < latest, stringResource(R.string.roundLabel, round + 1)) {
                    scope.launch { pager.animateScrollToPage(prizePage(round + 1, latest)) }
                }
            }
            HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth()) { page ->
                val pageRound = page + 1
                var result by remember(pageRound) { mutableStateOf<DrawPrizes?>(null) }
                var attempt by remember(pageRound) { mutableIntStateOf(0) }
                LaunchedEffect(pageRound, attempt) { result = container.resultService.getDrawPrizes(pageRound) }
                val date = result?.winning?.date?.takeIf { it.isNotBlank() }
                    ?: PurchaseService.getDrawDates(pageRound).drawDate.toString()
                Column(Modifier.fillMaxSize()) {
                    Text(
                        stringResource(R.string.prizeByRankDrawDate, date),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                    PrizePage(
                        result = result,
                        myGames = myPrizeGames(pageRound, loggedIn, purchases),
                        afterTax = showAfterTax,
                        onAfterTaxChange = onShowAfterTax,
                        onRetry = {
                            result = null
                            attempt++
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun PrizePage(
    result: DrawPrizes?,
    myGames: Map<Int, List<Char>>,
    afterTax: Boolean,
    onAfterTaxChange: (Boolean) -> Unit,
    onRetry: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        result?.winning?.let { winning ->
            val description = stringResource(R.string.cdPrizeByRankBalls, winning.numbers.joinToString(", "), winning.bonus)
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                WinningBallRow(winning, Modifier.clearAndSetSemantics { contentDescription = description })
            }
        }
        when (result) {
            null -> PrizeSkeleton()
            is DrawPrizes.Pending -> PendingPrizes(onRetry)
            DrawPrizes.Error -> ConnectionErrorState(
                onRetry = onRetry,
                title = R.string.prizeByRankError,
                modifier = Modifier.fillMaxWidth().heightIn(min = 300.dp),
            )
            is DrawPrizes.Published -> {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        .toggleable(value = afterTax, role = Role.Switch, onValueChange = onAfterTaxChange)
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.prizeByRankAfterTax), fontWeight = FontWeight.Bold)
                    Switch(checked = afterTax, onCheckedChange = null)
                }
                SectionCard(contentPadding = PaddingValues(0.dp)) {
                    result.ranks.forEachIndexed { index, prize ->
                        if (index > 0) HorizontalDivider()
                        PrizeRankRow(index + 1, prize, myGames[index + 1].orEmpty(), afterTax)
                    }
                }
                PrizeNote(stringResource(R.string.prizeByRankCountNote))
                if (afterTax) PrizeTaxNotes()
            }
        }
    }
}

@Composable
private fun PrizeRankRow(rank: Int, prize: RankPrize, games: List<Char>, afterTax: Boolean) {
    val colors = MaterialTheme.colorScheme
    val rankLabel = localizedRank("rank$rank")
    val matchLabel = stringResource(when (rank) {
        1 -> R.string.prizeByRankMatch1
        2 -> R.string.prizeByRankMatch2
        3 -> R.string.prizeByRankMatch3
        4 -> R.string.prizeByRankMatch4
        else -> R.string.prizeByRankMatch5
    })
    val amount = if (prize.games == 0) stringResource(R.string.prizeByRankNone)
    else stringResource(R.string.ledgerWon, formatNumber(prize.amount))
    val perGame = if (prize.games == 0) amount else stringResource(R.string.cdPrizeByRankPerGame, amount)
    val count = pluralStringResource(R.plurals.prizeByRankGames, prize.games, formatNumber(prize.games))
    val tax = if (afterTax && prize.games > 0) {
        val net = stringResource(R.string.ledgerWon, formatNumber(prizeAfterTax(prize.amount)))
        stringResource(R.string.prizeByRankAfterTaxValue, net)
    } else null
    val mine = if (games.isNotEmpty()) stringResource(R.string.prizeByRankMine, games.joinToString(", ")) else null
    val description = listOfNotNull(rankLabel, matchLabel, perGame, tax, count, mine).joinToString(", ")
    Box(
        Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = description }
            .then(if (mine == null) Modifier else Modifier.background(lerp(LgGold, colors.surfaceContainerLowest, 0.70f))),
    ) {
        if (mine != null) {
            Canvas(Modifier.matchParentSize()) { drawRect(LgGold, size = Size(4.dp.toPx(), size.height)) }
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FlowRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        rankLabel,
                        Modifier.alignByBaseline(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Text(
                        matchLabel,
                        Modifier.alignByBaseline(),
                        color = colors.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Text(amount, Modifier.weight(1.4f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(count, Modifier.weight(1f), color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                if (tax != null) Text(tax, Modifier.weight(1.4f), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.End)
            }
            if (mine != null) StatusPill(mine, PillTone.Win, Modifier.padding(top = 2.dp))
        }
    }
}

@Composable
private fun PrizeSkeleton() {
    Row(
        modifier = Modifier.fillMaxWidth().clearAndSetSemantics {},
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
    ) {
        repeat(7) { Box(Modifier.size(32.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)) }
    }
    SectionCard {
        repeat(5) {
            if (it > 0) HorizontalDivider()
            SkeletonRow()
        }
    }
}

@Composable
private fun PendingPrizes(onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().heightIn(min = 240.dp).padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
    ) {
        Text(stringResource(R.string.prizeByRankPendingTitle), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            stringResource(R.string.prizeByRankPendingBody),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        TextButton(onClick = onRetry) { Text(stringResource(R.string.prizeByRankRefresh)) }
    }
}

@Composable
private fun PrizeNote(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun PrizeTaxNotes() {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        PrizeNote(stringResource(R.string.prizeByRankTaxNote))
        PrizeNote(stringResource(R.string.prizeByRankTaxFormula))
        FlowRow {
            TextButton(onClick = {
                context.launchActivitySafely(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.dhlottery.co.kr/faq/home?tab=2")))
            }) { Text(stringResource(R.string.prizeByRankTaxSource)) }
            TextButton(onClick = {
                context.launchActivitySafely(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.law.go.kr/lsLinkCommonInfo.do?lsJoLnkSeq=1016426655")))
            }) { Text(stringResource(R.string.prizeByRankTaxLaw)) }
        }
    }
}
