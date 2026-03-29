import 'package:flutter_test/flutter_test.dart';
import 'package:autolotto/utils/constants.dart';

void main() {
  group('ApiConstants', () {
    test('baseUrl은 HTTPS', () {
      expect(ApiConstants.baseUrl, startsWith('https://'));
    });

    test('olottoUrl은 HTTPS', () {
      expect(ApiConstants.olottoUrl, startsWith('https://'));
    });

    test('모든 URL은 baseUrl 또는 olottoUrl 기반', () {
      final urls = [
        ApiConstants.rsaModulusUrl,
        ApiConstants.loginUrl,
        ApiConstants.loginPageUrl,
        ApiConstants.mainUrl,
        ApiConstants.winningNumberUrl,
        ApiConstants.balanceUrl,
        ApiConstants.purchaseHistoryUrl,
      ];

      for (final url in urls) {
        expect(url, startsWith(ApiConstants.baseUrl));
      }

      final olottoUrls = [
        ApiConstants.game645Url,
        ApiConstants.readySocketUrl,
        ApiConstants.execBuyUrl,
      ];

      for (final url in olottoUrls) {
        expect(url, startsWith(ApiConstants.olottoUrl));
      }
    });

    test('defaultHeaders에 User-Agent 포함', () {
      expect(ApiConstants.defaultHeaders, contains('User-Agent'));
      expect(ApiConstants.defaultHeaders['User-Agent'], isNotEmpty);
    });

    test('defaultHeaders에 Accept-Language 포함 (ko-KR)', () {
      expect(ApiConstants.defaultHeaders['Accept-Language'], contains('ko-KR'));
    });
  });
}
