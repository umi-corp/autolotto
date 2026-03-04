import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:url_launcher/url_launcher.dart';
import '../l10n/app_localizations.dart';
import '../providers/providers.dart';
import '../app.dart';
import '../services/scheduler_service.dart';
import '../utils/ui_helpers.dart';

/// 설정 화면
class SettingsScreen extends ConsumerStatefulWidget {
  const SettingsScreen({super.key});

  @override
  ConsumerState<SettingsScreen> createState() => _SettingsScreenState();
}

class _SettingsScreenState extends ConsumerState<SettingsScreen> {
  static const _primary = Color(0xFF2D5BFF);
  static const _batteryChannel = MethodChannel('com.umicorp.autolotto/battery');
  // _dayNames는 이제 l10n에서 동적으로 가져옴

  final _idController = TextEditingController();
  final _pwController = TextEditingController();
  bool _isLoggingIn = false;
  bool _purchaseNoti = true;
  bool _resultNoti = true;

  @override
  void dispose() {
    _idController.dispose();
    _pwController.dispose();
    super.dispose();
  }

  Future<void> _login() async {
    final id = _idController.text.trim();
    final pw = _pwController.text.trim();
    if (id.isEmpty || pw.isEmpty) return;

    setState(() => _isLoggingIn = true);
    try {
      final auth = ref.read(authServiceProvider);
      await auth.login(id, pw);

      // 저장
      await ref.read(secureStorageProvider).saveCredentials(id, pw);
      ref.read(isLoggedInProvider.notifier).state = true;

      // 잔액 즉시 조회
      ref.read(balanceProvider.notifier).state = await auth.getBalance();

      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text(AppLocalizations.of(context)!.snackbarLoginSuccess), backgroundColor: Colors.green),
        );
      }
    } catch (e) {
      if (mounted) {
        debugPrint('로그인 오류: $e');
        final msg = e.toString().contains('INVALID_CREDENTIALS')
            ? AppLocalizations.of(context)!.errorInvalidCredentials
            : '로그인에 실패했습니다. 다시 시도해주세요.';
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('❌ $msg'), backgroundColor: Colors.red),
        );
      }
    } finally {
      if (mounted) setState(() => _isLoggingIn = false);
    }
  }

  void _showLoginDialog() {
    _idController.clear();
    _pwController.clear();
    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        title: Text(AppLocalizations.of(context)!.dialogLoginTitle),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            TextField(
              controller: _idController,
              decoration: InputDecoration(labelText: AppLocalizations.of(context)!.inputUserId, prefixIcon: const Icon(Icons.person)),
            ),
            const SizedBox(height: 12),
            TextField(
              controller: _pwController,
              decoration: InputDecoration(labelText: AppLocalizations.of(context)!.inputPassword, prefixIcon: const Icon(Icons.lock)),
              obscureText: true,
            ),
          ],
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx), child: Text(AppLocalizations.of(context)!.buttonCancel)),
          ElevatedButton(
            onPressed: _isLoggingIn ? null : () { Navigator.pop(ctx); _login(); },
            style: ElevatedButton.styleFrom(backgroundColor: _primary),
            child: Text(_isLoggingIn ? AppLocalizations.of(context)!.buttonLoggingIn : AppLocalizations.of(context)!.buttonLogin, style: const TextStyle(color: Colors.white)),
          ),
        ],
      ),
    );
  }

  Future<void> _refreshBalance() async {
    final auth = ref.read(authServiceProvider);
    final balance = await auth.getBalance();
    if (mounted) {
      ref.read(balanceProvider.notifier).state = balance;
    }
  }

  Future<void> _logout() async {
    ref.read(authServiceProvider).logout();
    await ref.read(secureStorageProvider).deleteCredentials();
    ref.read(isLoggedInProvider.notifier).state = false;
    ref.read(balanceProvider.notifier).state = 0;
    if (mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(AppLocalizations.of(context)!.snackbarLogoutSuccess)),
      );
    }
  }

  Future<void> _onPurchaseDayChanged(int? day) async {
    if (day == null) return;
    final hour = ref.read(autoPurchaseHourProvider);
    final minute = ref.read(autoPurchaseMinuteProvider);
    if (!_isValidPurchaseTime(day, hour, minute)) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(AppLocalizations.of(context)!.errorInvalidPurchaseTime),
            backgroundColor: Colors.red,
          ),
        );
      }
      return;
    }
    ref.read(autoPurchaseDayProvider.notifier).state = day;
    await ref.read(secureStorageProvider).setAutoPurchaseDay(day);
    await _rescheduleAlarms();
  }

  Future<void> _pickPurchaseTime() async {
    final hour = ref.read(autoPurchaseHourProvider);
    final minute = ref.read(autoPurchaseMinuteProvider);
    final picked = await showTimePicker(
      context: context,
      initialTime: TimeOfDay(hour: hour, minute: minute),
    );
    if (picked == null) return;
    final day = ref.read(autoPurchaseDayProvider);
    if (!_isValidPurchaseTime(day, picked.hour, picked.minute)) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(AppLocalizations.of(context)!.errorPurchaseTimeRestriction),
            backgroundColor: Colors.red,
          ),
        );
      }
      return;
    }
    ref.read(autoPurchaseHourProvider.notifier).state = picked.hour;
    ref.read(autoPurchaseMinuteProvider.notifier).state = picked.minute;
    final storage = ref.read(secureStorageProvider);
    await storage.setAutoPurchaseHour(picked.hour);
    await storage.setAutoPurchaseMinute(picked.minute);
    await _rescheduleAlarms();
  }

  Future<void> _rescheduleAlarms() async {
    final day = ref.read(autoPurchaseDayProvider);
    final hour = ref.read(autoPurchaseHourProvider);
    final minute = ref.read(autoPurchaseMinuteProvider);
    await SchedulerService.scheduleAutoPurchase(
      weekday: day,
      hour: hour,
      minute: minute,
    );
  }

  Future<void> _checkBatteryOptimization() async {
    try {
      await _batteryChannel.invokeMethod<String>('requestIgnoreBatteryOptimizations');
    } catch (_) {}
  }

  Future<void> _requestBatteryOptimization() async {
    try {
      final result = await _batteryChannel.invokeMethod<String>('requestIgnoreBatteryOptimizations');
      if (!mounted) return;
      if (result == 'already_excluded') {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text(AppLocalizations.of(context)!.snackbarBatteryAlreadyExcluded), backgroundColor: Colors.green),
        );
      } else if (result == 'fallback') {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text(AppLocalizations.of(context)!.snackbarBatteryManualDisable)),
        );
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text(AppLocalizations.of(context)!.errorBatterySettings('설정을 변경할 수 없습니다.')), backgroundColor: Colors.red),
        );
      }
    }
  }

  /// 구매 가능 시간 체크
  /// 평일/일요일: 06:00~23:59, 토요일: 06:00~19:59
  /// 토 20:00 ~ 일 05:59 판매 정지
  bool _isValidPurchaseTime(int day, int hour, int minute) {
    // day: 1=월 ~ 7=일 (Dart weekday)
    if (day == 6) {
      // 토요일: 06:00 ~ 19:59
      return hour >= 6 && hour < 20;
    } else if (day == 7) {
      // 일요일: 06:00 ~ 23:59
      return hour >= 6;
    } else {
      // 평일: 06:00 ~ 23:59
      return hour >= 6;
    }
  }


  @override
  Widget build(BuildContext context) {
    final isLoggedIn = ref.watch(isLoggedInProvider);
    final autoEnabled = ref.watch(autoEnabledProvider);
    final autoGames = ref.watch(autoGamesProvider);
    final balance = ref.watch(balanceProvider);
    final purchaseDay = ref.watch(autoPurchaseDayProvider);
    final purchaseHour = ref.watch(autoPurchaseHourProvider);
    final purchaseMinute = ref.watch(autoPurchaseMinuteProvider);

    return Scaffold(
      backgroundColor: const Color(0xFFF5F6FA),
      appBar: AppBar(
        title: Text(AppLocalizations.of(context)!.settingsTitle, style: const TextStyle(color: Colors.white, fontWeight: FontWeight.bold)),
        backgroundColor: _primary,
        elevation: 0,
        centerTitle: true,
      ),
      body: ListView(
        padding: const EdgeInsets.all(20),
        children: [
          // 계정 섹션
          _sectionTitle(AppLocalizations.of(context)!.sectionAccount),
          _card(
            child: Column(
              children: [
                _tile(
                  icon: Icons.person_rounded,
                  title: AppLocalizations.of(context)!.dhLotteryAccount,
                  subtitle: isLoggedIn ? AppLocalizations.of(context)!.statusLoggedIn : AppLocalizations.of(context)!.statusLoginRequired,
                  trailing: TextButton(
                    onPressed: isLoggedIn ? _logout : _showLoginDialog,
                    child: Text(isLoggedIn ? AppLocalizations.of(context)!.buttonLogout : AppLocalizations.of(context)!.buttonLogin, style: const TextStyle(color: _primary)),
                  ),
                ),
                const Divider(height: 1),
                ListTile(
                  leading: Container(
                    padding: const EdgeInsets.all(8),
                    decoration: BoxDecoration(color: const Color(0xFFE8F0FE), borderRadius: BorderRadius.circular(10)),
                    child: const Icon(Icons.account_balance_wallet_rounded, color: _primary, size: 22),
                  ),
                  title: Text(AppLocalizations.of(context)!.balanceTitle, style: const TextStyle(fontWeight: FontWeight.w500)),
                  subtitle: Text('₩${formatNumber(balance)}'),
                  trailing: Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      if (isLoggedIn) IconButton(
                        icon: const Icon(Icons.refresh_rounded, color: _primary, size: 20),
                        onPressed: _refreshBalance,
                      ),
                      if (isLoggedIn) IconButton(
                        icon: const Icon(Icons.add_card_rounded, color: Colors.orange, size: 20),
                        tooltip: AppLocalizations.of(context)!.chargeNow,
                        onPressed: () => launchUrl(Uri.parse('https://www.dhlottery.co.kr/mypage/mndpChrg'), mode: LaunchMode.externalApplication),
                      ),
                    ],
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(height: 24),

          // 자동 구매 섹션
          _sectionTitle(AppLocalizations.of(context)!.sectionAutoPurchase),
          _card(
            child: Column(
              children: [
                SwitchListTile(
                  title: Text(AppLocalizations.of(context)!.settingEnableAutoPurchase, style: const TextStyle(fontWeight: FontWeight.w500)),
                  subtitle: !isLoggedIn ? Text(AppLocalizations.of(context)!.hintLoginRequired, style: const TextStyle(color: Colors.red, fontSize: 12)) : null,
                  value: autoEnabled,
                  activeThumbColor: _primary,
                  onChanged: !isLoggedIn ? null : (v) async {
                    ref.read(autoEnabledProvider.notifier).state = v;
                    await ref.read(secureStorageProvider).setAutoEnabled(v);
                    if (v) {
                      await SchedulerService.scheduleAutoPurchase(
                        weekday: purchaseDay,
                        hour: purchaseHour,
                        minute: purchaseMinute,
                      );
                      await SchedulerService.scheduleCheckResult();
                      // 배터리 최적화 미해제 시 안내
                      _checkBatteryOptimization();
                    } else {
                      await SchedulerService.cancelAll();
                    }
                  },
                ),
                if (autoEnabled) ...[
                  const Divider(height: 1),
                  ListTile(
                    leading: Container(
                      padding: const EdgeInsets.all(8),
                      decoration: BoxDecoration(color: const Color(0xFFE8F0FE), borderRadius: BorderRadius.circular(10)),
                      child: const Icon(Icons.confirmation_number_rounded, color: _primary, size: 22),
                    ),
                    title: Text(AppLocalizations.of(context)!.gamesConfigured(autoGames), style: const TextStyle(fontWeight: FontWeight.w500)),
                    subtitle: Text(autoGames > 0 ? AppLocalizations.of(context)!.hintChangeInNumberTab : AppLocalizations.of(context)!.hintSetupGamesInNumberTab, style: const TextStyle(fontSize: 13)),
                    trailing: const Icon(Icons.arrow_forward_ios_rounded, size: 16, color: Colors.grey),
                    onTap: () => AppShell.of(context)?.switchTab(1),
                  ),
                  const Divider(height: 1),
                  // 구매 요일 선택
                  ListTile(
                    leading: Container(
                      padding: const EdgeInsets.all(8),
                      decoration: BoxDecoration(color: const Color(0xFFE8F0FE), borderRadius: BorderRadius.circular(10)),
                      child: const Icon(Icons.calendar_today_rounded, color: _primary, size: 22),
                    ),
                    title: Text(AppLocalizations.of(context)!.settingPurchaseDay, style: const TextStyle(fontWeight: FontWeight.w500)),
                    trailing: DropdownButton<int>(
                      value: purchaseDay,
                      underline: const SizedBox(),
                      items: List.generate(7, (i) {
                        final d = i + 1;
                        final dayNames = localizedDayNames(AppLocalizations.of(context)!);
                        return DropdownMenuItem(value: d, child: Text(AppLocalizations.of(context)!.dayFormat(dayNames[i])));
                      }),
                      onChanged: _onPurchaseDayChanged,
                    ),
                  ),
                  const Divider(height: 1),
                  // 구매 시간 선택
                  ListTile(
                    leading: Container(
                      padding: const EdgeInsets.all(8),
                      decoration: BoxDecoration(color: const Color(0xFFE8F0FE), borderRadius: BorderRadius.circular(10)),
                      child: const Icon(Icons.access_time_rounded, color: _primary, size: 22),
                    ),
                    title: Text(AppLocalizations.of(context)!.settingPurchaseTime, style: const TextStyle(fontWeight: FontWeight.w500)),
                    trailing: TextButton(
                      onPressed: _pickPurchaseTime,
                      child: Text(
                        '${purchaseHour.toString().padLeft(2, '0')}:${purchaseMinute.toString().padLeft(2, '0')}',
                        style: const TextStyle(color: _primary, fontSize: 16, fontWeight: FontWeight.w600),
                      ),
                    ),
                  ),
                  const Divider(height: 1),
                  // 배터리 최적화 제외
                  ListTile(
                    leading: Container(
                      padding: const EdgeInsets.all(8),
                      decoration: BoxDecoration(color: const Color(0xFFFFF3E0), borderRadius: BorderRadius.circular(10)),
                      child: const Icon(Icons.battery_saver_rounded, color: Colors.orange, size: 22),
                    ),
                    title: Text(AppLocalizations.of(context)!.settingBatteryOptimization, style: const TextStyle(fontWeight: FontWeight.w500)),
                    subtitle: Text(AppLocalizations.of(context)!.hintBatteryOptimization, style: const TextStyle(fontSize: 12)),
                    trailing: const Icon(Icons.open_in_new_rounded, size: 18, color: Colors.grey),
                    onTap: _requestBatteryOptimization,
                  ),
                ],
              ],
            ),
          ),
          const SizedBox(height: 24),

          // 알림 섹션
          _sectionTitle(AppLocalizations.of(context)!.sectionNotifications),
          _card(
            child: Column(
              children: [
                SwitchListTile(
                  title: Text(AppLocalizations.of(context)!.settingPurchaseNoti),
                  subtitle: Text(formatPurchaseScheduleL10n(AppLocalizations.of(context)!, purchaseDay, purchaseHour, purchaseMinute)),
                  value: _purchaseNoti,
                  activeThumbColor: _primary,
                  onChanged: (v) => setState(() => _purchaseNoti = v),
                ),
                const Divider(height: 1),
                SwitchListTile(
                  title: Text(AppLocalizations.of(context)!.settingResultNoti),
                  subtitle: Text(AppLocalizations.of(context)!.notificationResultTime),
                  value: _resultNoti,
                  activeThumbColor: _primary,
                  onChanged: (v) => setState(() => _resultNoti = v),
                ),
                const Divider(height: 1),
                _buildBalanceAlertSection(),
              ],
            ),
          ),
          const SizedBox(height: 24),

          // 정보 섹션
          _sectionTitle(AppLocalizations.of(context)!.sectionAppInfo),
          _card(
            child: Column(
              children: [
                _tile(icon: Icons.info_outline_rounded, title: AppLocalizations.of(context)!.settingVersion, subtitle: '1.0.0'),
                const Divider(height: 1),
                _buildLanguageTile(context),
                const Divider(height: 1),
                ListTile(
                  leading: Container(
                    padding: const EdgeInsets.all(8),
                    decoration: BoxDecoration(color: const Color(0xFFE8F0FE), borderRadius: BorderRadius.circular(10)),
                    child: const Icon(Icons.code_rounded, color: _primary, size: 22),
                  ),
                  title: Text(AppLocalizations.of(context)!.settingOpenSource, style: const TextStyle(fontWeight: FontWeight.w500)),
                  subtitle: const Text('github.com/free4416/umicorp-autolotto'),
                  trailing: const Icon(Icons.open_in_new_rounded, size: 16, color: Colors.grey),
                  onTap: () => launchUrl(Uri.parse('https://github.com/free4416/umicorp-autolotto'), mode: LaunchMode.externalApplication),
                ),
                const Divider(height: 1),
                ListTile(
                  leading: Icon(Icons.delete_outline_rounded, color: Colors.red[400]),
                  title: Text(AppLocalizations.of(context)!.settingResetData, style: TextStyle(color: Colors.red[400])),
                  onTap: () => _showResetDialog(context),
                ),
              ],
            ),
          ),
          const SizedBox(height: 40),
        ],
      ),
    );
  }

  Widget _sectionTitle(String title) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 10, left: 4),
      child: Text(title, style: const TextStyle(fontSize: 15, fontWeight: FontWeight.w600, color: Colors.black87)),
    );
  }

  Widget _card({required Widget child}) {
    return Container(
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(16),
        boxShadow: [BoxShadow(color: Colors.black.withValues(alpha: 0.05), blurRadius: 10)],
      ),
      child: child,
    );
  }

  Widget _tile({required IconData icon, required String title, String? subtitle, Widget? trailing}) {
    return ListTile(
      leading: Container(
        padding: const EdgeInsets.all(8),
        decoration: BoxDecoration(color: const Color(0xFFE8F0FE), borderRadius: BorderRadius.circular(10)),
        child: Icon(icon, color: _primary, size: 22),
      ),
      title: Text(title, style: const TextStyle(fontWeight: FontWeight.w500)),
      subtitle: subtitle != null ? Text(subtitle) : null,
      trailing: trailing,
    );
  }

  Widget _buildLanguageTile(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    final currentLocale = ref.watch(appLocaleProvider);

    // 현재 선택된 언어 표시 텍스트
    String currentLabel;
    if (currentLocale == null) {
      currentLabel = l10n.languageSystem;
    } else if (currentLocale.languageCode == 'ko') {
      currentLabel = l10n.languageKo;
    } else if (currentLocale.languageCode == 'en') {
      currentLabel = l10n.languageEn;
    } else {
      currentLabel = l10n.languageJa;
    }

    return ListTile(
      leading: Container(
        padding: const EdgeInsets.all(8),
        decoration: BoxDecoration(color: const Color(0xFFE8F0FE), borderRadius: BorderRadius.circular(10)),
        child: const Icon(Icons.language_rounded, color: _primary, size: 22),
      ),
      title: Text(l10n.settingLanguage, style: const TextStyle(fontWeight: FontWeight.w500)),
      subtitle: Text(currentLabel, style: const TextStyle(fontSize: 12)),
      trailing: DropdownButton<String>(
        value: currentLocale?.languageCode ?? 'system',
        underline: const SizedBox(),
        items: [
          DropdownMenuItem(value: 'system', child: Text(l10n.languageSystem)),
          DropdownMenuItem(value: 'ko', child: Text(l10n.languageKo)),
          DropdownMenuItem(value: 'en', child: Text(l10n.languageEn)),
          DropdownMenuItem(value: 'ja', child: Text(l10n.languageJa)),
        ],
        onChanged: (value) async {
          Locale? newLocale;
          if (value != null && value != 'system') {
            newLocale = Locale(value);
          }
          ref.read(appLocaleProvider.notifier).state = newLocale;
          // 설정 저장
          final storage = ref.read(secureStorageProvider);
          await storage.setLanguage(value ?? 'system');
        },
      ),
    );
  }

  Widget _buildBalanceAlertSection() {
    final l10n = AppLocalizations.of(context)!;
    final alertEnabled = ref.watch(balanceAlertEnabledProvider);
    final threshold = ref.watch(balanceAlertThresholdProvider);

    return Column(children: [
      SwitchListTile(
        title: Text(l10n.balanceAlertTitle),
        subtitle: Text(l10n.balanceAlertDesc),
        value: alertEnabled,
        activeThumbColor: _primary,
        onChanged: (v) async {
          ref.read(balanceAlertEnabledProvider.notifier).state = v;
          await ref.read(secureStorageProvider).setBalanceAlertEnabled(v);
        },
      ),
      if (alertEnabled) ...[
        const Divider(height: 1),
        ListTile(
          leading: Container(
            padding: const EdgeInsets.all(8),
            decoration: BoxDecoration(color: const Color(0xFFFFF3E0), borderRadius: BorderRadius.circular(10)),
            child: const Icon(Icons.monetization_on_rounded, color: Colors.orange, size: 22),
          ),
          title: Text(l10n.balanceThreshold, style: const TextStyle(fontWeight: FontWeight.w500)),
          trailing: DropdownButton<int>(
            value: [5000, 10000, 20000].contains(threshold) ? threshold : -1,
            underline: const SizedBox(),
            items: [
              DropdownMenuItem(value: 5000, child: Text('₩${formatNumber(5000)}')),
              DropdownMenuItem(value: 10000, child: Text('₩${formatNumber(10000)}')),
              DropdownMenuItem(value: 20000, child: Text('₩${formatNumber(20000)}')),
              DropdownMenuItem(value: -1, child: Text(
                [5000, 10000, 20000].contains(threshold) ? l10n.thresholdCustom : '₩${formatNumber(threshold)}',
              )),
            ],
            onChanged: (v) async {
              if (v == null) return;
              if (v == -1) {
                _showThresholdInputDialog();
                return;
              }
              ref.read(balanceAlertThresholdProvider.notifier).state = v;
              await ref.read(secureStorageProvider).setBalanceAlertThreshold(v);
            },
          ),
        ),
      ],
    ]);
  }

  void _showThresholdInputDialog() {
    final l10n = AppLocalizations.of(context)!;
    final controller = TextEditingController(
      text: ref.read(balanceAlertThresholdProvider).toString(),
    );
    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        title: Text(l10n.thresholdInputTitle),
        content: TextField(
          controller: controller,
          keyboardType: TextInputType.number,
          decoration: InputDecoration(
            hintText: l10n.thresholdInputHint,
            prefixText: '₩ ',
          ),
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx), child: Text(l10n.buttonCancel)),
          ElevatedButton(
            onPressed: () async {
              final value = int.tryParse(controller.text.trim());
              if (value != null && value > 0) {
                ref.read(balanceAlertThresholdProvider.notifier).state = value;
                await ref.read(secureStorageProvider).setBalanceAlertThreshold(value);
              }
              if (ctx.mounted) Navigator.pop(ctx);
            },
            style: ElevatedButton.styleFrom(backgroundColor: _primary),
            child: Text(l10n.buttonConfirm, style: const TextStyle(color: Colors.white)),
          ),
        ],
      ),
    );
  }

  void _showResetDialog(BuildContext context) {
    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        title: Text(AppLocalizations.of(context)!.dialogResetTitle),
        content: Text(AppLocalizations.of(context)!.dialogResetMessage),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx), child: Text(AppLocalizations.of(context)!.buttonCancel)),
          TextButton(
            onPressed: () async {
              Navigator.pop(ctx);
              await ref.read(secureStorageProvider).clearAll();
              ref.read(isLoggedInProvider.notifier).state = false;
              ref.read(balanceProvider.notifier).state = 0;
              ref.read(autoEnabledProvider.notifier).state = false;
            },
            child: Text(AppLocalizations.of(context)!.buttonReset2, style: TextStyle(color: Colors.red[400])),
          ),
        ],
      ),
    );
  }

}
