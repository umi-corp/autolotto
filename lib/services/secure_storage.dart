import 'package:flutter_secure_storage/flutter_secure_storage.dart';

/// 보안 저장소 (ID/PW 암호화 저장)
class SecureStorageService {
  static const _storage = FlutterSecureStorage(
    aOptions: AndroidOptions(encryptedSharedPreferences: true),
  );

  static const _keyUserId = 'dhlottery_user_id';
  static const _keyPassword = 'dhlottery_password';
  static const _keyAutoEnabled = 'auto_purchase_enabled';
  static const _keyAutoGames = 'auto_games';
  static const _keyManualNumbers = 'manual_numbers';
  static const _keyAutoPurchaseDay = 'auto_purchase_day';
  static const _keyAutoPurchaseHour = 'auto_purchase_hour';
  static const _keyAutoPurchaseMinute = 'auto_purchase_minute';

  // === 계정 ===

  Future<void> saveCredentials(String userId, String password) async {
    await _storage.write(key: _keyUserId, value: userId);
    await _storage.write(key: _keyPassword, value: password);
  }

  Future<({String? userId, String? password})> getCredentials() async {
    final userId = await _storage.read(key: _keyUserId);
    final password = await _storage.read(key: _keyPassword);
    return (userId: userId, password: password);
  }

  Future<bool> hasCredentials() async {
    final cred = await getCredentials();
    return cred.userId != null && cred.password != null;
  }

  Future<void> deleteCredentials() async {
    await _storage.delete(key: _keyUserId);
    await _storage.delete(key: _keyPassword);
  }

  // === 자동 구매 설정 ===

  Future<void> setAutoEnabled(bool enabled) async {
    await _storage.write(key: _keyAutoEnabled, value: enabled.toString());
  }

  Future<bool> getAutoEnabled() async {
    final val = await _storage.read(key: _keyAutoEnabled);
    return val == 'true';
  }

  Future<void> setAutoGames(int games) async {
    await _storage.write(key: _keyAutoGames, value: games.toString());
  }

  Future<int> getAutoGames() async {
    final val = await _storage.read(key: _keyAutoGames);
    return int.tryParse(val ?? '') ?? 0;
  }

  /// 수동 번호 저장 (JSON 문자열)
  Future<void> setManualNumbers(String json) async {
    await _storage.write(key: _keyManualNumbers, value: json);
  }

  Future<String> getManualNumbers() async {
    return await _storage.read(key: _keyManualNumbers) ?? '[]';
  }

  // === 구매 시간 설정 ===

  /// 구매 요일 (1=월, 7=일, 기본: 7)
  Future<void> setAutoPurchaseDay(int day) async {
    await _storage.write(key: _keyAutoPurchaseDay, value: day.toString());
  }

  Future<int> getAutoPurchaseDay() async {
    final val = await _storage.read(key: _keyAutoPurchaseDay);
    return int.tryParse(val ?? '') ?? 7; // 기본: 일요일
  }

  Future<void> setAutoPurchaseHour(int hour) async {
    await _storage.write(key: _keyAutoPurchaseHour, value: hour.toString());
  }

  Future<int> getAutoPurchaseHour() async {
    final val = await _storage.read(key: _keyAutoPurchaseHour);
    return int.tryParse(val ?? '') ?? 9; // 기본: 09시
  }

  Future<void> setAutoPurchaseMinute(int minute) async {
    await _storage.write(key: _keyAutoPurchaseMinute, value: minute.toString());
  }

  Future<int> getAutoPurchaseMinute() async {
    final val = await _storage.read(key: _keyAutoPurchaseMinute);
    return int.tryParse(val ?? '') ?? 0; // 기본: 00분
  }

  // === 전체 초기화 ===

  Future<void> clearAll() async {
    await _storage.deleteAll();
  }
}
