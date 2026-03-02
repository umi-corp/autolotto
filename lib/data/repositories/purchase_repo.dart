import 'package:hive/hive.dart';
import '../database.dart';
import '../../models/purchase.dart';

class PurchaseRepository {
  Box get _box => AppDatabase.purchaseBox;

  /// 구매 기록 저장
  Future<void> save(Purchase purchase) async {
    await _box.put(purchase.round.toString(), purchase);
  }

  /// 회차별 조회
  Purchase? getByRound(int round) {
    return AppDatabase.purchaseBox.get(round.toString());
  }

  /// 전체 조회 (최신순)
  List<Purchase> getAll() {
    final list = AppDatabase.purchaseBox.values.toList();
    list.sort((a, b) => b.round.compareTo(a.round));
    return list;
  }

  /// 미확인 구매 목록
  List<Purchase> getUnchecked() {
    return AppDatabase.purchaseBox.values.where((p) => !p.checked).toList();
  }

  /// 당첨 결과 업데이트
  Future<void> updateResult(int round, String rank, int prize) async {
    final purchase = getByRound(round);
    if (purchase != null) {
      purchase.checked = true;
      purchase.rank = rank;
      purchase.prize = prize;
      await purchase.save();
    }
  }
}
