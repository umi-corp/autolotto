import 'dart:convert';
import 'package:android_alarm_manager_plus/android_alarm_manager_plus.dart';
import 'package:flutter_local_notifications/flutter_local_notifications.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'auth_service.dart';
import 'purchase_service.dart';
import 'result_service.dart';

const _alarmIdPurchase = 1001;
const _alarmIdCheckResult = 1002;

class SchedulerService {
  static final FlutterLocalNotificationsPlugin _notifications =
      FlutterLocalNotificationsPlugin();

  static Future<void> initialize() async {
    await AndroidAlarmManager.initialize();

    const androidSettings = AndroidInitializationSettings('@mipmap/ic_launcher');
    const initSettings = InitializationSettings(android: androidSettings);
    await _notifications.initialize(initSettings);
  }

  /// 자동 구매 알람 등록 (one-shot exact)
  static Future<void> scheduleAutoPurchase({
    int weekday = DateTime.sunday,
    int hour = 9,
    int minute = 0,
  }) async {
    final target = _nextDateTime(weekday, hour, minute);
    await AndroidAlarmManager.cancel(_alarmIdPurchase);
    await AndroidAlarmManager.oneShotAt(
      target,
      _alarmIdPurchase,
      _onAutoPurchaseAlarm,
      exact: true,
      wakeup: true,
      rescheduleOnReboot: true,
    );
  }

  /// 당첨확인 알람 등록 (one-shot exact, 매주 토요일 21:00 고정)
  static Future<void> scheduleCheckResult() async {
    final target = _nextDateTime(DateTime.saturday, 21, 0);
    await AndroidAlarmManager.cancel(_alarmIdCheckResult);
    await AndroidAlarmManager.oneShotAt(
      target,
      _alarmIdCheckResult,
      _onCheckResultAlarm,
      exact: true,
      wakeup: true,
      rescheduleOnReboot: true,
    );
  }

  static Future<void> cancelAll() async {
    await AndroidAlarmManager.cancel(_alarmIdPurchase);
    await AndroidAlarmManager.cancel(_alarmIdCheckResult);
  }

  static Future<void> showNotification({
    required String title,
    required String body,
    int id = 0,
  }) async {
    const androidDetails = AndroidNotificationDetails(
      'autolotto_channel',
      'AutoLotto 알림',
      channelDescription: '로또 구매/당첨 알림',
      importance: Importance.high,
      priority: Priority.high,
    );
    const details = NotificationDetails(android: androidDetails);
    await _notifications.show(id, title, body, details);
  }

  /// 다음 실행 시각 계산 (Dart weekday: 1=월 ~ 7=일)
  static DateTime _nextDateTime(int weekday, int hour, int minute) {
    final now = DateTime.now();
    var target = DateTime(now.year, now.month, now.day, hour, minute);
    var daysUntil = (weekday - now.weekday) % 7;
    if (daysUntil == 0 && now.isAfter(target)) {
      daysUntil = 7;
    }
    target = target.add(Duration(days: daysUntil));
    return target;
  }
}

// ===== AlarmManager Callbacks (top-level functions) =====

@pragma('vm:entry-point')
Future<void> _onAutoPurchaseAlarm() async {
  try {
    await _executeAutoPurchase();
  } catch (e) {
    final msg = e.toString().replaceAll(RegExp(r"(Exception: |\[\w+\] )"), "");
    await SchedulerService.showNotification(
      title: "⚠️ AutoLotto 오류",
      body: msg,
      id: 99,
    );
  }

  // 다음 주 알람 재등록 (one-shot 체인)
  try {
    const storage = FlutterSecureStorage(
      aOptions: AndroidOptions(encryptedSharedPreferences: true),
    );
    final autoEnabled = await storage.read(key: 'auto_purchase_enabled');
    if (autoEnabled == 'true') {
      final dayStr = await storage.read(key: 'auto_purchase_day');
      final hourStr = await storage.read(key: 'auto_purchase_hour');
      final minuteStr = await storage.read(key: 'auto_purchase_minute');
      final day = int.tryParse(dayStr ?? '') ?? 7;
      final hour = int.tryParse(hourStr ?? '') ?? 9;
      final minute = int.tryParse(minuteStr ?? '') ?? 0;
      await SchedulerService.scheduleAutoPurchase(
        weekday: day,
        hour: hour,
        minute: minute,
      );
    }
  } catch (_) {}
}

@pragma('vm:entry-point')
Future<void> _onCheckResultAlarm() async {
  try {
    await _executeCheckResult();
  } catch (e) {
    await SchedulerService.showNotification(
      title: '⚠️ AutoLotto 오류',
      body: '$e',
      id: 99,
    );
  }

  // 다음 주 알람 재등록
  try {
    const storage = FlutterSecureStorage(
      aOptions: AndroidOptions(encryptedSharedPreferences: true),
    );
    final autoEnabled = await storage.read(key: 'auto_purchase_enabled');
    if (autoEnabled == 'true') {
      await SchedulerService.scheduleCheckResult();
    }
  } catch (_) {}
}

Future<void> _executeAutoPurchase() async {
  const storage = FlutterSecureStorage(
    aOptions: AndroidOptions(encryptedSharedPreferences: true),
  );

  String step = 'init';
  try {
    step = 'read_credentials';
    final userId = await storage.read(key: 'dhlottery_user_id');
    final password = await storage.read(key: 'dhlottery_password');
    final autoEnabled = await storage.read(key: 'auto_purchase_enabled');
    final gamesStr = await storage.read(key: 'auto_games');

    if (autoEnabled != 'true' || userId == null || password == null) return;

    final games = int.tryParse(gamesStr ?? '') ?? 0;
    if (games == 0) return;

    step = 'parse_numbers';
    final manualJson = await storage.read(key: 'manual_numbers') ?? '[]';
    List<List<int>> manualNumbers = [];
    int autoGames = 0;

    try {
      final parsed = jsonDecode(manualJson);
      if (parsed is List) {
        for (final g in parsed) {
          if (g is List && g.isNotEmpty) {
            final nums = <int>[];
            for (final e in g) {
              if (e is int) {
                nums.add(e);
              } else if (e is num) {
                nums.add(e.toInt());
              } else {
                nums.add(int.parse(e.toString()));
              }
            }
            manualNumbers.add(nums);
          } else if (g is List && g.isEmpty) {
            autoGames++;
          } else if (g == null) {
            // 미설정 슬롯 — 스킵
          }
        }
      }
    } catch (e) {
      autoGames = games;
    }

    if (autoGames == 0 && manualNumbers.isEmpty) return;

    step = 'login';
    final auth = AuthService();
    await auth.login(userId, password);

    step = 'purchase';
    final purchaseService = PurchaseService(auth);
    try {
      final result = await purchaseService.purchase(
        autoGames: autoGames,
        manualNumbers: manualNumbers,
      );

      step = 'notify';
      final numbersText = result.numbers
          .asMap()
          .entries
          .map((e) => '${String.fromCharCode(65 + e.key)}: ${e.value.join(",")}')
          .join('\n');

      await SchedulerService.showNotification(
        title: '🎰 로또 자동 구매 완료!',
        body: '제 ${result.round}회 · ${result.totalGames}게임\n$numbersText',
        id: 1,
      );
    } catch (purchaseError) {
      throw Exception('$purchaseError'.replaceAll(RegExp(r'Exception: '), ''));
    }
  } catch (e) {
    throw Exception('[$step] $e'.replaceAll(RegExp(r'Exception: '), ''));
  }
}


Future<void> _executeCheckResult() async {
  const storage = FlutterSecureStorage(
    aOptions: AndroidOptions(encryptedSharedPreferences: true),
  );

  final autoEnabled = await storage.read(key: 'auto_purchase_enabled');
  if (autoEnabled != 'true') return;

  // 당첨번호 조회
  final resultService = ResultService();
  final winning = await resultService.getWinningNumbers();
  if (winning == null) return;

  // TODO: DB에서 해당 회차 구매기록 로드 → 매칭
  // 백그라운드에서 Hive 접근은 별도 초기화 필요 (Phase 2에서 개선)

  await SchedulerService.showNotification(
    title: '🎱 제 ${winning.round}회 당첨번호',
    body: '${winning.numbers.join(", ")} + ${winning.bonus}',
    id: 2,
  );
}
