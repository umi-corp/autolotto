/// 동행복권 API 상수
class ApiConstants {
  static const String baseUrl = 'https://www.dhlottery.co.kr';
  static const String olottoUrl = 'https://ol.dhlottery.co.kr';

  // Auth
  static const String rsaModulusUrl = '$baseUrl/login/selectRsaModulus.do';
  static const String loginUrl = '$baseUrl/login/securityLoginCheck.do';
  static const String loginPageUrl = '$baseUrl/login';
  static const String mainUrl = '$baseUrl/main';

  // Purchase
  static const String game645Url = '$olottoUrl/olotto/game/game645.do';
  static const String readySocketUrl = '$olottoUrl/olotto/game/egovUserReadySocket.json';
  static const String execBuyUrl = '$olottoUrl/olotto/game/execBuy.do';

  // Result
  static const String winningNumberUrl = '$baseUrl/lt645/selectPstLt645Info.do';

  // Balance
  static const String balanceUrl = '$baseUrl/mypage/selectUserMndp.do';
  static const String purchaseHistoryUrl = '$baseUrl/mypage/selectMyLotteryledger.do';

  // Headers
  static const Map<String, String> defaultHeaders = {
    'User-Agent': 'Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36',
    'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8',
    'Accept-Language': 'ko-KR,ko;q=0.9,en-US;q=0.8,en;q=0.7',
    'Connection': 'keep-alive',
  };
}
