import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../services/auth_service.dart';
import '../services/purchase_service.dart';
import '../services/result_service.dart';
import '../services/history_service.dart';
import '../services/secure_storage.dart';
import '../data/repositories/purchase_repo.dart';
import '../data/repositories/result_repo.dart';

// === 서비스 ===
final authServiceProvider = Provider<AuthService>((ref) => AuthService());
final purchaseServiceProvider = Provider<PurchaseService>((ref) {
  return PurchaseService(ref.read(authServiceProvider));
});
final historyServiceProvider = Provider<HistoryService>((ref) {
  return HistoryService(ref.read(authServiceProvider));
});

final resultServiceProvider = Provider<ResultService>((ref) => ResultService());
final secureStorageProvider = Provider<SecureStorageService>((ref) => SecureStorageService());

// === 리포지토리 ===
final purchaseRepoProvider = Provider<PurchaseRepository>((ref) => PurchaseRepository());
final resultRepoProvider = Provider<ResultRepository>((ref) => ResultRepository());

// === 상태 ===

/// 로그인 상태
final isLoggedInProvider = StateProvider<bool>((ref) => false);

/// 잔액
final balanceProvider = StateProvider<int>((ref) => 0);

/// 구매 기록 목록
final purchaseListProvider = StateProvider<List>((ref) {
  return ref.read(purchaseRepoProvider).getAll();
});

/// 자동 구매 활성화
final autoEnabledProvider = StateProvider<bool>((ref) => false);

/// 자동 게임 수
final autoGamesProvider = StateProvider<int>((ref) => 0);

/// 자동 구매 요일 (1=월 ~ 7=일)
final autoPurchaseDayProvider = StateProvider<int>((ref) => 7);

/// 자동 구매 시간
final autoPurchaseHourProvider = StateProvider<int>((ref) => 9);

/// 자동 구매 분
final autoPurchaseMinuteProvider = StateProvider<int>((ref) => 0);

/// 현재 회차
final currentRoundProvider = Provider<int>((ref) {
  return PurchaseService.getCurrentRound();
});

