import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../app.dart';
import '../l10n/app_localizations.dart';
import 'package:url_launcher/url_launcher.dart';
import '../providers/providers.dart';
import '../services/balance_alert_service.dart';
import '../services/purchase_service.dart';
import '../utils/ui_helpers.dart';

/// 홈 화면
class HomeScreen extends ConsumerStatefulWidget {
  const HomeScreen({super.key});

  @override
  ConsumerState<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends ConsumerState<HomeScreen> {
  List<int>? _winningNumbers;
  int? _bonusNumber;
  int? _winningRound;
  bool _loadingNumbers = false;
  late Timer _countdownTimer;
  Duration _timeUntilDraw = Duration.zero;

  @override
  void initState() {
    super.initState();
    _fetchWinningNumbers();
    _updateCountdown();
    _countdownTimer = Timer.periodic(const Duration(seconds: 1), (_) => _updateCountdown());
  }

  @override
  void dispose() {
    _countdownTimer.cancel();
    super.dispose();
  }

  void _updateCountdown() {
    final now = DateTime.now();
    var nextDraw = now;
    while (nextDraw.weekday != DateTime.saturday) {
      nextDraw = nextDraw.add(const Duration(days: 1));
    }
    nextDraw = DateTime(nextDraw.year, nextDraw.month, nextDraw.day, 20, 45);
    if (now.isAfter(nextDraw)) {
      nextDraw = nextDraw.add(const Duration(days: 7));
    }
    if (mounted) setState(() => _timeUntilDraw = nextDraw.difference(now));
  }

  Future<void> _fetchWinningNumbers() async {
    setState(() => _loadingNumbers = true);
    try {
      final resultService = ref.read(resultServiceProvider);
      final round = PurchaseService.getCurrentRound() - 1; // 지난 회차
      final result = await resultService.getWinningNumbers(roundNo: round);
      if (mounted && result != null) {
        setState(() {
          _winningNumbers = result.numbers;
          _bonusNumber = result.bonus;
          _winningRound = result.round;
        });
      }
    } catch (e) {
      debugPrint('당첨번호 조회 실패: $e');
    } finally {
      if (mounted) setState(() => _loadingNumbers = false);
    }
  }

  Future<void> _refreshAll() async {
    await Future.wait([
      _fetchWinningNumbers(),
      _refreshBalance(),
    ]);
  }

  Future<void> _refreshBalance() async {
    final auth = ref.read(authServiceProvider);
    final balance = await auth.getBalance();
    if (mounted) {
      ref.read(balanceProvider.notifier).state = balance;
      // 잔액 부족 알림 체크
      await BalanceAlertService.checkAndNotify(
        balance: balance,
        enabled: ref.read(balanceAlertEnabledProvider),
        threshold: ref.read(balanceAlertThresholdProvider),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    final round = ref.watch(currentRoundProvider);
    final balance = ref.watch(balanceProvider);
    final autoEnabled = ref.watch(autoEnabledProvider);
    final isLoggedIn = ref.watch(isLoggedInProvider);
    final balanceAlertEnabled = ref.watch(balanceAlertEnabledProvider);
    final balanceAlertThreshold = ref.watch(balanceAlertThresholdProvider);

    return Scaffold(
      backgroundColor: const Color(0xFFF5F6FA),
      appBar: AppBar(
        title: const Text('AutoLotto', style: TextStyle(color: Colors.white, fontWeight: FontWeight.bold)),
        backgroundColor: const Color(0xFF2D5BFF),
        elevation: 0, centerTitle: true,
        actions: [
          Icon(isLoggedIn ? Icons.cloud_done_rounded : Icons.cloud_off_rounded,
            color: isLoggedIn ? Colors.white70 : Colors.white38),
          const SizedBox(width: 16),
        ],
      ),
      body: SafeArea(
        child: RefreshIndicator(
          onRefresh: _refreshAll,
          child: SingleChildScrollView(
            physics: const AlwaysScrollableScrollPhysics(),
            padding: const EdgeInsets.all(20),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              _buildCountdownCard(round),
              const SizedBox(height: 24),
              _buildWinningNumbers(),
              const SizedBox(height: 24),
              _buildBalanceCard(balance, balanceAlertEnabled, balanceAlertThreshold),
              const SizedBox(height: 16),

              // 자동 구매 상태
              Container(
                width: double.infinity,
                padding: const EdgeInsets.all(16),
                decoration: BoxDecoration(color: Colors.white, borderRadius: BorderRadius.circular(16),
                  boxShadow: [BoxShadow(color: Colors.black.withValues(alpha: 0.05), blurRadius: 10)]),
                child: Row(children: [
                  Icon(autoEnabled ? Icons.autorenew_rounded : Icons.pause_circle_outline_rounded,
                    color: autoEnabled ? Colors.green : Colors.grey, size: 28),
                  const SizedBox(width: 12),
                  Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                    Text(autoEnabled ? AppLocalizations.of(context)!.autoPurchaseEnabled : AppLocalizations.of(context)!.autoPurchaseDisabled,
                      style: const TextStyle(fontWeight: FontWeight.w600, fontSize: 15)),
                    Text(autoEnabled
                      ? AppLocalizations.of(context)!.autoPurchaseSchedule(formatPurchaseScheduleL10n(AppLocalizations.of(context)!, ref.watch(autoPurchaseDayProvider), ref.watch(autoPurchaseHourProvider), ref.watch(autoPurchaseMinuteProvider)))
                      : AppLocalizations.of(context)!.enableInSettings,
                      style: TextStyle(color: Colors.grey[500], fontSize: 13)),
                  ])),
                ]),
              ),
              const SizedBox(height: 16),

              // 번호 설정 버튼
              SizedBox(
                width: double.infinity, height: 56,
                child: ElevatedButton(
                  onPressed: () => AppShell.of(context)?.switchTab(1),
                  style: ElevatedButton.styleFrom(backgroundColor: const Color(0xFF2D5BFF),
                    shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
                    elevation: 4, shadowColor: const Color(0xFF2D5BFF).withValues(alpha: 0.4)),
                  child: Text(AppLocalizations.of(context)!.buttonSetupNumbers,
                    style: TextStyle(color: Colors.white, fontSize: 16, fontWeight: FontWeight.bold)),
                ),
              ),
            ],
          ),
        ),
        ),
      ),
    );
  }

  Widget _buildCountdownCard(int round) {
    final days = _timeUntilDraw.inDays;
    final hours = _timeUntilDraw.inHours % 24;
    final mins = _timeUntilDraw.inMinutes % 60;
    final secs = _timeUntilDraw.inSeconds % 60;

    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(24),
      decoration: BoxDecoration(
        gradient: const LinearGradient(colors: [Color(0xFF2D5BFF), Color(0xFF6B8CFF)]),
        borderRadius: BorderRadius.circular(20),
        boxShadow: [BoxShadow(color: const Color(0xFF2D5BFF).withValues(alpha: 0.3), blurRadius: 15, offset: const Offset(0, 6))],
      ),
      child: Column(children: [
        Text(AppLocalizations.of(context)!.countdownTitle(round), style: const TextStyle(color: Colors.white70, fontSize: 14)),
        const SizedBox(height: 8),
        Row(mainAxisAlignment: MainAxisAlignment.center, children: [
          _countdownUnit('$days', AppLocalizations.of(context)!.countdownDays),
          const SizedBox(width: 16),
          _countdownUnit('$hours', AppLocalizations.of(context)!.countdownHours),
          const SizedBox(width: 16),
          _countdownUnit('$mins', AppLocalizations.of(context)!.countdownMinutes),
          const SizedBox(width: 16),
          _countdownUnit(secs.toString().padLeft(2, '0'), AppLocalizations.of(context)!.countdownSeconds),
        ]),
      ]),
    );
  }

  Widget _countdownUnit(String value, String label) {
    return Column(children: [
      Text(value, style: const TextStyle(color: Colors.white, fontSize: 32, fontWeight: FontWeight.bold)),
      Text(label, style: const TextStyle(color: Colors.white60, fontSize: 12)),
    ]);
  }

  Widget _buildWinningNumbers() {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          _winningRound != null ? AppLocalizations.of(context)!.winningNumbersWithRound(_winningRound!) : AppLocalizations.of(context)!.winningNumbersPrevious,
          style: const TextStyle(color: Colors.black87, fontSize: 15, fontWeight: FontWeight.w600),
        ),
        const SizedBox(height: 12),
        Container(
          width: double.infinity,
          padding: const EdgeInsets.symmetric(vertical: 16, horizontal: 12),
          decoration: BoxDecoration(color: Colors.white, borderRadius: BorderRadius.circular(16),
            boxShadow: [BoxShadow(color: Colors.black.withValues(alpha: 0.05), blurRadius: 10)]),
          child: _loadingNumbers
              ? const Center(child: Padding(padding: EdgeInsets.all(8), child: CircularProgressIndicator(strokeWidth: 2)))
              : _winningNumbers != null
                  ? Row(mainAxisAlignment: MainAxisAlignment.spaceEvenly, children: [
                      ..._winningNumbers!.map((n) => _ball(n)),
                      const Text('+', style: TextStyle(color: Colors.grey, fontSize: 18)),
                      _ball(_bonusNumber!, isBonus: true),
                    ])
                  : Center(child: Padding(
                      padding: const EdgeInsets.all(8),
                      child: Text(AppLocalizations.of(context)!.winningNumbersLoadError, style: TextStyle(color: Colors.grey[400], fontSize: 14)),
                    )),
        ),
      ],
    );
  }

  Widget _ball(int n, {bool isBonus = false}) {
    return Container(
      width: 38, height: 38,
      decoration: BoxDecoration(shape: BoxShape.circle, color: ballColor(n),
        border: isBonus ? Border.all(color: Colors.black26, width: 2) : null,
        boxShadow: [BoxShadow(color: ballColor(n).withValues(alpha: 0.4), blurRadius: 4)]),
      child: Center(child: Text('$n', style: const TextStyle(color: Colors.white, fontWeight: FontWeight.bold, fontSize: 14))),
    );
  }

  Widget _buildBalanceCard(int balance, bool alertEnabled, int threshold) {
    final isLow = alertEnabled && balance <= threshold && balance > 0;
    return Container(
      width: double.infinity, padding: const EdgeInsets.all(20),
      decoration: BoxDecoration(color: Colors.white, borderRadius: BorderRadius.circular(16),
        boxShadow: [BoxShadow(color: Colors.black.withValues(alpha: 0.05), blurRadius: 10)]),
      child: Column(
        children: [
          Row(children: [
            Container(padding: const EdgeInsets.all(10),
              decoration: BoxDecoration(color: isLow ? const Color(0xFFFFF3E0) : const Color(0xFFE8F0FE), borderRadius: BorderRadius.circular(12)),
              child: Icon(Icons.account_balance_wallet_rounded, color: isLow ? Colors.orange : const Color(0xFF2D5BFF), size: 24)),
            const SizedBox(width: 12),
            Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Text(AppLocalizations.of(context)!.balanceTitle, style: const TextStyle(color: Colors.black54, fontSize: 13)),
              const SizedBox(height: 4),
              Text('₩${formatNumber(balance)}', style: TextStyle(color: isLow ? Colors.orange : Colors.black87, fontSize: 22, fontWeight: FontWeight.bold)),
            ])),
          ]),
          if (isLow) ...[
            const SizedBox(height: 12),
            SizedBox(
              width: double.infinity,
              child: OutlinedButton.icon(
                onPressed: () => launchUrl(Uri.parse('https://www.dhlottery.co.kr/mypage/mndpChrg'), mode: LaunchMode.externalApplication),
                icon: const Icon(Icons.add_card_rounded, size: 18),
                label: Text(AppLocalizations.of(context)!.chargeNow),
                style: OutlinedButton.styleFrom(
                  foregroundColor: Colors.orange,
                  side: const BorderSide(color: Colors.orange),
                  shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
                ),
              ),
            ),
          ],
        ],
      ),
    );
  }

}
