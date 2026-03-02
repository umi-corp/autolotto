import 'package:flutter/material.dart';
import 'package:flutter/foundation.dart' show kIsWeb;
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'app.dart';
import 'data/database.dart';
import 'services/scheduler_service.dart';
import 'providers/providers.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await AppDatabase.initialize();

  if (!kIsWeb) {
    await SchedulerService.initialize();
  }

  final container = ProviderContainer();

  // 저장된 설정 로드
  try {
    final storage = container.read(secureStorageProvider);
    final autoEnabled = await storage.getAutoEnabled();
    final autoGames = await storage.getAutoGames();
    final purchaseDay = await storage.getAutoPurchaseDay();
    final purchaseHour = await storage.getAutoPurchaseHour();
    final purchaseMinute = await storage.getAutoPurchaseMinute();

    container.read(autoEnabledProvider.notifier).state = autoEnabled;
    container.read(autoGamesProvider.notifier).state = autoGames;
    container.read(autoPurchaseDayProvider.notifier).state = purchaseDay;
    container.read(autoPurchaseHourProvider.notifier).state = purchaseHour;
    container.read(autoPurchaseMinuteProvider.notifier).state = purchaseMinute;

    // 저장된 계정이 있으면 자동 로그인 시도
    if (!kIsWeb) {
      final hasCred = await storage.hasCredentials();
      if (hasCred) {
        try {
          final cred = await storage.getCredentials();
          final auth = container.read(authServiceProvider);
          await auth.login(cred.userId!, cred.password!);
          container.read(isLoggedInProvider.notifier).state = true;

          // 잔액 조회
          container.read(balanceProvider.notifier).state = await auth.getBalance();
        } catch (_) {}
      }
    }
  } catch (_) {}

  runApp(UncontrolledProviderScope(
    container: container,
    child: const AutoLottoApp(),
  ));
}

class AutoLottoApp extends StatelessWidget {
  const AutoLottoApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      debugShowCheckedModeBanner: false,
      title: 'AutoLotto',
      theme: ThemeData(
        colorSchemeSeed: const Color(0xFF2D5BFF),
        useMaterial3: true,
        listTileTheme: const ListTileThemeData(
          titleTextStyle: TextStyle(
            fontSize: 15,
            fontWeight: FontWeight.w500,
            color: Colors.black87,
          ),
          subtitleTextStyle: TextStyle(
            fontSize: 13,
            color: Colors.black54,
          ),
        ),
      ),
      home: const AppShell(),
    );
  }
}
