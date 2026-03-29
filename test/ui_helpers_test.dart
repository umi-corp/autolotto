import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:autolotto/utils/ui_helpers.dart';

void main() {
  group('ballColor', () {
    test('1~10은 노란색', () {
      for (var i = 1; i <= 10; i++) {
        expect(ballColor(i), const Color(0xFFFBC400));
      }
    });

    test('11~20은 파란색', () {
      for (var i = 11; i <= 20; i++) {
        expect(ballColor(i), const Color(0xFF69C8F2));
      }
    });

    test('21~30은 빨간색', () {
      for (var i = 21; i <= 30; i++) {
        expect(ballColor(i), const Color(0xFFFF7272));
      }
    });

    test('31~40은 회색', () {
      for (var i = 31; i <= 40; i++) {
        expect(ballColor(i), const Color(0xFFAAAAAA));
      }
    });

    test('41~45는 초록색', () {
      for (var i = 41; i <= 45; i++) {
        expect(ballColor(i), const Color(0xFFB0D840));
      }
    });
  });

  group('formatNumber', () {
    test('천 단위 콤마 포맷', () {
      expect(formatNumber(0), '0');
      expect(formatNumber(999), '999');
      expect(formatNumber(1000), '1,000');
      expect(formatNumber(10000), '10,000');
      expect(formatNumber(1000000), '1,000,000');
      expect(formatNumber(1234567890), '1,234,567,890');
    });

    test('음수 포맷', () {
      expect(formatNumber(-1000), '-1,000');
      expect(formatNumber(-999), '-999');
      expect(formatNumber(-1234567), '-1,234,567');
    });
  });
}
