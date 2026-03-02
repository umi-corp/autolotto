import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:url_launcher/url_launcher.dart';
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
  static const _dayNames = ['월', '화', '수', '목', '금', '토', '일'];

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
          const SnackBar(content: Text('✅ 로그인 성공!'), backgroundColor: Colors.green),
        );
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('❌ $e'), backgroundColor: Colors.red),
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
        title: const Text('동행복권 로그인'),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            TextField(
              controller: _idController,
              decoration: const InputDecoration(labelText: '아이디', prefixIcon: Icon(Icons.person)),
            ),
            const SizedBox(height: 12),
            TextField(
              controller: _pwController,
              decoration: const InputDecoration(labelText: '비밀번호', prefixIcon: Icon(Icons.lock)),
              obscureText: true,
            ),
          ],
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx), child: const Text('취소')),
          ElevatedButton(
            onPressed: _isLoggingIn ? null : () { Navigator.pop(ctx); _login(); },
            style: ElevatedButton.styleFrom(backgroundColor: _primary),
            child: Text(_isLoggingIn ? '로그인 중...' : '로그인', style: const TextStyle(color: Colors.white)),
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
        const SnackBar(content: Text('로그아웃 완료')),
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
          const SnackBar(
            content: Text('현재 설정된 시간이 해당 요일에 구매 불가합니다. 시간을 먼저 변경해주세요.'),
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
          const SnackBar(
            content: Text('해당 시간에는 구매할 수 없습니다.\n평일/일: 06:00~23:59, 토: 06:00~19:59'),
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

  Future<void> _requestBatteryOptimization() async {
    try {
      final result = await _batteryChannel.invokeMethod<String>('requestIgnoreBatteryOptimizations');
      if (!mounted) return;
      if (result == 'already_excluded') {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('✅ 이미 배터리 최적화에서 제외되어 있습니다'), backgroundColor: Colors.green),
        );
      } else if (result == 'fallback') {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('앱 설정에서 배터리 최적화를 직접 해제해주세요')),
        );
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('배터리 최적화 설정을 열 수 없습니다: $e'), backgroundColor: Colors.red),
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
        title: const Text('설정', style: TextStyle(color: Colors.white, fontWeight: FontWeight.bold)),
        backgroundColor: _primary,
        elevation: 0,
        centerTitle: true,
      ),
      body: ListView(
        padding: const EdgeInsets.all(20),
        children: [
          // 계정 섹션
          _sectionTitle('👤 계정'),
          _card(
            child: Column(
              children: [
                _tile(
                  icon: Icons.person_rounded,
                  title: '동행복권 계정',
                  subtitle: isLoggedIn ? '로그인됨' : '로그인 필요',
                  trailing: TextButton(
                    onPressed: isLoggedIn ? _logout : _showLoginDialog,
                    child: Text(isLoggedIn ? '로그아웃' : '로그인', style: const TextStyle(color: _primary)),
                  ),
                ),
                const Divider(height: 1),
                _tile(
                  icon: Icons.account_balance_wallet_rounded,
                  title: '예치금 잔액',
                  subtitle: '₩${formatNumber(balance)}',
                  trailing: isLoggedIn ? IconButton(
                    icon: const Icon(Icons.refresh_rounded, color: _primary, size: 20),
                    onPressed: _refreshBalance,
                  ) : null,
                ),
              ],
            ),
          ),
          const SizedBox(height: 24),

          // 자동 구매 섹션
          _sectionTitle('⏰ 자동 구매'),
          _card(
            child: Column(
              children: [
                SwitchListTile(
                  title: const Text('자동 구매 활성화', style: TextStyle(fontWeight: FontWeight.w500)),
                  subtitle: !isLoggedIn ? const Text('로그인 후 사용 가능', style: TextStyle(color: Colors.red, fontSize: 12)) : null,
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
                    title: Text('$autoGames게임 설정됨', style: const TextStyle(fontWeight: FontWeight.w500)),
                    subtitle: Text(autoGames > 0 ? '번호 설정 탭에서 변경' : '번호 설정 탭에서 게임을 설정해주세요', style: const TextStyle(fontSize: 13)),
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
                    title: const Text('구매 요일', style: TextStyle(fontWeight: FontWeight.w500)),
                    trailing: DropdownButton<int>(
                      value: purchaseDay,
                      underline: const SizedBox(),
                      items: List.generate(7, (i) {
                        final d = i + 1;
                        return DropdownMenuItem(value: d, child: Text('${_dayNames[i]}요일'));
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
                    title: const Text('구매 시간', style: TextStyle(fontWeight: FontWeight.w500)),
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
                    title: const Text('배터리 최적화 제외', style: TextStyle(fontWeight: FontWeight.w500)),
                    subtitle: const Text('정시 실행을 위해 권장', style: TextStyle(fontSize: 12)),
                    trailing: const Icon(Icons.open_in_new_rounded, size: 18, color: Colors.grey),
                    onTap: _requestBatteryOptimization,
                  ),
                ],
              ],
            ),
          ),
          const SizedBox(height: 24),

          // 알림 섹션
          _sectionTitle('🔔 알림'),
          _card(
            child: Column(
              children: [
                SwitchListTile(
                  title: const Text('구매 완료 알림'),
                  subtitle: Text(formatPurchaseSchedule(purchaseDay, purchaseHour, purchaseMinute)),
                  value: _purchaseNoti,
                  activeThumbColor: _primary,
                  onChanged: (v) => setState(() => _purchaseNoti = v),
                ),
                const Divider(height: 1),
                SwitchListTile(
                  title: const Text('당첨 결과 알림'),
                  subtitle: const Text('매주 토요일 21:00'),
                  value: _resultNoti,
                  activeThumbColor: _primary,
                  onChanged: (v) => setState(() => _resultNoti = v),
                ),
              ],
            ),
          ),
          const SizedBox(height: 24),

          // 정보 섹션
          _sectionTitle('📱 앱 정보'),
          _card(
            child: Column(
              children: [
                _tile(icon: Icons.info_outline_rounded, title: '버전', subtitle: '1.0.0'),
                const Divider(height: 1),
                ListTile(
                  leading: Container(
                    padding: const EdgeInsets.all(8),
                    decoration: BoxDecoration(color: const Color(0xFFE8F0FE), borderRadius: BorderRadius.circular(10)),
                    child: const Icon(Icons.code_rounded, color: _primary, size: 22),
                  ),
                  title: const Text('오픈소스', style: TextStyle(fontWeight: FontWeight.w500)),
                  subtitle: const Text('github.com/free4416/umicorp-autolotto'),
                  trailing: const Icon(Icons.open_in_new_rounded, size: 16, color: Colors.grey),
                  onTap: () => launchUrl(Uri.parse('https://github.com/free4416/umicorp-autolotto'), mode: LaunchMode.externalApplication),
                ),
                const Divider(height: 1),
                ListTile(
                  leading: Icon(Icons.delete_outline_rounded, color: Colors.red[400]),
                  title: Text('데이터 초기화', style: TextStyle(color: Colors.red[400])),
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

  void _showResetDialog(BuildContext context) {
    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('데이터 초기화'),
        content: const Text('모든 설정과 구매 기록이 삭제됩니다.\n정말 초기화하시겠습니까?'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx), child: const Text('취소')),
          TextButton(
            onPressed: () async {
              Navigator.pop(ctx);
              await ref.read(secureStorageProvider).clearAll();
              ref.read(isLoggedInProvider.notifier).state = false;
              ref.read(balanceProvider.notifier).state = 0;
              ref.read(autoEnabledProvider.notifier).state = false;
            },
            child: Text('초기화', style: TextStyle(color: Colors.red[400])),
          ),
        ],
      ),
    );
  }

}
