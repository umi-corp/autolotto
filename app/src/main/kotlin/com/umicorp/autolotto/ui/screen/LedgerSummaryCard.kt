@file:OptIn(ExperimentalMaterial3Api::class)

package com.umicorp.autolotto.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umicorp.autolotto.R
import com.umicorp.autolotto.data.LEDGER_ZONE
import com.umicorp.autolotto.data.LedgerSummary
import com.umicorp.autolotto.data.YearPage
import com.umicorp.autolotto.data.yearPages
import com.umicorp.autolotto.ui.theme.LgAmber
import com.umicorp.autolotto.ui.util.formatNumber
import com.umicorp.autolotto.ui.vm.LedgerUi
import kotlinx.coroutines.launch
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private val SYNCED_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("MM-dd HH:mm")
private val TABLE_BODY_HEIGHT = 220.dp
private const val SKELETON_ROWS = 4
private val MONTH_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM")

/** 내역 탭 맨 위 누적 요약. 손익을 빨강·초록으로 강조하지 않는다. 글자색은 카드의 내용 색(onSurface)을 물려받는다. */
@Composable
fun LedgerSummaryCard(ui: LedgerUi, onOpenMonthly: () -> Unit, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val s = ui.summary
    SectionCard(modifier = modifier) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.ledgerTitle), fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
            s?.since?.let { Text(stringResource(R.string.ledgerSince, it.toString()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        if (ui.prefilling && (s == null || s.units == 0)) {
            LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 12.dp))
            ui.prefillReached?.let {
                Text(
                    stringResource(R.string.ledgerPrefilling, MONTH_FMT.format(it)),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
        if (s != null) {
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Kpi(stringResource(R.string.ledgerPurchase), s.purchaseWon, stringResource(R.string.ledgerUnits, s.units), Modifier.weight(1f))
                Kpi(stringResource(R.string.ledgerPrize), s.prizeWon, stringResource(R.string.ledgerWins, s.wins), Modifier.weight(1f))
            }
            HorizontalDivider(Modifier.padding(top = 12.dp))
            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                Text(stringResource(R.string.ledgerReturn), modifier = Modifier.weight(1f))
                Text(s.returnPct?.let { String.format(Locale.US, "%.1f%%", it) } ?: "–", fontWeight = FontWeight.Bold)
            }
            HorizontalDivider()
            Row(
                Modifier.fillMaxWidth().clickable(onClick = onOpenMonthly).padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.ledgerMonthly), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                Text("›", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
        }
        if (ui.failed) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.ledgerFailed), style = MaterialTheme.typography.bodySmall, color = LgAmber, modifier = Modifier.weight(1f))
                TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
            }
        }
        MetaLine(ui)
    }
}

@Composable
private fun MetaLine(ui: LedgerUi) {
    val parts = buildList {
        if (ui.prefilling && ui.summary?.units?.let { it > 0 } == true) add(stringResource(R.string.ledgerPrefillingMeta))
        ui.syncedAt?.let { add(stringResource(R.string.ledgerSynced, SYNCED_FMT.format(it.atZoneSameInstant(LEDGER_ZONE)))) }
        ui.summary?.pending?.takeIf { it > 0 }?.let { add(stringResource(R.string.ledgerPendingCount, it)) }
    }
    if (parts.isNotEmpty()) {
        Text(parts.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Kpi(label: String, won: Long, sub: String, modifier: Modifier) {
    Column(
        modifier
            .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(14.dp))
            .padding(12.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(R.string.ledgerWon, formatNumber(won)), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
        Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** 월별 구매·당첨: 연도별 12개월 막대를 가로로 넘기고, 아래 표는 고른 해에 구매·당첨이 있던 달을 최신순으로 보인다. */
@Composable
fun LedgerMonthlySheet(summary: LedgerSummary, onDismiss: () -> Unit) {
    val purchaseColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
    val prizeColor = MaterialTheme.colorScheme.primary
    val pages = remember(summary) { yearPages(summary.monthly, YearMonth.now(LEDGER_ZONE)) }
    // 연도끼리 크기를 비교할 수 있게 전 기간 공통 최대값으로 막대 높이를 맞춘다.
    val max = remember(summary) { summary.monthly.maxOfOrNull { maxOf(it.purchaseWon, it.prizeWon) } ?: 0L }
    val pager = rememberPagerState(initialPage = pages.lastIndex) { pages.size }
    val scope = rememberCoroutineScope()
    val page = pages[pager.currentPage]
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.ledgerSheetTitle), fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, modifier = Modifier.weight(1f))
                YearStep("‹", pager.currentPage > 0, "${page.year - 1}") { scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } }
                Text(page.year.toString(), fontWeight = FontWeight.Bold)
                YearStep("›", pager.currentPage < pages.lastIndex, "${page.year + 1}") { scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Legend(purchaseColor, stringResource(R.string.ledgerPurchase))
                Spacer(Modifier.width(12.dp))
                Legend(prizeColor, stringResource(R.string.ledgerPrize))
            }
            HorizontalPager(pager, Modifier.fillMaxWidth()) { i -> YearBars(pages[i], max, purchaseColor, prizeColor) }
            TableRow(
                stringResource(R.string.ledgerColMonth), stringResource(R.string.ledgerPurchase),
                stringResource(R.string.ledgerPrize), stringResource(R.string.ledgerColWins), header = true,
            )
            // 연도를 넘겨도 시트 높이가 같도록 표 본문은 고정 높이 안에서 스크롤한다.
            key(page.year) {
                val rows = page.months.filterNotNull().filter { it.purchaseWon > 0 || it.prizeWon > 0 }.asReversed()
                Column(Modifier.fillMaxWidth().height(TABLE_BODY_HEIGHT).verticalScroll(rememberScrollState(), enabled = rows.isNotEmpty())) {
                    rows.forEach { m ->
                        HorizontalDivider()
                        TableRow(MONTH_FMT.format(m.month), formatNumber(m.purchaseWon), formatNumber(m.prizeWon), m.wins.toString())
                    }
                    if (rows.isEmpty()) repeat(SKELETON_ROWS) {
                        HorizontalDivider()
                        SkeletonRow()
                    }
                }
            }
        }
    }
}

@Composable
private fun YearStep(label: String, enabled: Boolean, target: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.semantics { contentDescription = target }) {
        Text(label, fontWeight = FontWeight.Bold, fontSize = 20.sp)
    }
}

@Composable
private fun Legend(color: Color, label: String) {
    Box(Modifier.size(10.dp).background(color, RoundedCornerShape(2.dp)))
    Spacer(Modifier.width(4.dp))
    Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** 1~12월 막대. 구매가 없던 달은 빈칸, 아직 오지 않은 달은 월 숫자만 흐리게 둔다. */
@Composable
private fun YearBars(page: YearPage, max: Long, purchaseColor: Color, prizeColor: Color) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        page.months.forEachIndexed { i, m ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(Modifier.height(96.dp), horizontalArrangement = Arrangement.spacedBy(1.dp), verticalAlignment = Alignment.Bottom) {
                    if (m != null && max > 0) {
                        Bar(m.purchaseWon.toFloat() / max, purchaseColor)
                        Bar(m.prizeWon.toFloat() / max, prizeColor)
                    }
                }
                Text("${i + 1}", style = MaterialTheme.typography.labelSmall, color = if (m == null) muted.copy(alpha = 0.4f) else muted)
            }
        }
    }
}

@Composable
private fun Bar(fraction: Float, color: Color) {
    if (fraction <= 0f) return
    Box(
        Modifier.width(6.dp).fillMaxHeight(fraction.coerceIn(0.02f, 1f))
            .background(color, RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)),
    )
}

/** 기록이 없는 해의 표 자리. 표 행과 같은 칸 비율의 회색 막대만 그린다. */
@Composable
private fun SkeletonRow() {
    val tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f)
    Row(Modifier.fillMaxWidth().padding(vertical = 11.dp).clearAndSetSemantics {}) {
        listOf(1f to Alignment.Start, 1.2f to Alignment.End, 1.2f to Alignment.End, 0.7f to Alignment.End).forEach { (w, align) ->
            Box(Modifier.weight(w), contentAlignment = if (align == Alignment.Start) Alignment.CenterStart else Alignment.CenterEnd) {
                Box(Modifier.fillMaxWidth(0.6f).height(14.dp).background(tint, RoundedCornerShape(7.dp)))
            }
        }
    }
}

@Composable
private fun TableRow(month: String, purchase: String, prize: String, wins: String, header: Boolean = false) {
    val weight = if (header) FontWeight.Bold else FontWeight.Normal
    val color = if (header) MaterialTheme.colorScheme.onSurfaceVariant else Color.Unspecified
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(month, fontWeight = weight, color = color, modifier = Modifier.weight(1f))
        Text(purchase, fontWeight = weight, color = color, textAlign = TextAlign.End, modifier = Modifier.weight(1.2f))
        Text(prize, fontWeight = weight, color = color, textAlign = TextAlign.End, modifier = Modifier.weight(1.2f))
        Text(wins, fontWeight = weight, color = color, textAlign = TextAlign.End, modifier = Modifier.weight(0.7f))
    }
}
