import 'dart:convert';
import 'package:dio/dio.dart';
import 'package:flutter/foundation.dart';
import '../utils/constants.dart';
import 'auth_service.dart';

/// 로또 6/45 구매 서비스
class PurchaseService {
  final AuthService _auth;
  final List<String> _debugLog = [];
  String get debugInfo => _debugLog.join('\n');
  
  void _log(String msg) {
    if (!kDebugMode) return;
    _debugLog.add('[${DateTime.now().toString().substring(11, 19)}] $msg');
  }

  PurchaseService(this._auth);

  /// 로또 1회차 기준일 (2002-12-07)
  static final DateTime _firstRoundDate = DateTime(2002, 12, 7);

  /// 현재 판매 중인 회차 계산
  static int getCurrentRound() {
    final now = DateTime.now();
    final today = DateTime(now.year, now.month, now.day);

    // 이번 주 토요일 계산
    final daysUntilSaturday = (6 - today.weekday) % 7;
    final thisSaturday = today.add(Duration(days: daysUntilSaturday));

    final daysDiff = thisSaturday.difference(_firstRoundDate).inDays;
    final weeksPassed = daysDiff ~/ 7;
    var round = 1 + weeksPassed;

    // 토요일 20:45(추첨시간) 이후면 다음 회차 표시
    if (now.weekday == DateTime.saturday) {
      final drawTime = DateTime(now.year, now.month, now.day, 20, 45);
      if (now.isAfter(drawTime)) {
        round += 1;
      }
    }

    return round;
  }

  /// 추첨일, 지급기한 계산
  static ({DateTime drawDate, DateTime payLimitDate}) getDrawDates() {
    final now = DateTime.now();
    final today = DateTime(now.year, now.month, now.day);
    final daysUntilSaturday = (6 - today.weekday) % 7;
    final drawDate = today.add(Duration(days: daysUntilSaturday));
    final payLimitDate = drawDate.add(const Duration(days: 365));
    return (drawDate: drawDate, payLimitDate: payLimitDate);
  }

  /// 구매 파라미터 빌드
  /// [autoGames]: 자동 게임 수
  /// [manualNumbers]: 수동 번호 리스트 (예: [[1,2,3,4,5,6], ...])
  static String buildParam(int autoGames, List<List<int>> manualNumbers) {
    final slotNames = ['A', 'B', 'C', 'D', 'E'];
    final params = <Map<String, dynamic>>[];
    var slotIdx = 0;

    // 수동 번호
    for (final numbers in manualNumbers) {
      if (slotIdx >= slotNames.length) break;
      final sorted = List<int>.from(numbers)..sort();
      params.add({
        'genType': '1',
        'arrGameChoiceNum': sorted.map((n) => n.toString().padLeft(2, '0')).join(','),
        'alpabet': slotNames[slotIdx],
      });
      slotIdx++;
    }

    // 자동 번호
    for (var i = 0; i < autoGames; i++) {
      if (slotIdx >= slotNames.length) break;
      params.add({
        'genType': '0',
        'arrGameChoiceNum': null,
        'alpabet': slotNames[slotIdx],
      });
      slotIdx++;
    }

    return jsonEncode(params);
  }

  /// API 응답에서 번호 추출
  /// 포맷: "A|01|02|04|27|39|443" (마지막 자리 = 모드)
  static List<List<int>> parseNumbersFromResponse(List<dynamic> arrGameChoiceNum) {
    final allNumbers = <List<int>>[];

    for (final line in arrGameChoiceNum) {
      final str = line.toString();
      if (str.length < 3) continue;

      try {
        // "A|01|02|04|27|39|443" → 가운데 6개 번호 추출
        final numsStr = str.substring(2, str.length - 1).split('|');
        final nums = numsStr.map(int.parse).toList();

        if (nums.length == 6 && nums.every((n) => n >= 1 && n <= 45)) {
          allNumbers.add(nums);
        }
      } catch (e) {
        debugPrint('번호 파싱 실패: $e (원본: $str)');
        continue;
      }
    }

    return allNumbers;
  }

  /// 로또 6/45 구매 실행
  /// [autoGames]: 자동 게임 수
  /// [manualNumbers]: 수동 번호 리스트
  /// 
  /// Returns: 구매 결과 (회차, 번호, 금액 등)
  Future<PurchaseResult> purchase({
    required int autoGames,
    List<List<int>> manualNumbers = const [],
  }) async {
    if (!_auth.isLoggedIn) {
      throw Exception('로그인이 필요합니다.');
    }

    final dio = _auth.dio;
    final totalGames = manualNumbers.length + autoGames;

    if (totalGames < 1 || totalGames > 5) {
      throw Exception('게임 수는 1~5개여야 합니다. (현재: $totalGames)');
    }

    // 1. 구매 페이지 방문
    _log('--- purchase: mainUrl ---');
    await dio.get(ApiConstants.mainUrl);
    _log('--- purchase: game645Url ---');
    await dio.get(ApiConstants.game645Url);

    // 2. Direct IP 획득
    _log('--- purchase: readySocket ---');
    final readyResp = await dio.post(ApiConstants.readySocketUrl);
    var readyData = readyResp.data;
    _log('readySocket type: ${readyData.runtimeType}');
    if (readyData is String) {
      _log('readySocket String(${readyData.length}): ${readyData.substring(0, readyData.length.clamp(0, 200))}');
      if (readyData.trimLeft().startsWith('{')) {
        readyData = jsonDecode(readyData);
        _log('readySocket JSON parse OK');
      } else {
        throw Exception('구매 실패: 세션 만료 (readySocket HTML). 재로그인 필요.');
      }
    }
    if (readyData is! Map) {
      throw Exception('구매 실패: readySocket 비Map 응답');
    }
    final directIp = readyData['ready_ip']?.toString() ?? '';
    if (directIp.isEmpty) {
      throw Exception('구매 실패: Direct IP 없음. 응답: $readyData');
    }
    // 3. 회차, 날짜 계산
    final round = getCurrentRound();
    final dates = getDrawDates();
    final drawDateStr =
        '${dates.drawDate.year}/${dates.drawDate.month.toString().padLeft(2, '0')}/${dates.drawDate.day.toString().padLeft(2, '0')}';
    final payLimitStr =
        '${dates.payLimitDate.year}/${dates.payLimitDate.month.toString().padLeft(2, '0')}/${dates.payLimitDate.day.toString().padLeft(2, '0')}';

    // 4. 구매 요청
    final param = buildParam(autoGames, manualNumbers);
    final buyData = {
      'round': round.toString(),
      'direct': directIp,
      'nBuyAmount': (1000 * totalGames).toString(),
      'param': param,
      'ROUND_DRAW_DATE': drawDateStr,
      'WAMT_PAY_TLMT_END_DT': payLimitStr,
      'gameCnt': totalGames,
      'saleMdaDcd': '10',
    };

    _log('--- purchase: execBuy ---');
    _log('buyData: round=${buyData["round"]} games=$totalGames direct=$directIp');
    final buyResp = await dio.post(
      ApiConstants.execBuyUrl,
      data: buyData,
      options: Options(
        contentType: Headers.formUrlEncodedContentType,
        headers: {
          'Referer': ApiConstants.game645Url,
          'Origin': ApiConstants.olottoUrl,
        },
      ),
    );

    final response = buyResp.data;
    _log('execBuy type: ${response.runtimeType}');
    
    // API가 JSON 대신 HTML을 반환하면 로그인 세션 만료
    if (response is String) {
      _log('execBuy HTML(첫200자): ${response.substring(0, response.length.clamp(0, 200))}');
      throw Exception('구매 실패: 세션 만료 (HTML 응답). 로그인을 다시 시도합니다.');
    }
    if (response is! Map) {
      throw Exception('구매 실패: 예상치 못한 응답 타입 (${response.runtimeType})');
    }
    
    final result = response['result'] ?? {};
    final resultCode = result['resultCode']?.toString();

    if (resultCode != '100') {
      final msg = result['resultMsg'] ?? '알 수 없는 오류';
      _log('구매 실패: $msg (code: $resultCode)');
      throw Exception('구매 실패: $msg');
    }

    // 5. 번호 추출
    final arrGame = List<dynamic>.from(result['arrGameChoiceNum'] ?? []);
    final numbers = parseNumbersFromResponse(arrGame);

    return PurchaseResult(
      round: round,
      numbers: numbers,
      autoCount: autoGames,
      manualCount: manualNumbers.length,
      amount: totalGames * 1000,
    );
  }
}

/// 구매 결과 모델
class PurchaseResult {
  final int round;
  final List<List<int>> numbers;
  final int autoCount;
  final int manualCount;
  final int amount;

  PurchaseResult({
    required this.round,
    required this.numbers,
    required this.autoCount,
    required this.manualCount,
    required this.amount,
  });

  int get totalGames => autoCount + manualCount;
}
