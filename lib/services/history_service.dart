import 'package:dio/dio.dart';
import 'package:flutter/foundation.dart';
import 'package:intl/intl.dart';
import 'auth_service.dart';
import '../models/purchase.dart';
import '../utils/constants.dart';

/// 동행복권 마이페이지에서 구매 내역 조회
class HistoryService {
  final AuthService _auth;

  HistoryService(this._auth);

  /// API rank 숫자 → 코드값 (0=nowin, 1~5=rank1~rank5)
  static String _rankLabel(int rank) {
    if (rank >= 1 && rank <= 5) return 'rank$rank';
    return 'nowin';
  }

  /// 최근 구매 내역 가져오기
  Future<List<Purchase>> fetchRecentPurchases({int count = 5}) async {
    final now = DateTime.now();
    final thirtyDaysAgo = now.subtract(const Duration(days: 30));
    final todayStr = DateFormat('yyyyMMdd').format(now);
    final fromStr = DateFormat('yyyyMMdd').format(thirtyDaysAgo);

    // 1. 마이페이지 방문 (Referer용)
    await _auth.dio.get('https://www.dhlottery.co.kr/mypage/mylotteryledger');

    // 2. 구매 목록 조회
    final listResp = await _auth.dio.get(
      ApiConstants.purchaseHistoryUrl,
      queryParameters: {
        'srchStrDt': fromStr,
        'srchEndDt': todayStr,
        'pageNum': 1,
        'recordCountPerPage': 100,
        '_': DateTime.now().millisecondsSinceEpoch,
      },
      options: Options(headers: {
        'Accept': 'application/json, text/javascript, */*; q=0.01',
        'X-Requested-With': 'XMLHttpRequest',
        'Referer': 'https://www.dhlottery.co.kr/mypage/mylotteryledger',
      }),
    );

    final items = (listResp.data['data']?['list'] as List?) ?? [];
    final purchases = <Purchase>[];

    for (final item in items) {
      if (item['ltGdsNm'] != '로또6/45') continue;

      final roundStr = item['ltEpsdView']?.toString().replaceAll('회', '').trim() ?? '';
      final round = int.tryParse(roundStr) ?? 0;
      final ntslOrdrNo = item['ntslOrdrNo']?.toString() ?? '';
      final gmInfo = item['gmInfo']?.toString() ?? '';
      final purchaseDateStr = item['eltOrdrDt']?.toString() ?? '';
      final drawDateStr = item['epsdRflDt']?.toString() ?? '';

      if (ntslOrdrNo.isEmpty || gmInfo.isEmpty) continue;

      // 3. 상세 조회 (번호 + 당첨결과)
      try {
        final purchaseDt = DateTime.tryParse(purchaseDateStr) ?? now;
        final startDt = DateFormat('yyyyMMdd').format(purchaseDt.subtract(const Duration(days: 7)));
        final endDt = DateFormat('yyyyMMdd').format(purchaseDt.add(const Duration(days: 7)));

        final detailResp = await _auth.dio.get(
          'https://www.dhlottery.co.kr/mypage/lotto645TicketDetail.do'
          '?ntslOrdrNo=$ntslOrdrNo&srchStrDt=$startDt&srchEndDt=$endDt&barcd=$gmInfo',
          options: Options(headers: {
            'X-Requested-With': 'XMLHttpRequest',
            'Referer': 'https://www.dhlottery.co.kr/mypage/mylotteryledger',
          }),
        );

        final detailData = detailResp.data;
        if (detailData['data']?['success'] != true) continue;

        final ticket = detailData['data']['ticket'];
        final gameDtl = ticket['game_dtl'] as List? ?? [];
        final drawed = ticket['drawed'] as bool? ?? false;

        final numbers = <List<int>>[];
        var autoCount = 0;
        var manualCount = 0;

        // 게임별 당첨결과 (API에서 직접 제공)
        final gameRanks = <String>[];
        final gamePrizes = <int>[];

        for (final game in gameDtl) {
          final nums = (game['num'] as List?)?.cast<int>() ?? [];
          if (nums.length == 6) {
            numbers.add(nums);
            final type = game['type'] ?? 3;
            if (type == 1) {
              manualCount++;
            } else {
              autoCount++;
            }

            // API가 제공하는 게임별 등수/당첨금
            final apiRank = game['rank'] as int? ?? 0;
            final apiAmt = game['amt'] as int? ?? 0;
            gameRanks.add(drawed ? _rankLabel(apiRank) : 'pending');
            gamePrizes.add(apiAmt);
          }
        }

        if (numbers.isEmpty) continue;

        // 추첨 완료 여부로 checked 판단
        final checked = drawed;

        // 최고 등수 & 총 당첨금
        String? bestRank;
        int totalPrize = 0;

        if (checked) {
          const rankOrder = ['rank1', 'rank2', 'rank3', 'rank4', 'rank5', 'nowin'];
          bestRank = 'nowin';
          for (final r in gameRanks) {
            if (rankOrder.indexOf(r) < rankOrder.indexOf(bestRank!)) {
              bestRank = r;
            }
          }
          totalPrize = gamePrizes.fold(0, (sum, p) => sum + p);
        }

        final purchase = Purchase(
          round: round,
          date: DateTime.tryParse(drawDateStr) ?? DateTime.tryParse(purchaseDateStr) ?? now,
          numbers: numbers,
          autoCount: autoCount,
          manualCount: manualCount,
          amount: numbers.length * 1000,
        );
        purchase.checked = checked;
        purchase.rank = bestRank;
        purchase.prize = totalPrize;
        purchase.gameRanks = gameRanks;
        purchase.gamePrizes = gamePrizes;

        purchases.add(purchase);
        if (purchases.length >= count) break;
      } catch (e) {
        debugPrint('구매 상세 조회 실패 (ntslOrdrNo: $ntslOrdrNo): $e');
        continue;
      }
    }

    return purchases;
  }
}
