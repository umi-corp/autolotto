import 'package:flutter/material.dart';

/// 로또 번호 공 색상 (1~10 노랑, 11~20 파랑, 21~30 빨강, 31~40 회색, 41~45 초록)
Color ballColor(int n) {
  if (n <= 10) return const Color(0xFFFBC400);
  if (n <= 20) return const Color(0xFF69C8F2);
  if (n <= 30) return const Color(0xFFFF7272);
  if (n <= 40) return const Color(0xFFAAAAAA);
  return const Color(0xFFB0D840);
}

/// 자동 구매 스케줄 텍스트 (예: "매주 일요일 09:00")
String formatPurchaseSchedule(int day, int hour, int minute) {
  const dayNames = ['월', '화', '수', '목', '금', '토', '일'];
  final h = hour.toString().padLeft(2, '0');
  final m = minute.toString().padLeft(2, '0');
  return '매주 ${dayNames[day - 1]}요일 $h:$m';
}

/// 숫자 천 단위 콤마 포맷
String formatNumber(int n) {
  final str = n.toString();
  final buf = StringBuffer();
  for (var i = 0; i < str.length; i++) {
    if (i > 0 && (str.length - i) % 3 == 0) buf.write(',');
    buf.write(str[i]);
  }
  return buf.toString();
}
