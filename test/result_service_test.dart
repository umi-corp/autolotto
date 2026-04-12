import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http_mock_adapter/http_mock_adapter.dart';
import 'package:autolotto/services/result_service.dart';
import 'package:autolotto/utils/constants.dart';

void main() {
  group('ResultService.checkMatches', () {
    const winningNumbers = [3, 11, 22, 33, 40, 45];
    const bonus = 7;

    test('1등 — 6개 일치', () {
      final results = ResultService.checkMatches(
        [[3, 11, 22, 33, 40, 45]],
        winningNumbers,
        bonus,
      );
      expect(results.length, equals(1));
      expect(results[0].rank, equals('rank1'));
      expect(results[0].matchCount, equals(6));
      expect(results[0].isWinner, isTrue);
    });

    test('2등 — 5개 + 보너스 일치', () {
      final results = ResultService.checkMatches(
        [[3, 7, 22, 33, 40, 45]], // 11 대신 7(보너스)
        winningNumbers,
        bonus,
      );
      expect(results[0].rank, equals('rank2'));
      expect(results[0].matchCount, equals(5));
      expect(results[0].bonusMatch, isTrue);
      expect(results[0].isWinner, isTrue);
    });

    test('3등 — 5개 일치, 보너스 불일치', () {
      final results = ResultService.checkMatches(
        [[3, 11, 22, 33, 40, 1]], // 45 대신 1
        winningNumbers,
        bonus,
      );
      expect(results[0].rank, equals('rank3'));
      expect(results[0].matchCount, equals(5));
      expect(results[0].bonusMatch, isFalse);
    });

    test('4등 — 4개 일치', () {
      final results = ResultService.checkMatches(
        [[3, 11, 22, 33, 1, 2]],
        winningNumbers,
        bonus,
      );
      expect(results[0].rank, equals('rank4'));
      expect(results[0].matchCount, equals(4));
      expect(results[0].prize, equals(50000));
    });

    test('5등 — 3개 일치', () {
      final results = ResultService.checkMatches(
        [[3, 11, 22, 1, 2, 4]],
        winningNumbers,
        bonus,
      );
      expect(results[0].rank, equals('rank5'));
      expect(results[0].matchCount, equals(3));
      expect(results[0].prize, equals(5000));
    });

    test('낙첨 — 2개 이하 일치', () {
      final results = ResultService.checkMatches(
        [[3, 11, 1, 2, 4, 5]],
        winningNumbers,
        bonus,
      );
      expect(results[0].rank, equals('nowin'));
      expect(results[0].matchCount, equals(2));
      expect(results[0].prize, equals(0));
      expect(results[0].isWinner, isFalse);
    });

    test('0개 일치 — 낙첨', () {
      final results = ResultService.checkMatches(
        [[1, 2, 4, 5, 6, 8]],
        winningNumbers,
        bonus,
      );
      expect(results[0].rank, equals('nowin'));
      expect(results[0].matchCount, equals(0));
    });

    test('복수 게임 매칭', () {
      final results = ResultService.checkMatches(
        [
          [3, 11, 22, 33, 40, 45], // 1등
          [3, 11, 22, 1, 2, 4],    // 5등
          [1, 2, 4, 5, 6, 8],      // 낙첨
        ],
        winningNumbers,
        bonus,
      );
      expect(results.length, equals(3));
      expect(results[0].rank, equals('rank1'));
      expect(results[1].rank, equals('rank5'));
      expect(results[2].rank, equals('nowin'));
    });

    test('matched 리스트는 정렬됨', () {
      final results = ResultService.checkMatches(
        [[45, 40, 33, 22, 11, 3]],
        winningNumbers,
        bonus,
      );
      expect(results[0].matched, equals([3, 11, 22, 33, 40, 45]));
    });

    test('4개 일치 + 보너스는 4등 (보너스는 5개 일치에서만 의미)', () {
      final results = ResultService.checkMatches(
        [[3, 7, 22, 33, 40, 1]], // 4개 + 보너스
        winningNumbers,
        bonus,
      );
      expect(results[0].rank, equals('rank4'));
      expect(results[0].matchCount, equals(4));
      expect(results[0].bonusMatch, isTrue);
    });
  });

  group('ResultService.getWinningNumbers', () {
    late Dio dio;
    late DioAdapter dioAdapter;
    late ResultService service;

    setUp(() {
      dio = Dio(BaseOptions(
        headers: ApiConstants.defaultHeaders,
      ));
      dioAdapter = DioAdapter(dio: dio);
      service = ResultService(dio);
    });

    test('정상 응답 시 WinningResult 반환', () async {
      // 메인 페이지 방문 mock
      dioAdapter.onGet(
        ApiConstants.baseUrl,
        (server) => server.reply(200, ''),
      );

      dioAdapter.onGet(
        ApiConstants.winningNumberUrl,
        (server) => server.reply(200, {
          'data': {
            'list': [
              {
                'ltEpsd': 1100,
                'tm1WnNo': 3,
                'tm2WnNo': 11,
                'tm3WnNo': 22,
                'tm4WnNo': 33,
                'tm5WnNo': 40,
                'tm6WnNo': 45,
                'bnsWnNo': 7,
                'ltRflYmd': '20240101',
                'rnk1WnAmt': 2000000000,
                'rnk2WnAmt': 50000000,
                'rnk3WnAmt': 1500000,
              }
            ]
          }
        }),
        queryParameters: {'drwNo': '1100'},
      );

      final result = await service.getWinningNumbers(roundNo: 1100);
      expect(result, isNotNull);
      expect(result!.round, equals(1100));
      expect(result.numbers, equals([3, 11, 22, 33, 40, 45]));
      expect(result.bonus, equals(7));
      expect(result.date, equals('2024-01-01'));
      expect(result.prize1st, equals(2000000000));
    });

    test('빈 list 응답 시 null 반환', () async {
      dioAdapter.onGet(
        ApiConstants.baseUrl,
        (server) => server.reply(200, ''),
      );

      dioAdapter.onGet(
        ApiConstants.winningNumberUrl,
        (server) => server.reply(200, {
          'data': {'list': []}
        }),
      );

      final result = await service.getWinningNumbers();
      expect(result, isNull);
    });

    test('네트워크 에러 시 null 반환', () async {
      dioAdapter.onGet(
        ApiConstants.baseUrl,
        (server) => server.throws(
          0,
          DioException(requestOptions: RequestOptions(path: '')),
        ),
      );

      final result = await service.getWinningNumbers();
      expect(result, isNull);
    });

    test('번호는 정렬되어 반환', () async {
      dioAdapter.onGet(
        ApiConstants.baseUrl,
        (server) => server.reply(200, ''),
      );

      dioAdapter.onGet(
        ApiConstants.winningNumberUrl,
        (server) => server.reply(200, {
          'data': {
            'list': [
              {
                'ltEpsd': 1100,
                'tm1WnNo': 45,
                'tm2WnNo': 3,
                'tm3WnNo': 33,
                'tm4WnNo': 11,
                'tm5WnNo': 40,
                'tm6WnNo': 22,
                'bnsWnNo': 7,
                'ltRflYmd': '20240101',
              }
            ]
          }
        }),
      );

      final result = await service.getWinningNumbers();
      expect(result!.numbers, equals([3, 11, 22, 33, 40, 45]));
    });
  });

  group('WinningResult', () {
    test('기본 생성', () {
      final result = WinningResult(
        round: 1100,
        numbers: [1, 2, 3, 4, 5, 6],
        bonus: 7,
        date: '2024-01-01',
      );
      expect(result.prize1st, equals(0));
      expect(result.prize2nd, equals(0));
      expect(result.prize3rd, equals(0));
    });
  });

  group('MatchResult', () {
    test('isWinner — rank1~rank5는 true', () {
      for (final rank in ['rank1', 'rank2', 'rank3', 'rank4', 'rank5']) {
        final result = MatchResult(
          numbers: [1, 2, 3, 4, 5, 6],
          matched: [1, 2, 3],
          matchCount: 3,
          bonusMatch: false,
          rank: rank,
          prize: 0,
        );
        expect(result.isWinner, isTrue, reason: '$rank should be winner');
      }
    });

    test('isWinner — nowin은 false', () {
      final result = MatchResult(
        numbers: [1, 2, 3, 4, 5, 6],
        matched: [],
        matchCount: 0,
        bonusMatch: false,
        rank: 'nowin',
        prize: 0,
      );
      expect(result.isWinner, isFalse);
    });
  });
}
