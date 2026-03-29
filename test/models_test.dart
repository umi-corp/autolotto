import 'package:flutter_test/flutter_test.dart';
import 'package:autolotto/models/purchase.dart';
import 'package:autolotto/models/result.dart';

void main() {
  group('Purchase', () {
    test('기본 생성', () {
      final p = Purchase(
        round: 1218,
        date: DateTime(2026, 3, 29),
        numbers: [
          [1, 2, 3, 4, 5, 6],
          [7, 8, 9, 10, 11, 12],
        ],
        autoCount: 1,
        manualCount: 1,
        amount: 2000,
      );

      expect(p.round, 1218);
      expect(p.numbers.length, 2);
      expect(p.totalGames, 2);
      expect(p.amount, 2000);
      expect(p.checked, false);
      expect(p.rank, isNull);
      expect(p.prize, 0);
    });

    test('totalGames는 autoCount + manualCount', () {
      final p = Purchase(
        round: 1218,
        date: DateTime(2026, 3, 29),
        numbers: [
          [1, 2, 3, 4, 5, 6],
          [7, 8, 9, 10, 11, 12],
          [13, 14, 15, 16, 17, 18],
        ],
        autoCount: 2,
        manualCount: 1,
        amount: 3000,
      );

      expect(p.totalGames, 3);
    });

    test('당첨 정보 설정', () {
      final p = Purchase(
        round: 1218,
        date: DateTime(2026, 3, 29),
        numbers: [
          [1, 2, 3, 4, 5, 6],
        ],
        autoCount: 1,
        manualCount: 0,
        amount: 1000,
        checked: true,
        rank: 'rank5',
        prize: 5000,
        gameRanks: ['rank5'],
        gamePrizes: [5000],
      );

      expect(p.checked, true);
      expect(p.rank, 'rank5');
      expect(p.prize, 5000);
      expect(p.gameRanks, ['rank5']);
      expect(p.gamePrizes, [5000]);
    });
  });

  group('WinningNumbers', () {
    test('기본 생성', () {
      final w = WinningNumbers(
        round: 1218,
        numbers: [3, 12, 18, 27, 35, 42],
        bonus: 7,
        date: '2026-03-29',
      );

      expect(w.round, 1218);
      expect(w.numbers.length, 6);
      expect(w.bonus, 7);
      expect(w.date, '2026-03-29');
      expect(w.prize1st, 0);
      expect(w.prize2nd, 0);
      expect(w.prize3rd, 0);
    });

    test('당첨금 포함 생성', () {
      final w = WinningNumbers(
        round: 1218,
        numbers: [3, 12, 18, 27, 35, 42],
        bonus: 7,
        date: '2026-03-29',
        prize1st: 2000000000,
        prize2nd: 50000000,
        prize3rd: 1500000,
      );

      expect(w.prize1st, 2000000000);
      expect(w.prize2nd, 50000000);
      expect(w.prize3rd, 1500000);
    });
  });
}
