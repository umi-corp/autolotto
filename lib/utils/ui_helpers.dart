import 'package:flutter/material.dart';
import '../l10n/app_localizations.dart';

/// 로또 번호 공 색상 (1~10 노랑, 11~20 파랑, 21~30 빨강, 31~40 회색, 41~45 초록)
Color ballColor(int n) {
  if (n <= 10) return const Color(0xFFFBC400);
  if (n <= 20) return const Color(0xFF69C8F2);
  if (n <= 30) return const Color(0xFFFF7272);
  if (n <= 40) return const Color(0xFFAAAAAA);
  return const Color(0xFFB0D840);
}

/// 로컬라이즈된 요일명 리스트 반환 (월~일)
List<String> localizedDayNames(AppLocalizations l10n) {
  return [l10n.dayMon, l10n.dayTue, l10n.dayWed, l10n.dayThu, l10n.dayFri, l10n.daySat, l10n.daySun];
}

/// 자동 구매 스케줄 텍스트 (로컬라이즈 버전)
String formatPurchaseScheduleL10n(AppLocalizations l10n, int day, int hour, int minute) {
  final dayNames = localizedDayNames(l10n);
  final h = hour.toString().padLeft(2, '0');
  final m = minute.toString().padLeft(2, '0');
  return l10n.weeklySchedule(dayNames[day - 1], '$h:$m');
}

/// rank 코드값 → 로컬라이즈 문자열
String localizedRank(AppLocalizations l10n, String rankCode) {
  switch (rankCode) {
    case 'rank1': return l10n.rank1st;
    case 'rank2': return l10n.rank2nd;
    case 'rank3': return l10n.rank3rd;
    case 'rank4': return l10n.rank4th;
    case 'rank5': return l10n.rank5th;
    case 'nowin': return l10n.statusNoWin;
    default: return rankCode;
  }
}

/// 숫자 천 단위 콤마 포맷
String formatNumber(int n) {
  final isNegative = n < 0;
  final str = (isNegative ? -n : n).toString();
  final buf = StringBuffer();
  if (isNegative) buf.write('-');
  for (var i = 0; i < str.length; i++) {
    if (i > 0 && (str.length - i) % 3 == 0) buf.write(',');
    buf.write(str[i]);
  }
  return buf.toString();
}
