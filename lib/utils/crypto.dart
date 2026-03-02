import 'dart:convert';
import 'dart:typed_data';
import 'package:pointycastle/export.dart';

/// RSA 암호화 (동행복권 로그인용)
class RsaCrypto {
  /// RSA PKCS1v1.5 암호화 후 hex 문자열 반환
  static String encrypt(String plainText, String modulusHex, String exponentHex) {
    final n = BigInt.parse(modulusHex, radix: 16);
    final e = BigInt.parse(exponentHex, radix: 16);

    final publicKey = RSAPublicKey(n, e);
    final encryptor = PKCS1Encoding(RSAEngine())
      ..init(true, PublicKeyParameter<RSAPublicKey>(publicKey));

    final input = Uint8List.fromList(utf8.encode(plainText));
    final output = encryptor.process(input);

    return output.map((b) => b.toRadixString(16).padLeft(2, '0')).join();
  }
}
