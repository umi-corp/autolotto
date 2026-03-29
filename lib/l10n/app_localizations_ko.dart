// ignore: unused_import
import 'package:intl/intl.dart' as intl;
import 'app_localizations.dart';

// ignore_for_file: type=lint

/// The translations for Korean (`ko`).
class AppLocalizationsKo extends AppLocalizations {
  AppLocalizationsKo([String locale = 'ko']) : super(locale);

  @override
  String get bottomNavHome => '홈';

  @override
  String get bottomNavNumbers => '번호설정';

  @override
  String get bottomNavHistory => '기록';

  @override
  String get bottomNavSettings => '설정';

  @override
  String get appTitle => 'AutoLotto';

  @override
  String get autoPurchaseEnabled => '자동 구매 활성화';

  @override
  String get autoPurchaseDisabled => '자동 구매 비활성화';

  @override
  String autoPurchaseSchedule(String schedule) {
    return '$schedule에 자동 구매됩니다';
  }

  @override
  String get enableInSettings => '설정에서 활성화해주세요';

  @override
  String get buttonSetupNumbers => '🎯 번호 설정하기';

  @override
  String countdownTitle(int round) {
    return '제 $round회 추첨까지';
  }

  @override
  String get countdownDays => '일';

  @override
  String get countdownHours => '시간';

  @override
  String get countdownMinutes => '분';

  @override
  String get countdownSeconds => '초';

  @override
  String winningNumbersWithRound(int round) {
    return '🏆 제 $round회 당첨번호';
  }

  @override
  String get winningNumbersPrevious => '🏆 지난 당첨번호';

  @override
  String get winningNumbersLoadError => '당첨번호를 불러올 수 없습니다';

  @override
  String get balanceTitle => '예치금 잔액';

  @override
  String get numberSetupTitle => '번호 설정';

  @override
  String get bannerEnableAutoPurchase => '설정에서 자동 구매를 활성화해주세요';

  @override
  String get numberSetupInstruction =>
      '매주 자동 구매할 번호를 설정하세요.\n수동 번호는 매주 고정, 자동은 매주 랜덤 생성됩니다.';

  @override
  String get modeManual => '✏️ 수동';

  @override
  String get modeAuto => '🎲 자동';

  @override
  String get autoNumberTitle => '자동 번호';

  @override
  String get autoNumberSubtitle => '매주 랜덤으로 생성됩니다';

  @override
  String get buttonAllAuto => '전부 자동';

  @override
  String get buttonReset => '초기화';

  @override
  String buttonConfirmGame(String letter) {
    return '게임 $letter 확정 ✓';
  }

  @override
  String selectionCount(int count) {
    return '선택: $count/6  ';
  }

  @override
  String get gameSummaryTitle => '📋 게임 설정';

  @override
  String get gameSummarySelecting => '선택 중...';

  @override
  String get gameSummaryNotSet => '미설정';

  @override
  String get gameSummaryAuto => '🎲 자동 (매주 랜덤)';

  @override
  String get buttonSaveDone => '저장 완료!';

  @override
  String buttonSaveGames(int count) {
    return '$count게임 설정 저장';
  }

  @override
  String snackbarSaveSuccess(int count, String schedule) {
    return '✅ $count게임 설정 저장 완료! $schedule에 자동 구매됩니다.';
  }

  @override
  String get historyTitle => '구매 기록';

  @override
  String get historyNoRecords => '구매 기록이 없습니다';

  @override
  String get historyLoginToLoad => '로그인하면 구매 기록을 불러옵니다';

  @override
  String historyLoadError(String error) {
    return '조회 실패: $error';
  }

  @override
  String get statusPending => '확인 대기';

  @override
  String get statusNoWin => '낙첨';

  @override
  String rankWithEmoji(String rank) {
    return '$rank 🎉';
  }

  @override
  String roundLabel(int round) {
    return '제 $round회';
  }

  @override
  String prizeLabel(String amount) {
    return '당첨금: ₩$amount';
  }

  @override
  String get rank1st => '1등';

  @override
  String get rank2nd => '2등';

  @override
  String get rank3rd => '3등';

  @override
  String get rank4th => '4등';

  @override
  String get rank5th => '5등';

  @override
  String get settingsTitle => '설정';

  @override
  String get sectionAccount => '👤 계정';

  @override
  String get sectionAutoPurchase => '⏰ 자동 구매';

  @override
  String get sectionNotifications => '🔔 알림';

  @override
  String get sectionAppInfo => '📱 앱 정보';

  @override
  String get dhLotteryAccount => '동행복권 계정';

  @override
  String get statusLoggedIn => '로그인됨';

  @override
  String get statusLoginRequired => '로그인 필요';

  @override
  String get buttonLogout => '로그아웃';

  @override
  String get buttonLogin => '로그인';

  @override
  String get dialogLoginTitle => '동행복권 로그인';

  @override
  String get inputUserId => '아이디';

  @override
  String get inputPassword => '비밀번호';

  @override
  String get buttonCancel => '취소';

  @override
  String get buttonLoggingIn => '로그인 중...';

  @override
  String get snackbarLoginSuccess => '✅ 로그인 성공!';

  @override
  String get snackbarLogoutSuccess => '로그아웃 완료';

  @override
  String get settingEnableAutoPurchase => '자동 구매 활성화';

  @override
  String get hintLoginRequired => '로그인 후 사용 가능';

  @override
  String gamesConfigured(int count) {
    return '$count게임 설정됨';
  }

  @override
  String get hintChangeInNumberTab => '번호 설정 탭에서 변경';

  @override
  String get hintSetupGamesInNumberTab => '번호 설정 탭에서 게임을 설정해주세요';

  @override
  String get settingPurchaseDay => '구매 요일';

  @override
  String get settingPurchaseTime => '구매 시간';

  @override
  String get settingBatteryOptimization => '배터리 최적화 제외';

  @override
  String get hintBatteryOptimization => '정시 실행을 위해 권장';

  @override
  String get settingPurchaseNoti => '구매 완료 알림';

  @override
  String get settingResultNoti => '당첨 결과 알림';

  @override
  String get notificationResultTime => '매주 토요일 21:00';

  @override
  String get settingVersion => '버전';

  @override
  String get settingOpenSource => '오픈소스';

  @override
  String get settingResetData => '데이터 초기화';

  @override
  String get dialogResetTitle => '데이터 초기화';

  @override
  String get dialogResetMessage => '모든 설정과 구매 기록이 삭제됩니다.\n정말 초기화하시겠습니까?';

  @override
  String get buttonReset2 => '초기화';

  @override
  String get errorInvalidPurchaseTime =>
      '현재 설정된 시간이 해당 요일에 구매 불가합니다. 시간을 먼저 변경해주세요.';

  @override
  String get errorPurchaseTimeRestriction =>
      '해당 시간에는 구매할 수 없습니다.\n평일/일: 06:00~23:59, 토: 06:00~19:59';

  @override
  String get snackbarBatteryAlreadyExcluded => '✅ 이미 배터리 최적화에서 제외되어 있습니다';

  @override
  String get snackbarBatteryManualDisable => '앱 설정에서 배터리 최적화를 직접 해제해주세요';

  @override
  String errorBatterySettings(String error) {
    return '배터리 최적화 설정을 열 수 없습니다: $error';
  }

  @override
  String get dayMon => '월';

  @override
  String get dayTue => '화';

  @override
  String get dayWed => '수';

  @override
  String get dayThu => '목';

  @override
  String get dayFri => '금';

  @override
  String get daySat => '토';

  @override
  String get daySun => '일';

  @override
  String dayFormat(String day) {
    return '$day요일';
  }

  @override
  String weeklySchedule(String day, String time) {
    return '매주 $day요일 $time';
  }

  @override
  String get settingLanguage => '언어';

  @override
  String get languageSystem => '시스템 기본';

  @override
  String get languageKo => '한국어';

  @override
  String get languageEn => 'English';

  @override
  String get languageJa => '日本語';

  @override
  String get errorInvalidCredentials => '아이디 또는 비밀번호가 올바르지 않습니다.';

  @override
  String get balanceAlertTitle => '잔액 부족 알림';

  @override
  String get balanceAlertDesc => '설정 금액 이하일 때 알림';

  @override
  String get balanceThreshold => '알림 기준 금액';

  @override
  String get chargeNow => '충전하기';

  @override
  String get balanceLowNotifTitle => '잔액 부족';

  @override
  String balanceLowNotifBody(String amount) {
    return '예치금 잔액이 $amount원입니다. 충전이 필요합니다.';
  }

  @override
  String get thresholdCustom => '직접입력';

  @override
  String get thresholdInputTitle => '알림 기준 금액 입력';

  @override
  String get thresholdInputHint => '금액 (원)';

  @override
  String get buttonConfirm => '확인';

  @override
  String get sectionDonation => '개발자 후원하기';

  @override
  String get donationTitle => '커피 한 잔 후원';
}
