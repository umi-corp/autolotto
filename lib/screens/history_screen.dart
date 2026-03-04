import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../l10n/app_localizations.dart';
import '../providers/providers.dart';
import '../models/purchase.dart';
import '../utils/ui_helpers.dart';

/// 구매/당첨 기록 화면
class HistoryScreen extends ConsumerStatefulWidget {
  const HistoryScreen({super.key});

  @override
  ConsumerState<HistoryScreen> createState() => _HistoryScreenState();
}

class _HistoryScreenState extends ConsumerState<HistoryScreen> {
  static const _primary = Color(0xFF2D5BFF);
  List<Purchase> _purchases = [];
  bool _loading = false;
  String? _error;

  @override
  void initState() {
    super.initState();
    _loadHistory();
  }

  Future<void> _loadHistory() async {
    // 먼저 로컬 DB
    final local = ref.read(purchaseRepoProvider).getAll();
    if (local.isNotEmpty) {
      setState(() => _purchases = local);
    }

    // 로그인 상태면 서버에서도 조회
    final isLoggedIn = ref.read(isLoggedInProvider);
    if (!isLoggedIn) return;

    setState(() { _loading = true; _error = null; });
    try {
      final history = ref.read(historyServiceProvider);
      final remote = await history.fetchRecentPurchases(count: 5);

      // API 결과를 직접 표시 (최신 당첨 결과 포함)
      if (mounted) {
        setState(() {
          _purchases = remote;
          _purchases.sort((a, b) => b.round.compareTo(a.round));
        });
      }
    } catch (e) {
      debugPrint('구매내역 로드 오류: $e');
      if (mounted) setState(() => _error = '데이터를 불러올 수 없습니다.');
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFFF5F6FA),
      appBar: AppBar(
        title: Text(AppLocalizations.of(context)!.historyTitle, style: const TextStyle(color: Colors.white, fontWeight: FontWeight.bold)),
        backgroundColor: _primary, elevation: 0, centerTitle: true,
        actions: [
          IconButton(
            icon: const Icon(Icons.refresh_rounded, color: Colors.white70),
            onPressed: _loading ? null : _loadHistory,
          ),
        ],
      ),
      body: _loading && _purchases.isEmpty
          ? const Center(child: CircularProgressIndicator())
          : _purchases.isEmpty
              ? Center(
                  child: Column(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      Icon(Icons.history_rounded, size: 64, color: Colors.grey[300]),
                      const SizedBox(height: 16),
                      Text(
                        ref.watch(isLoggedInProvider)
                            ? AppLocalizations.of(context)!.historyNoRecords
                            : AppLocalizations.of(context)!.historyLoginToLoad,
                        style: TextStyle(color: Colors.grey[500], fontSize: 16),
                      ),
                      if (_error != null) ...[
                        const SizedBox(height: 8),
                        Text(AppLocalizations.of(context)!.historyLoadError(_error!), style: TextStyle(color: Colors.red[300], fontSize: 13)),
                      ],
                    ],
                  ),
                )
              : RefreshIndicator(
                  onRefresh: _loadHistory,
                  child: ListView.builder(
                    padding: const EdgeInsets.all(20),
                    itemCount: _purchases.length + (_loading ? 1 : 0),
                    itemBuilder: (context, index) {
                      if (index == 0 && _loading) {
                        return const Padding(
                          padding: EdgeInsets.only(bottom: 16),
                          child: Center(child: CircularProgressIndicator(strokeWidth: 2)),
                        );
                      }
                      final i = _loading ? index - 1 : index;
                      return _buildHistoryCard(_purchases[i]);
                    },
                  ),
                ),
    );
  }

  /// 게임별 등수 배지 색상
  Color _rankBadgeColor(String rankCode) {
    switch (rankCode) {
      case 'rank1': return const Color(0xFFE65100);
      case 'rank2': return const Color(0xFFFBC400);
      case 'rank3': return const Color(0xFF2D5BFF);
      case 'rank4': return const Color(0xFF4CAF50);
      case 'rank5': return const Color(0xFF8BC34A);
      default: return const Color(0xFFBDBDBD);
    }
  }

  Widget _buildHistoryCard(Purchase item) {
    final l10n = AppLocalizations.of(context)!;
    final isWinner = item.rank != null && item.rank != 'nowin';
    final dateStr = '${item.date.year}-${item.date.month.toString().padLeft(2, '0')}-${item.date.day.toString().padLeft(2, '0')}';

    // 헤더 태그 텍스트 결정
    String headerTag;
    if (!item.checked) {
      headerTag = l10n.statusPending;
    } else if (isWinner) {
      headerTag = l10n.rankWithEmoji(localizedRank(l10n, item.rank!));
    } else {
      headerTag = l10n.statusNoWin;
    }

    return Container(
      margin: const EdgeInsets.only(bottom: 16),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(16),
        border: isWinner ? Border.all(color: const Color(0xFFFBC400), width: 2) : null,
        boxShadow: [BoxShadow(color: Colors.black.withValues(alpha: 0.05), blurRadius: 10)],
      ),
      child: Column(
        children: [
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 14),
            decoration: BoxDecoration(
              color: isWinner ? const Color(0xFFFFF8E1) : Colors.grey[50],
              borderRadius: const BorderRadius.vertical(top: Radius.circular(16)),
            ),
            child: Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Row(children: [
                  Text(l10n.roundLabel(item.round), style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 16)),
                  const SizedBox(width: 8),
                  Text(dateStr, style: TextStyle(color: Colors.grey[500], fontSize: 13)),
                ]),
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 4),
                  decoration: BoxDecoration(
                    color: !item.checked
                        ? Colors.blue[50]
                        : (isWinner ? const Color(0xFFFBC400) : Colors.grey[200]),
                    borderRadius: BorderRadius.circular(20),
                  ),
                  child: Text(
                    headerTag,
                    style: TextStyle(
                      color: !item.checked ? _primary : (isWinner ? Colors.white : Colors.grey[600]),
                      fontWeight: FontWeight.bold, fontSize: 13,
                    ),
                  ),
                ),
              ],
            ),
          ),
          Padding(
            padding: const EdgeInsets.all(16),
            child: Column(
              children: List.generate(item.numbers.length, (i) {
                final gameRank = (item.gameRanks != null && i < item.gameRanks!.length)
                    ? item.gameRanks![i]
                    : null;
                final isGameWinner = gameRank != null && gameRank != 'nowin';

                return Padding(
                  padding: const EdgeInsets.only(bottom: 8),
                  child: Row(children: [
                    SizedBox(width: 24,
                      child: Text(String.fromCharCode(65 + i),
                        style: TextStyle(color: Colors.grey[400], fontWeight: FontWeight.bold, fontSize: 13))),
                    ...item.numbers[i].map((n) => Padding(
                      padding: const EdgeInsets.only(right: 6),
                      child: Container(width: 32, height: 32,
                        decoration: BoxDecoration(shape: BoxShape.circle, color: ballColor(n)),
                        child: Center(child: Text('$n',
                          style: const TextStyle(color: Colors.white, fontWeight: FontWeight.bold, fontSize: 12)))),
                    )),
                    if (gameRank != null) ...[
                      const SizedBox(width: 4),
                      Container(
                        padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
                        decoration: BoxDecoration(
                          color: isGameWinner
                              ? _rankBadgeColor(gameRank).withValues(alpha: 0.15)
                              : Colors.grey[100],
                          borderRadius: BorderRadius.circular(10),
                        ),
                        child: Text(
                          isGameWinner ? l10n.rankWithEmoji(localizedRank(l10n, gameRank)) : localizedRank(l10n, gameRank),
                          style: TextStyle(
                            color: isGameWinner ? _rankBadgeColor(gameRank) : Colors.grey[500],
                            fontWeight: FontWeight.bold,
                            fontSize: 11,
                          ),
                        ),
                      ),
                    ],
                  ]),
                );
              }),
            ),
          ),
          if (isWinner)
            Container(
              width: double.infinity, padding: const EdgeInsets.all(14),
              decoration: const BoxDecoration(
                color: Color(0xFFFFF8E1),
                borderRadius: BorderRadius.vertical(bottom: Radius.circular(16)),
              ),
              child: Text(l10n.prizeLabel(formatNumber(item.prize)),
                style: const TextStyle(color: Color(0xFFE65100), fontWeight: FontWeight.bold, fontSize: 15),
                textAlign: TextAlign.center),
            ),
        ],
      ),
    );
  }

}
