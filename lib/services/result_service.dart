import 'package:dio/dio.dart';
import '../utils/constants.dart';

/// 당첨 확인 서비스
class ResultService {
  final Dio _dio;

  ResultService([Dio? dio])
      : _dio = dio ??
            Dio(BaseOptions(
              connectTimeout: const Duration(seconds: 10),
              receiveTimeout: const Duration(seconds: 10),
              headers: ApiConstants.defaultHeaders,
            ));

  /// 당첨번호 조회 (로그인 불필요)
  /// [roundNo]: 회차 번호 (null이면 최신)
  Future<WinningResult?> getWinningNumbers({int? roundNo}) async {
    try {
      // 쿠키 획득용 메인 방문
      await _dio.get(ApiConstants.baseUrl);

      final params = <String, dynamic>{};
      if (roundNo != null) {
        params['drwNo'] = roundNo.toString();
      }

      final resp = await _dio.get(
        ApiConstants.winningNumberUrl,
        queryParameters: params,
      );

      if (resp.statusCode != 200) return null;

      final data = resp.data['data'] ?? {};
      final items = data['list'] as List?;
      if (items == null || items.isEmpty) return null;

      final item = items[0];
      final numbers = [
        item['tm1WnNo'] as int,
        item['tm2WnNo'] as int,
        item['tm3WnNo'] as int,
        item['tm4WnNo'] as int,
        item['tm5WnNo'] as int,
        item['tm6WnNo'] as int,
      ]..sort();

      return WinningResult(
        round: item['ltEpsd'] as int,
        numbers: numbers,
        bonus: item['bnsWnNo'] as int,
        date: _parseDate(item['ltRflYmd']?.toString() ?? ''),
        prize1st: item['rnk1WnAmt'] as int? ?? 0,
        prize2nd: item['rnk2WnAmt'] as int? ?? 0,
        prize3rd: item['rnk3WnAmt'] as int? ?? 0,
      );
    } catch (_) {
      return null;
    }
  }

  /// 내 번호와 당첨번호 매칭
  static List<MatchResult> checkMatches(
    List<List<int>> myNumbers,
    List<int> winningNumbers,
    int bonus,
  ) {
    final winningSet = winningNumbers.toSet();
    final results = <MatchResult>[];

    for (final nums in myNumbers) {
      final numsSet = nums.toSet();
      final matched = numsSet.intersection(winningSet).toList()..sort();
      final count = matched.length;
      final bonusMatch = numsSet.contains(bonus);

      String rank;
      int prize;

      if (count == 6) {
        rank = '1등';
        prize = 0; // 변동
      } else if (count == 5 && bonusMatch) {
        rank = '2등';
        prize = 0; // 변동
      } else if (count == 5) {
        rank = '3등';
        prize = 0; // 변동
      } else if (count == 4) {
        rank = '4등';
        prize = 50000;
      } else if (count == 3) {
        rank = '5등';
        prize = 5000;
      } else {
        rank = '낙첨';
        prize = 0;
      }

      results.add(MatchResult(
        numbers: nums,
        matched: matched,
        matchCount: count,
        bonusMatch: bonusMatch,
        rank: rank,
        prize: prize,
      ));
    }

    return results;
  }

  String _parseDate(String raw) {
    if (raw.length == 8) {
      return '${raw.substring(0, 4)}-${raw.substring(4, 6)}-${raw.substring(6, 8)}';
    }
    return raw;
  }
}

/// 당첨번호 결과
class WinningResult {
  final int round;
  final List<int> numbers;
  final int bonus;
  final String date;
  final int prize1st;
  final int prize2nd;
  final int prize3rd;

  WinningResult({
    required this.round,
    required this.numbers,
    required this.bonus,
    required this.date,
    this.prize1st = 0,
    this.prize2nd = 0,
    this.prize3rd = 0,
  });
}

/// 매칭 결과
class MatchResult {
  final List<int> numbers;
  final List<int> matched;
  final int matchCount;
  final bool bonusMatch;
  final String rank;
  final int prize;

  MatchResult({
    required this.numbers,
    required this.matched,
    required this.matchCount,
    required this.bonusMatch,
    required this.rank,
    required this.prize,
  });

  bool get isWinner => rank != '낙첨';
}
