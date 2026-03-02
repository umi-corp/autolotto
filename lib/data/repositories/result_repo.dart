import '../database.dart';
import '../../models/result.dart';

class ResultRepository {
  /// 당첨번호 저장
  Future<void> save(WinningNumbers result) async {
    await AppDatabase.resultBox.put(result.round.toString(), result);
  }

  /// 회차별 조회
  WinningNumbers? getByRound(int round) {
    return AppDatabase.resultBox.get(round.toString());
  }

  /// 최신 당첨번호
  WinningNumbers? getLatest() {
    if (AppDatabase.resultBox.isEmpty) return null;
    final list = AppDatabase.resultBox.values.toList();
    list.sort((a, b) => b.round.compareTo(a.round));
    return list.first;
  }
}
