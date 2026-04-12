import 'dart:convert';
import 'package:flutter_test/flutter_test.dart';
import 'package:autolotto/services/purchase_service.dart';

void main() {
  group('PurchaseService.getCurrentRound', () {
    test('1회차 기준일(2002-12-07) 이후 양수 회차 반환', () {
      final round = PurchaseService.getCurrentRound();
      // 2002년 12월 7일 이후 1000회차 이상
      expect(round, greaterThan(1000));
    });

    test('회차는 항상 양의 정수', () {
      final round = PurchaseService.getCurrentRound();
      expect(round, isPositive);
    });
  });

  group('PurchaseService.getDrawDates', () {
    test('추첨일은 토요일', () {
      final dates = PurchaseService.getDrawDates();
      expect(dates.drawDate.weekday, equals(DateTime.saturday));
    });

    test('지급기한은 추첨일로부터 365일 후', () {
      final dates = PurchaseService.getDrawDates();
      final diff = dates.payLimitDate.difference(dates.drawDate).inDays;
      expect(diff, equals(365));
    });

    test('추첨일은 오늘 이후(또는 오늘)', () {
      final dates = PurchaseService.getDrawDates();
      final today = DateTime(
        DateTime.now().year,
        DateTime.now().month,
        DateTime.now().day,
      );
      expect(
        dates.drawDate.isAfter(today) || dates.drawDate.isAtSameMomentAs(today),
        isTrue,
      );
    });
  });

  group('PurchaseService.buildParam', () {
    test('자동 1게임 파라미터 생성', () {
      final param = PurchaseService.buildParam(1, []);
      final decoded = jsonDecode(param) as List;
      expect(decoded.length, equals(1));
      expect(decoded[0]['genType'], equals('0'));
      expect(decoded[0]['alpabet'], equals('A'));
      expect(decoded[0]['arrGameChoiceNum'], isNull);
    });

    test('수동 1게임 파라미터 생성', () {
      final param = PurchaseService.buildParam(0, [
        [3, 1, 45, 22, 10, 7]
      ]);
      final decoded = jsonDecode(param) as List;
      expect(decoded.length, equals(1));
      expect(decoded[0]['genType'], equals('1'));
      expect(decoded[0]['alpabet'], equals('A'));
      // 정렬되어야 함
      expect(decoded[0]['arrGameChoiceNum'], equals('01,03,07,10,22,45'));
    });

    test('수동 2 + 자동 3 = 5게임 혼합', () {
      final param = PurchaseService.buildParam(3, [
        [1, 2, 3, 4, 5, 6],
        [7, 8, 9, 10, 11, 12],
      ]);
      final decoded = jsonDecode(param) as List;
      expect(decoded.length, equals(5));
      // 수동 먼저
      expect(decoded[0]['genType'], equals('1'));
      expect(decoded[0]['alpabet'], equals('A'));
      expect(decoded[1]['genType'], equals('1'));
      expect(decoded[1]['alpabet'], equals('B'));
      // 자동
      expect(decoded[2]['genType'], equals('0'));
      expect(decoded[2]['alpabet'], equals('C'));
      expect(decoded[3]['genType'], equals('0'));
      expect(decoded[3]['alpabet'], equals('D'));
      expect(decoded[4]['genType'], equals('0'));
      expect(decoded[4]['alpabet'], equals('E'));
    });

    test('5게임 초과 시 잘림', () {
      final param = PurchaseService.buildParam(6, []);
      final decoded = jsonDecode(param) as List;
      expect(decoded.length, equals(5));
    });

    test('수동 번호는 오름차순 정렬', () {
      final param = PurchaseService.buildParam(0, [
        [45, 1, 33, 7, 12, 28]
      ]);
      final decoded = jsonDecode(param) as List;
      expect(decoded[0]['arrGameChoiceNum'], equals('01,07,12,28,33,45'));
    });

    test('슬롯 이름은 A~E 순서', () {
      final param = PurchaseService.buildParam(5, []);
      final decoded = jsonDecode(param) as List;
      final slots = decoded.map((e) => e['alpabet']).toList();
      expect(slots, equals(['A', 'B', 'C', 'D', 'E']));
    });
  });

  group('PurchaseService.parseNumbersFromResponse', () {
    test('정상 응답에서 번호 추출', () {
      final response = ['A|01|02|04|27|39|443'];
      final numbers = PurchaseService.parseNumbersFromResponse(response);
      expect(numbers.length, equals(1));
      expect(numbers[0], equals([1, 2, 4, 27, 39, 44]));
    });

    test('복수 게임 번호 추출', () {
      final response = [
        'A|03|11|22|33|40|450',
        'B|01|05|15|25|35|451',
      ];
      final numbers = PurchaseService.parseNumbersFromResponse(response);
      expect(numbers.length, equals(2));
      expect(numbers[0], equals([3, 11, 22, 33, 40, 45]));
      expect(numbers[1], equals([1, 5, 15, 25, 35, 45]));
    });

    test('빈 리스트 처리', () {
      final numbers = PurchaseService.parseNumbersFromResponse([]);
      expect(numbers, isEmpty);
    });

    test('짧은 문자열 무시', () {
      final numbers = PurchaseService.parseNumbersFromResponse(['AB']);
      expect(numbers, isEmpty);
    });

    test('유효하지 않은 번호(1~45 범위 밖) 무시', () {
      // 번호가 46 이상이면 무시됨
      final response = ['A|01|02|03|04|05|991'];
      final numbers = PurchaseService.parseNumbersFromResponse(response);
      // 99는 범위 밖이므로 제외됨
      expect(numbers, isEmpty);
    });
  });

  group('PurchaseResult', () {
    test('totalGames 계산', () {
      final result = PurchaseResult(
        round: 1100,
        numbers: [[1, 2, 3, 4, 5, 6], [7, 8, 9, 10, 11, 12]],
        autoCount: 1,
        manualCount: 1,
        amount: 2000,
      );
      expect(result.totalGames, equals(2));
    });
  });
}
