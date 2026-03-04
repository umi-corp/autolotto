import 'package:flutter/material.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../providers/providers.dart';
import '../services/balance_alert_service.dart';
import '../services/scheduler_service.dart';
import '../app.dart';

/// 앱 시작 시 스플래시 화면
/// 초기화 (설정 로드 + 자동 로그인) 후 메인 화면으로 전환
class SplashScreen extends ConsumerStatefulWidget {
  const SplashScreen({super.key});

  @override
  ConsumerState<SplashScreen> createState() => _SplashScreenState();
}

class _SplashScreenState extends ConsumerState<SplashScreen>
    with SingleTickerProviderStateMixin {
  late AnimationController _fadeController;
  late Animation<double> _fadeAnimation;

  @override
  void initState() {
    super.initState();
    _fadeController = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 800),
    );
    _fadeAnimation = CurvedAnimation(
      parent: _fadeController,
      curve: Curves.easeIn,
    );
    _fadeController.forward();
    _initialize();
  }

  @override
  void dispose() {
    _fadeController.dispose();
    super.dispose();
  }

  Future<void> _initialize() async {
    final stopwatch = Stopwatch()..start();

    try {
      if (!kIsWeb) {
        await SchedulerService.initialize();
      }

      final storage = ref.read(secureStorageProvider);
      final autoEnabled = await storage.getAutoEnabled();
      final autoGames = await storage.getAutoGames();
      final purchaseDay = await storage.getAutoPurchaseDay();
      final purchaseHour = await storage.getAutoPurchaseHour();
      final purchaseMinute = await storage.getAutoPurchaseMinute();

      ref.read(autoEnabledProvider.notifier).state = autoEnabled;
      ref.read(autoGamesProvider.notifier).state = autoGames;
      ref.read(autoPurchaseDayProvider.notifier).state = purchaseDay;
      ref.read(autoPurchaseHourProvider.notifier).state = purchaseHour;
      ref.read(autoPurchaseMinuteProvider.notifier).state = purchaseMinute;

      // 잔액 알림 설정 로드
      final balanceAlertEnabled = await storage.getBalanceAlertEnabled();
      final balanceAlertThreshold = await storage.getBalanceAlertThreshold();
      ref.read(balanceAlertEnabledProvider.notifier).state = balanceAlertEnabled;
      ref.read(balanceAlertThresholdProvider.notifier).state = balanceAlertThreshold;

      // 저장된 언어 설정 로드
      final savedLang = await storage.getLanguage();
      if (savedLang != 'system') {
        ref.read(appLocaleProvider.notifier).state = Locale(savedLang);
      }

      // 자동 로그인
      if (!kIsWeb) {
        final hasCred = await storage.hasCredentials();
        if (hasCred) {
          try {
            final cred = await storage.getCredentials();
            if (cred.userId != null && cred.password != null) {
              final auth = ref.read(authServiceProvider);
              await auth.login(cred.userId!, cred.password!);
              ref.read(isLoggedInProvider.notifier).state = true;
              final balance = await auth.getBalance();
              ref.read(balanceProvider.notifier).state = balance;
              // 잔액 부족 알림 체크
              await BalanceAlertService.checkAndNotify(
                balance: balance,
                enabled: balanceAlertEnabled,
                threshold: balanceAlertThreshold,
              );
            }
          } catch (e) {
            debugPrint('자동 로그인 실패: $e');
          }
        }
      }
    } catch (e) {
      debugPrint('초기화 실패: $e');
    }

    // 최소 1.5초 표시
    stopwatch.stop();
    final elapsed = stopwatch.elapsedMilliseconds;
    if (elapsed < 1500) {
      await Future.delayed(Duration(milliseconds: 1500 - elapsed));
    }

    if (mounted) {
      Navigator.of(context).pushReplacement(
        PageRouteBuilder(
          pageBuilder: (_, a, b) => const AppShell(),
          transitionsBuilder: (_, animation, a, child) {
            return FadeTransition(opacity: animation, child: child);
          },
          transitionDuration: const Duration(milliseconds: 400),
        ),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: Container(
        width: double.infinity,
        height: double.infinity,
        decoration: const BoxDecoration(
          gradient: LinearGradient(
            begin: Alignment.topCenter,
            end: Alignment.bottomCenter,
            colors: [Color(0xFF15152D), Color(0xFF1E2D50)],
          ),
        ),
        child: FadeTransition(
          opacity: _fadeAnimation,
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              const Spacer(flex: 3),
              // 앱 아이콘
              Container(
                width: 120,
                height: 120,
                decoration: BoxDecoration(
                  borderRadius: BorderRadius.circular(28),
                  boxShadow: [
                    BoxShadow(
                      color: Colors.black.withValues(alpha: 0.3),
                      blurRadius: 20,
                      offset: const Offset(0, 8),
                    ),
                  ],
                ),
                child: ClipRRect(
                  borderRadius: BorderRadius.circular(28),
                  child: Image.asset('assets/autolotto_icon.png'),
                ),
              ),
              const SizedBox(height: 24),
              // 앱 이름
              const Text(
                'AutoLotto',
                style: TextStyle(
                  color: Colors.white,
                  fontSize: 32,
                  fontWeight: FontWeight.bold,
                  letterSpacing: 1.2,
                ),
              ),
              const SizedBox(height: 8),
              Text(
                'Lotto 6/45',
                style: TextStyle(
                  color: Colors.white.withValues(alpha: 0.7),
                  fontSize: 16,
                  fontWeight: FontWeight.w300,
                ),
              ),
              const Spacer(flex: 2),
              // 로딩 인디케이터
              SizedBox(
                width: 28,
                height: 28,
                child: CircularProgressIndicator(
                  strokeWidth: 2.5,
                  valueColor: AlwaysStoppedAnimation<Color>(
                    Colors.white.withValues(alpha: 0.8),
                  ),
                ),
              ),
              const SizedBox(height: 48),
            ],
          ),
        ),
      ),
    );
  }
}
