import 'package:flutter/foundation.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'scheduler_service.dart';

/// 잔액 부족 체크 및 알림 발송
/// 앱 내(Riverpod) / 백그라운드(FlutterSecureStorage) 양쪽에서 사용
class BalanceAlertService {
  static const _notifId = 50;

  static const _storage = FlutterSecureStorage(
    aOptions: AndroidOptions(encryptedSharedPreferences: true),
  );

  /// 잔액을 체크하고, 임계값 이하면 알림을 보낸다.
  /// 같은 날 중복 알림은 방지한다.
  /// [balance]: 현재 잔액
  /// [threshold]: 알림 임계값 (null이면 storage에서 읽음)
  /// [enabled]: 알림 활성화 여부 (null이면 storage에서 읽음)
  static Future<void> checkAndNotify({
    required int balance,
    bool? enabled,
    int? threshold,
  }) async {
    try {
      final isEnabled = enabled ?? (await _storage.read(key: 'balance_alert_enabled')) == 'true';
      if (!isEnabled) return;

      final thresh = threshold ?? int.tryParse(await _storage.read(key: 'balance_alert_threshold') ?? '') ?? 5000;
      if (balance > thresh) return;

      // 같은 날 중복 알림 방지
      final today = DateTime.now().toIso8601String().substring(0, 10);
      final lastDate = await _storage.read(key: 'balance_alert_last_date');
      if (lastDate == today) return;

      await _storage.write(key: 'balance_alert_last_date', value: today);

      final formatted = _formatNumber(balance);
      await SchedulerService.showNotification(
        title: '💰 잔액 부족',
        body: '예치금 잔액이 $formatted원입니다. 충전이 필요합니다.',
        id: _notifId,
      );
    } catch (e) {
      debugPrint('잔액 알림 체크 오류: $e');
    }
  }

  static String _formatNumber(int n) {
    final str = n.toString();
    final buf = StringBuffer();
    for (var i = 0; i < str.length; i++) {
      if (i > 0 && (str.length - i) % 3 == 0) buf.write(',');
      buf.write(str[i]);
    }
    return buf.toString();
  }
}
