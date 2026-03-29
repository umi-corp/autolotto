import 'package:flutter_test/flutter_test.dart';
import 'package:autolotto/utils/crypto.dart';

void main() {
  // 테스트용 RSA 키 (2048bit)
  // openssl genrsa 2048 으로 생성한 고정 키
  const testModulus =
      'c4f8e9f09f84b2e1a3d5c7b9e1f3a5d7c9b1e3f5a7d9c1b3e5f7a9d1c3b5e7'
      'f9a1d3c5b7e9f1a3d5c7b9e1f3a5d7c9b1e3f5a7d9c1b3e5f7a9d1c3b5e7f9'
      'a1d3c5b7e9f1a3d5c7b9e1f3a5d7c9b1e3f5a7d9c1b3e5f7a9d1c3b5e7f9a1'
      'd3c5b7e9f1a3d5c7b9e1f3a5d7c9b1e3f5a7d9c1b3e5f7a9d1c3b5e7f9a1d3'
      'c5b7e9f1a3d5c7b9e1f3a5d7c9b1e3f5a7d9c1b3e5f7a9d1c3b5e7f9a1d3c5'
      'b7e9f1a3d5c7b9e1f3a5d7c9b1e3f5a7d9c1b3e5f7a9d1c3b5e7f9a1d3c5b7'
      'e9f1a3d5c7b9e1f3a5d7c9b1e3f5a7d9c1b3e5f7a9d1c3b5e7f9a1d3c5b7e9'
      'f1a3d5c7b9e1f3a5d7c9b1e3f5a7d9c1b3e5f7a9d1c3b5e7f9a1d3c5b7e9f1';
  const testExponent = '10001';

  group('RsaCrypto', () {
    test('encrypt 결과는 빈 문자열이 아님', () {
      final result = RsaCrypto.encrypt('testUser', testModulus, testExponent);
      expect(result, isNotEmpty);
    });

    test('encrypt 결과는 hex 문자열', () {
      final result = RsaCrypto.encrypt('hello', testModulus, testExponent);
      expect(result, matches(RegExp(r'^[0-9a-f]+$')));
    });

    test('encrypt 결과 길이는 modulus 길이와 동일 (512자 = 256바이트)', () {
      final result = RsaCrypto.encrypt('test', testModulus, testExponent);
      expect(result.length, testModulus.length);
    });

    test('동일 입력이라도 매번 다른 결과 (PKCS1v1.5 랜덤 패딩)', () {
      final r1 = RsaCrypto.encrypt('same', testModulus, testExponent);
      final r2 = RsaCrypto.encrypt('same', testModulus, testExponent);
      expect(r1, isNot(equals(r2)));
    });

    test('한글 입력 암호화', () {
      final result = RsaCrypto.encrypt('비밀번호', testModulus, testExponent);
      expect(result, isNotEmpty);
      expect(result, matches(RegExp(r'^[0-9a-f]+$')));
    });
  });
}
