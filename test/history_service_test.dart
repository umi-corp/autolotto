import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http_mock_adapter/http_mock_adapter.dart';
import 'package:autolotto/services/auth_service.dart';
import 'package:autolotto/services/history_service.dart';

void main() {
  late AuthService auth;
  late DioAdapter dioAdapter;
  late HistoryService historyService;

  setUp(() {
    auth = AuthService();
    dioAdapter = DioAdapter(dio: auth.dio);
    historyService = HistoryService(auth);
  });

  group('HistoryService — fetchRecentPurchases', () {
    test('빈 목록 반환 시 빈 리스트', () async {
      // 마이페이지 방문
      dioAdapter.onGet(
        'https://www.dhlottery.co.kr/mypage/mylotteryledger',
        (server) => server.reply(200, ''),
      );

      // 구매 목록 조회 — 빈 리스트
      dioAdapter.onGet(
        'https://www.dhlottery.co.kr/mypage/selectMyLotteryledger.do',
        (server) => server.reply(200, {
          'data': {'list': []}
        }),
      );

      final purchases = await historyService.fetchRecentPurchases();
      expect(purchases, isEmpty);
    });

    test('로또6/45 아닌 항목은 건너뜀', () async {
      dioAdapter.onGet(
        'https://www.dhlottery.co.kr/mypage/mylotteryledger',
        (server) => server.reply(200, ''),
      );

      dioAdapter.onGet(
        'https://www.dhlottery.co.kr/mypage/selectMyLotteryledger.do',
        (server) => server.reply(200, {
          'data': {
            'list': [
              {
                'ltGdsNm': '연금복권',
                'ltEpsdView': '100회',
                'ntslOrdrNo': 'ORD001',
                'gmInfo': 'GM001',
                'eltOrdrDt': '2024-01-01',
                'epsdRflDt': '2024-01-06',
              }
            ]
          }
        }),
      );

      final purchases = await historyService.fetchRecentPurchases();
      expect(purchases, isEmpty);
    });

    test('정상 구매 내역 파싱 — 추첨 완료', () async {
      dioAdapter.onGet(
        'https://www.dhlottery.co.kr/mypage/mylotteryledger',
        (server) => server.reply(200, ''),
      );

      dioAdapter.onGet(
        'https://www.dhlottery.co.kr/mypage/selectMyLotteryledger.do',
        (server) => server.reply(200, {
          'data': {
            'list': [
              {
                'ltGdsNm': '로또6/45',
                'ltEpsdView': '1100회',
                'ntslOrdrNo': 'ORD100',
                'gmInfo': 'BARCD100',
                'eltOrdrDt': '2024-01-03',
                'epsdRflDt': '2024-01-06',
              }
            ]
          }
        }),
      );

      // 상세 조회 mock
      dioAdapter.onGet(
        RegExp(r'lotto645TicketDetail'),
        (server) => server.reply(200, {
          'data': {
            'success': true,
            'ticket': {
              'drawed': true,
              'win_num': [3, 11, 22, 33, 40, 45],
              'bonus_num': 7,
              'game_dtl': [
                {
                  'num': [1, 5, 12, 22, 33, 44],
                  'type': 3, // 자동
                  'rank': 5,
                  'amt': 5000,
                },
                {
                  'num': [3, 11, 22, 33, 40, 45],
                  'type': 1, // 수동
                  'rank': 1,
                  'amt': 2000000000,
                },
              ],
            },
          }
        }),
      );

      final purchases = await historyService.fetchRecentPurchases();
      expect(purchases.length, equals(1));

      final p = purchases[0];
      expect(p.round, equals(1100));
      expect(p.numbers.length, equals(2));
      expect(p.autoCount, equals(1));
      expect(p.manualCount, equals(1));
      expect(p.amount, equals(2000));
      expect(p.checked, isTrue);
      expect(p.rank, equals('rank1')); // 최고 등수
      expect(p.prize, equals(2000005000)); // 총 당첨금
      expect(p.gameRanks, equals(['rank5', 'rank1']));
      expect(p.gamePrizes, equals([5000, 2000000000]));
      expect(p.winningNumbers, equals([3, 11, 22, 33, 40, 45]));
      expect(p.bonusNumber, equals(7));
    });

    test('미추첨 구매 내역 — pending 상태', () async {
      dioAdapter.onGet(
        'https://www.dhlottery.co.kr/mypage/mylotteryledger',
        (server) => server.reply(200, ''),
      );

      dioAdapter.onGet(
        'https://www.dhlottery.co.kr/mypage/selectMyLotteryledger.do',
        (server) => server.reply(200, {
          'data': {
            'list': [
              {
                'ltGdsNm': '로또6/45',
                'ltEpsdView': '1101회',
                'ntslOrdrNo': 'ORD101',
                'gmInfo': 'BARCD101',
                'eltOrdrDt': '2024-01-10',
                'epsdRflDt': '2024-01-13',
              }
            ]
          }
        }),
      );

      dioAdapter.onGet(
        RegExp(r'lotto645TicketDetail'),
        (server) => server.reply(200, {
          'data': {
            'success': true,
            'ticket': {
              'drawed': false,
              'win_num': null,
              'bonus_num': null,
              'game_dtl': [
                {
                  'num': [7, 14, 21, 28, 35, 42],
                  'type': 3,
                  'rank': 0,
                  'amt': 0,
                },
              ],
            },
          }
        }),
      );

      final purchases = await historyService.fetchRecentPurchases();
      expect(purchases.length, equals(1));

      final p = purchases[0];
      expect(p.round, equals(1101));
      expect(p.checked, isFalse);
      expect(p.gameRanks, equals(['pending']));
      expect(p.rank, isNull); // 미추첨이므로 bestRank 계산 안 함
    });

    test('count 파라미터로 반환 건수 제한', () async {
      dioAdapter.onGet(
        'https://www.dhlottery.co.kr/mypage/mylotteryledger',
        (server) => server.reply(200, ''),
      );

      dioAdapter.onGet(
        'https://www.dhlottery.co.kr/mypage/selectMyLotteryledger.do',
        (server) => server.reply(200, {
          'data': {
            'list': [
              for (var i = 0; i < 10; i++)
                {
                  'ltGdsNm': '로또6/45',
                  'ltEpsdView': '${1100 + i}회',
                  'ntslOrdrNo': 'ORD$i',
                  'gmInfo': 'BARCD$i',
                  'eltOrdrDt': '2024-01-03',
                  'epsdRflDt': '2024-01-06',
                }
            ]
          }
        }),
      );

      // 각 상세 조회 mock (10개 등록)
      for (var i = 0; i < 10; i++) {
        dioAdapter.onGet(
          RegExp(r'lotto645TicketDetail'),
          (server) => server.reply(200, {
            'data': {
              'success': true,
              'ticket': {
                'drawed': true,
                'win_num': [1, 2, 3, 4, 5, 6],
                'bonus_num': 7,
                'game_dtl': [
                  {'num': [1, 2, 3, 4, 5, 6], 'type': 3, 'rank': 0, 'amt': 0},
                ],
              },
            }
          }),
        );
      }

      final purchases = await historyService.fetchRecentPurchases(count: 2);
      expect(purchases.length, equals(2));
    });

    test('상세 조회 실패 시 해당 건 건너뜀', () async {
      dioAdapter.onGet(
        'https://www.dhlottery.co.kr/mypage/mylotteryledger',
        (server) => server.reply(200, ''),
      );

      dioAdapter.onGet(
        'https://www.dhlottery.co.kr/mypage/selectMyLotteryledger.do',
        (server) => server.reply(200, {
          'data': {
            'list': [
              {
                'ltGdsNm': '로또6/45',
                'ltEpsdView': '1100회',
                'ntslOrdrNo': 'ORD100',
                'gmInfo': 'BARCD100',
                'eltOrdrDt': '2024-01-03',
                'epsdRflDt': '2024-01-06',
              }
            ]
          }
        }),
      );

      // 상세 조회 실패 (success: false)
      dioAdapter.onGet(
        RegExp(r'lotto645TicketDetail'),
        (server) => server.reply(200, {
          'data': {'success': false}
        }),
      );

      final purchases = await historyService.fetchRecentPurchases();
      expect(purchases, isEmpty);
    });

    test('ntslOrdrNo 또는 gmInfo 빈 값이면 건너뜀', () async {
      dioAdapter.onGet(
        'https://www.dhlottery.co.kr/mypage/mylotteryledger',
        (server) => server.reply(200, ''),
      );

      dioAdapter.onGet(
        'https://www.dhlottery.co.kr/mypage/selectMyLotteryledger.do',
        (server) => server.reply(200, {
          'data': {
            'list': [
              {
                'ltGdsNm': '로또6/45',
                'ltEpsdView': '1100회',
                'ntslOrdrNo': '', // 빈 값
                'gmInfo': 'BARCD100',
                'eltOrdrDt': '2024-01-03',
                'epsdRflDt': '2024-01-06',
              }
            ]
          }
        }),
      );

      final purchases = await historyService.fetchRecentPurchases();
      expect(purchases, isEmpty);
    });

    test('낙첨(rank 0) 처리', () async {
      dioAdapter.onGet(
        'https://www.dhlottery.co.kr/mypage/mylotteryledger',
        (server) => server.reply(200, ''),
      );

      dioAdapter.onGet(
        'https://www.dhlottery.co.kr/mypage/selectMyLotteryledger.do',
        (server) => server.reply(200, {
          'data': {
            'list': [
              {
                'ltGdsNm': '로또6/45',
                'ltEpsdView': '1100회',
                'ntslOrdrNo': 'ORD100',
                'gmInfo': 'BARCD100',
                'eltOrdrDt': '2024-01-03',
                'epsdRflDt': '2024-01-06',
              }
            ]
          }
        }),
      );

      dioAdapter.onGet(
        RegExp(r'lotto645TicketDetail'),
        (server) => server.reply(200, {
          'data': {
            'success': true,
            'ticket': {
              'drawed': true,
              'win_num': [3, 11, 22, 33, 40, 45],
              'bonus_num': 7,
              'game_dtl': [
                {'num': [1, 2, 4, 5, 6, 8], 'type': 3, 'rank': 0, 'amt': 0},
              ],
            },
          }
        }),
      );

      final purchases = await historyService.fetchRecentPurchases();
      expect(purchases.length, equals(1));
      expect(purchases[0].rank, equals('nowin'));
      expect(purchases[0].prize, equals(0));
      expect(purchases[0].gameRanks, equals(['nowin']));
    });
  });
}
