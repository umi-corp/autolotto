import 'dart:typed_data';
import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http_mock_adapter/http_mock_adapter.dart';
import 'package:pointycastle/export.dart';
import 'package:autolotto/services/auth_service.dart';
import 'package:autolotto/utils/constants.dart';

/// 테스트용 RSA 키 쌍 (고정 시드로 재현 가능)
late String _testModulus;
late String _testExponent;

void _initTestRsaKey() {
  final secureRandom = FortunaRandom();
  final seed = Uint8List(32);
  for (var i = 0; i < 32; i++) seed[i] = i + 1;
  secureRandom.seed(KeyParameter(seed));

  final keyGen = RSAKeyGenerator()
    ..init(ParametersWithRandom(
      RSAKeyGeneratorParameters(BigInt.from(65537), 512, 64),
      secureRandom,
    ));

  final pair = keyGen.generateKeyPair();
  final pub = pair.publicKey as RSAPublicKey;
  _testModulus = pub.modulus!.toRadixString(16);
  _testExponent = pub.publicExponent!.toRadixString(16);
}

/// 로그인 성공 플로우에 필요한 모든 엔드포인트 mock 설정
void _setupLoginMocks(DioAdapter adapter) {
  // Step 1: 메인 페이지
  adapter.onGet(
    ApiConstants.baseUrl,
    (server) => server.reply(200, ''),
  );

  // Step 2: 로그인 페이지
  adapter.onGet(
    ApiConstants.loginPageUrl,
    (server) => server.reply(200, ''),
  );

  // Step 3: RSA 키
  adapter.onGet(
    ApiConstants.rsaModulusUrl,
    (server) => server.reply(200, {
      'data': {
        'rsaModulus': _testModulus,
        'publicExponent': _testExponent,
      }
    }),
  );

  // Step 4: 로그인 POST — JSESSIONID 쿠키 설정
  adapter.onPost(
    ApiConstants.loginUrl,
    (server) => server.reply(200, '', headers: {
      'set-cookie': [
        'JSESSIONID=test-session-123; Domain=.dhlottery.co.kr; Path=/',
      ],
    }),
    data: Matchers.any,
  );

  // Step 5: main 방문
  adapter.onGet(
    ApiConstants.mainUrl,
    (server) => server.reply(200, ''),
  );

  // Step 5: game645 방문 — ol 도메인 JSESSIONID 설정
  adapter.onGet(
    ApiConstants.game645Url,
    (server) => server.reply(200, '', headers: {
      'set-cookie': [
        'JSESSIONID=ol-session-456; Path=/',
      ],
    }),
  );

  // Step 6: 로그인 검증 (mypage API)
  adapter.onGet(
    '${ApiConstants.baseUrl}/mypage/selectUserMndp.do',
    (server) => server.reply(200, {'data': {}}),
  );
}

void main() {
  setUpAll(() {
    _initTestRsaKey();
  });

  group('AuthService — 초기 상태', () {
    test('isLoggedIn 초기값은 false', () {
      final auth = AuthService();
      expect(auth.isLoggedIn, isFalse);
    });

    test('미로그인 시 getBalance 0 반환', () async {
      final auth = AuthService();
      final balance = await auth.getBalance();
      expect(balance, equals(0));
    });
  });

  group('AuthService — logout', () {
    test('logout 후 isLoggedIn false', () {
      final auth = AuthService();
      auth.logout();
      expect(auth.isLoggedIn, isFalse);
    });
  });

  group('AuthService — login', () {
    late AuthService auth;
    late DioAdapter dioAdapter;

    setUp(() {
      auth = AuthService();
      dioAdapter = DioAdapter(dio: auth.dio);
    });

    test('정상 로그인 성공', () async {
      _setupLoginMocks(dioAdapter);

      final result = await auth.login('testUser', 'testPass');
      expect(result, isTrue);
      expect(auth.isLoggedIn, isTrue);
    });

    test('로그인 성공 후 logout 하면 isLoggedIn false', () async {
      _setupLoginMocks(dioAdapter);

      await auth.login('testUser', 'testPass');
      expect(auth.isLoggedIn, isTrue);

      auth.logout();
      expect(auth.isLoggedIn, isFalse);
    });

    test('RSA 키 없으면 예외 발생', () async {
      dioAdapter.onGet(
        ApiConstants.baseUrl,
        (server) => server.reply(200, ''),
      );
      dioAdapter.onGet(
        ApiConstants.loginPageUrl,
        (server) => server.reply(200, ''),
      );
      // RSA 키 없음 (data가 null)
      dioAdapter.onGet(
        ApiConstants.rsaModulusUrl,
        (server) => server.reply(200, {'data': null}),
      );

      expect(
        () => auth.login('testUser', 'testPass'),
        throwsA(isA<Exception>()),
      );
    });

    test('인증 실패 시 INVALID_CREDENTIALS 예외', () async {
      // 로그인 POST까지는 성공하나 검증에서 실패
      dioAdapter.onGet(
        ApiConstants.baseUrl,
        (server) => server.reply(200, ''),
      );
      dioAdapter.onGet(
        ApiConstants.loginPageUrl,
        (server) => server.reply(200, ''),
      );
      dioAdapter.onGet(
        ApiConstants.rsaModulusUrl,
        (server) => server.reply(200, {
          'data': {
            'rsaModulus': _testModulus,
            'publicExponent': _testExponent,
          }
        }),
      );
      dioAdapter.onPost(
        ApiConstants.loginUrl,
        (server) => server.reply(200, '', headers: {
          'set-cookie': [
            'JSESSIONID=test-session; Domain=.dhlottery.co.kr; Path=/',
          ],
        }),
        data: Matchers.any,
      );
      dioAdapter.onGet(
        ApiConstants.mainUrl,
        (server) => server.reply(200, ''),
      );
      dioAdapter.onGet(
        ApiConstants.game645Url,
        (server) => server.reply(200, '', headers: {
          'set-cookie': ['JSESSIONID=ol-session; Path=/'],
        }),
      );
      // 검증 실패: 401 반환
      dioAdapter.onGet(
        '${ApiConstants.baseUrl}/mypage/selectUserMndp.do',
        (server) => server.reply(401, 'Unauthorized'),
      );

      expect(
        () => auth.login('testUser', 'testPass'),
        throwsA(
          predicate((e) =>
              e is Exception && e.toString().contains('INVALID_CREDENTIALS')),
        ),
      );
    });
  });

  group('AuthService — getBalance', () {
    late AuthService auth;
    late DioAdapter dioAdapter;

    setUp(() {
      auth = AuthService();
      dioAdapter = DioAdapter(dio: auth.dio);
    });

    test('로그인 후 잔액 조회 성공', () async {
      _setupLoginMocks(dioAdapter);
      await auth.login('testUser', 'testPass');

      // getBalance용 mock (기존 mock은 소모됐을 수 있으므로 재등록)
      dioAdapter.onGet(
        '${ApiConstants.baseUrl}/mypage/selectUserMndp.do',
        (server) => server.reply(200, {
          'data': {
            'userMndp': {'crntEntrsAmt': 50000}
          }
        }),
      );

      final balance = await auth.getBalance();
      expect(balance, equals(50000));
    });

    test('잔액 JSON 문자열 응답 파싱', () async {
      _setupLoginMocks(dioAdapter);
      await auth.login('testUser', 'testPass');

      dioAdapter.onGet(
        '${ApiConstants.baseUrl}/mypage/selectUserMndp.do',
        (server) => server.reply(
          200,
          '{"data":{"userMndp":{"crntEntrsAmt":25000}}}',
        ),
      );

      final balance = await auth.getBalance();
      expect(balance, equals(25000));
    });

    test('잔액 필드 없으면 0 반환', () async {
      _setupLoginMocks(dioAdapter);
      await auth.login('testUser', 'testPass');

      dioAdapter.onGet(
        '${ApiConstants.baseUrl}/mypage/selectUserMndp.do',
        (server) => server.reply(200, {'data': {}}),
      );

      final balance = await auth.getBalance();
      expect(balance, equals(0));
    });

    test('잔액 조회 에러 시 0 반환', () async {
      _setupLoginMocks(dioAdapter);
      await auth.login('testUser', 'testPass');

      dioAdapter.onGet(
        '${ApiConstants.baseUrl}/mypage/selectUserMndp.do',
        (server) => server.throws(
          0,
          DioException(requestOptions: RequestOptions(path: '')),
        ),
      );

      final balance = await auth.getBalance();
      expect(balance, equals(0));
    });
  });
}
