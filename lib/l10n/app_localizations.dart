import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/widgets.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:intl/intl.dart' as intl;

import 'app_localizations_en.dart';
import 'app_localizations_ja.dart';
import 'app_localizations_ko.dart';

// ignore_for_file: type=lint

/// Callers can lookup localized strings with an instance of AppLocalizations
/// returned by `AppLocalizations.of(context)`.
///
/// Applications need to include `AppLocalizations.delegate()` in their app's
/// `localizationDelegates` list, and the locales they support in the app's
/// `supportedLocales` list. For example:
///
/// ```dart
/// import 'l10n/app_localizations.dart';
///
/// return MaterialApp(
///   localizationsDelegates: AppLocalizations.localizationsDelegates,
///   supportedLocales: AppLocalizations.supportedLocales,
///   home: MyApplicationHome(),
/// );
/// ```
///
/// ## Update pubspec.yaml
///
/// Please make sure to update your pubspec.yaml to include the following
/// packages:
///
/// ```yaml
/// dependencies:
///   # Internationalization support.
///   flutter_localizations:
///     sdk: flutter
///   intl: any # Use the pinned version from flutter_localizations
///
///   # Rest of dependencies
/// ```
///
/// ## iOS Applications
///
/// iOS applications define key application metadata, including supported
/// locales, in an Info.plist file that is built into the application bundle.
/// To configure the locales supported by your app, you’ll need to edit this
/// file.
///
/// First, open your project’s ios/Runner.xcworkspace Xcode workspace file.
/// Then, in the Project Navigator, open the Info.plist file under the Runner
/// project’s Runner folder.
///
/// Next, select the Information Property List item, select Add Item from the
/// Editor menu, then select Localizations from the pop-up menu.
///
/// Select and expand the newly-created Localizations item then, for each
/// locale your application supports, add a new item and select the locale
/// you wish to add from the pop-up menu in the Value field. This list should
/// be consistent with the languages listed in the AppLocalizations.supportedLocales
/// property.
abstract class AppLocalizations {
  AppLocalizations(String locale)
    : localeName = intl.Intl.canonicalizedLocale(locale.toString());

  final String localeName;

  static AppLocalizations? of(BuildContext context) {
    return Localizations.of<AppLocalizations>(context, AppLocalizations);
  }

  static const LocalizationsDelegate<AppLocalizations> delegate =
      _AppLocalizationsDelegate();

  /// A list of this localizations delegate along with the default localizations
  /// delegates.
  ///
  /// Returns a list of localizations delegates containing this delegate along with
  /// GlobalMaterialLocalizations.delegate, GlobalCupertinoLocalizations.delegate,
  /// and GlobalWidgetsLocalizations.delegate.
  ///
  /// Additional delegates can be added by appending to this list in
  /// MaterialApp. This list does not have to be used at all if a custom list
  /// of delegates is preferred or required.
  static const List<LocalizationsDelegate<dynamic>> localizationsDelegates =
      <LocalizationsDelegate<dynamic>>[
        delegate,
        GlobalMaterialLocalizations.delegate,
        GlobalCupertinoLocalizations.delegate,
        GlobalWidgetsLocalizations.delegate,
      ];

  /// A list of this localizations delegate's supported locales.
  static const List<Locale> supportedLocales = <Locale>[
    Locale('en'),
    Locale('ja'),
    Locale('ko'),
  ];

  /// No description provided for @bottomNavHome.
  ///
  /// In ko, this message translates to:
  /// **'홈'**
  String get bottomNavHome;

  /// No description provided for @bottomNavNumbers.
  ///
  /// In ko, this message translates to:
  /// **'번호설정'**
  String get bottomNavNumbers;

  /// No description provided for @bottomNavHistory.
  ///
  /// In ko, this message translates to:
  /// **'기록'**
  String get bottomNavHistory;

  /// No description provided for @bottomNavSettings.
  ///
  /// In ko, this message translates to:
  /// **'설정'**
  String get bottomNavSettings;

  /// No description provided for @appTitle.
  ///
  /// In ko, this message translates to:
  /// **'AutoLotto'**
  String get appTitle;

  /// No description provided for @autoPurchaseEnabled.
  ///
  /// In ko, this message translates to:
  /// **'자동 구매 활성화'**
  String get autoPurchaseEnabled;

  /// No description provided for @autoPurchaseDisabled.
  ///
  /// In ko, this message translates to:
  /// **'자동 구매 비활성화'**
  String get autoPurchaseDisabled;

  /// No description provided for @autoPurchaseSchedule.
  ///
  /// In ko, this message translates to:
  /// **'{schedule}에 자동 구매됩니다'**
  String autoPurchaseSchedule(String schedule);

  /// No description provided for @enableInSettings.
  ///
  /// In ko, this message translates to:
  /// **'설정에서 활성화해주세요'**
  String get enableInSettings;

  /// No description provided for @buttonSetupNumbers.
  ///
  /// In ko, this message translates to:
  /// **'🎯 번호 설정하기'**
  String get buttonSetupNumbers;

  /// No description provided for @countdownTitle.
  ///
  /// In ko, this message translates to:
  /// **'제 {round}회 추첨까지'**
  String countdownTitle(int round);

  /// No description provided for @countdownDays.
  ///
  /// In ko, this message translates to:
  /// **'일'**
  String get countdownDays;

  /// No description provided for @countdownHours.
  ///
  /// In ko, this message translates to:
  /// **'시간'**
  String get countdownHours;

  /// No description provided for @countdownMinutes.
  ///
  /// In ko, this message translates to:
  /// **'분'**
  String get countdownMinutes;

  /// No description provided for @countdownSeconds.
  ///
  /// In ko, this message translates to:
  /// **'초'**
  String get countdownSeconds;

  /// No description provided for @winningNumbersWithRound.
  ///
  /// In ko, this message translates to:
  /// **'🏆 제 {round}회 당첨번호'**
  String winningNumbersWithRound(int round);

  /// No description provided for @winningNumbersPrevious.
  ///
  /// In ko, this message translates to:
  /// **'🏆 지난 당첨번호'**
  String get winningNumbersPrevious;

  /// No description provided for @winningNumbersLoadError.
  ///
  /// In ko, this message translates to:
  /// **'당첨번호를 불러올 수 없습니다'**
  String get winningNumbersLoadError;

  /// No description provided for @balanceTitle.
  ///
  /// In ko, this message translates to:
  /// **'예치금 잔액'**
  String get balanceTitle;

  /// No description provided for @numberSetupTitle.
  ///
  /// In ko, this message translates to:
  /// **'번호 설정'**
  String get numberSetupTitle;

  /// No description provided for @bannerEnableAutoPurchase.
  ///
  /// In ko, this message translates to:
  /// **'설정에서 자동 구매를 활성화해주세요'**
  String get bannerEnableAutoPurchase;

  /// No description provided for @numberSetupInstruction.
  ///
  /// In ko, this message translates to:
  /// **'매주 자동 구매할 번호를 설정하세요.\n수동 번호는 매주 고정, 자동은 매주 랜덤 생성됩니다.'**
  String get numberSetupInstruction;

  /// No description provided for @modeManual.
  ///
  /// In ko, this message translates to:
  /// **'✏️ 수동'**
  String get modeManual;

  /// No description provided for @modeAuto.
  ///
  /// In ko, this message translates to:
  /// **'🎲 자동'**
  String get modeAuto;

  /// No description provided for @autoNumberTitle.
  ///
  /// In ko, this message translates to:
  /// **'자동 번호'**
  String get autoNumberTitle;

  /// No description provided for @autoNumberSubtitle.
  ///
  /// In ko, this message translates to:
  /// **'매주 랜덤으로 생성됩니다'**
  String get autoNumberSubtitle;

  /// No description provided for @buttonAllAuto.
  ///
  /// In ko, this message translates to:
  /// **'전부 자동'**
  String get buttonAllAuto;

  /// No description provided for @buttonReset.
  ///
  /// In ko, this message translates to:
  /// **'초기화'**
  String get buttonReset;

  /// No description provided for @buttonConfirmGame.
  ///
  /// In ko, this message translates to:
  /// **'게임 {letter} 확정 ✓'**
  String buttonConfirmGame(String letter);

  /// No description provided for @selectionCount.
  ///
  /// In ko, this message translates to:
  /// **'선택: {count}/6  '**
  String selectionCount(int count);

  /// No description provided for @gameSummaryTitle.
  ///
  /// In ko, this message translates to:
  /// **'📋 게임 설정'**
  String get gameSummaryTitle;

  /// No description provided for @gameSummarySelecting.
  ///
  /// In ko, this message translates to:
  /// **'선택 중...'**
  String get gameSummarySelecting;

  /// No description provided for @gameSummaryNotSet.
  ///
  /// In ko, this message translates to:
  /// **'미설정'**
  String get gameSummaryNotSet;

  /// No description provided for @gameSummaryAuto.
  ///
  /// In ko, this message translates to:
  /// **'🎲 자동 (매주 랜덤)'**
  String get gameSummaryAuto;

  /// No description provided for @buttonSaveDone.
  ///
  /// In ko, this message translates to:
  /// **'저장 완료!'**
  String get buttonSaveDone;

  /// No description provided for @buttonSaveGames.
  ///
  /// In ko, this message translates to:
  /// **'{count}게임 설정 저장'**
  String buttonSaveGames(int count);

  /// No description provided for @snackbarSaveSuccess.
  ///
  /// In ko, this message translates to:
  /// **'✅ {count}게임 설정 저장 완료! {schedule}에 자동 구매됩니다.'**
  String snackbarSaveSuccess(int count, String schedule);

  /// No description provided for @historyTitle.
  ///
  /// In ko, this message translates to:
  /// **'구매 기록'**
  String get historyTitle;

  /// No description provided for @historyNoRecords.
  ///
  /// In ko, this message translates to:
  /// **'구매 기록이 없습니다'**
  String get historyNoRecords;

  /// No description provided for @historyLoginToLoad.
  ///
  /// In ko, this message translates to:
  /// **'로그인하면 구매 기록을 불러옵니다'**
  String get historyLoginToLoad;

  /// No description provided for @historyLoadError.
  ///
  /// In ko, this message translates to:
  /// **'조회 실패: {error}'**
  String historyLoadError(String error);

  /// No description provided for @statusPending.
  ///
  /// In ko, this message translates to:
  /// **'확인 대기'**
  String get statusPending;

  /// No description provided for @statusNoWin.
  ///
  /// In ko, this message translates to:
  /// **'낙첨'**
  String get statusNoWin;

  /// No description provided for @rankWithEmoji.
  ///
  /// In ko, this message translates to:
  /// **'{rank} 🎉'**
  String rankWithEmoji(String rank);

  /// No description provided for @roundLabel.
  ///
  /// In ko, this message translates to:
  /// **'제 {round}회'**
  String roundLabel(int round);

  /// No description provided for @prizeLabel.
  ///
  /// In ko, this message translates to:
  /// **'당첨금: ₩{amount}'**
  String prizeLabel(String amount);

  /// No description provided for @rank1st.
  ///
  /// In ko, this message translates to:
  /// **'1등'**
  String get rank1st;

  /// No description provided for @rank2nd.
  ///
  /// In ko, this message translates to:
  /// **'2등'**
  String get rank2nd;

  /// No description provided for @rank3rd.
  ///
  /// In ko, this message translates to:
  /// **'3등'**
  String get rank3rd;

  /// No description provided for @rank4th.
  ///
  /// In ko, this message translates to:
  /// **'4등'**
  String get rank4th;

  /// No description provided for @rank5th.
  ///
  /// In ko, this message translates to:
  /// **'5등'**
  String get rank5th;

  /// No description provided for @settingsTitle.
  ///
  /// In ko, this message translates to:
  /// **'설정'**
  String get settingsTitle;

  /// No description provided for @sectionAccount.
  ///
  /// In ko, this message translates to:
  /// **'👤 계정'**
  String get sectionAccount;

  /// No description provided for @sectionAutoPurchase.
  ///
  /// In ko, this message translates to:
  /// **'⏰ 자동 구매'**
  String get sectionAutoPurchase;

  /// No description provided for @sectionNotifications.
  ///
  /// In ko, this message translates to:
  /// **'🔔 알림'**
  String get sectionNotifications;

  /// No description provided for @sectionAppInfo.
  ///
  /// In ko, this message translates to:
  /// **'📱 앱 정보'**
  String get sectionAppInfo;

  /// No description provided for @dhLotteryAccount.
  ///
  /// In ko, this message translates to:
  /// **'동행복권 계정'**
  String get dhLotteryAccount;

  /// No description provided for @statusLoggedIn.
  ///
  /// In ko, this message translates to:
  /// **'로그인됨'**
  String get statusLoggedIn;

  /// No description provided for @statusLoginRequired.
  ///
  /// In ko, this message translates to:
  /// **'로그인 필요'**
  String get statusLoginRequired;

  /// No description provided for @buttonLogout.
  ///
  /// In ko, this message translates to:
  /// **'로그아웃'**
  String get buttonLogout;

  /// No description provided for @buttonLogin.
  ///
  /// In ko, this message translates to:
  /// **'로그인'**
  String get buttonLogin;

  /// No description provided for @dialogLoginTitle.
  ///
  /// In ko, this message translates to:
  /// **'동행복권 로그인'**
  String get dialogLoginTitle;

  /// No description provided for @inputUserId.
  ///
  /// In ko, this message translates to:
  /// **'아이디'**
  String get inputUserId;

  /// No description provided for @inputPassword.
  ///
  /// In ko, this message translates to:
  /// **'비밀번호'**
  String get inputPassword;

  /// No description provided for @buttonCancel.
  ///
  /// In ko, this message translates to:
  /// **'취소'**
  String get buttonCancel;

  /// No description provided for @buttonLoggingIn.
  ///
  /// In ko, this message translates to:
  /// **'로그인 중...'**
  String get buttonLoggingIn;

  /// No description provided for @snackbarLoginSuccess.
  ///
  /// In ko, this message translates to:
  /// **'✅ 로그인 성공!'**
  String get snackbarLoginSuccess;

  /// No description provided for @snackbarLogoutSuccess.
  ///
  /// In ko, this message translates to:
  /// **'로그아웃 완료'**
  String get snackbarLogoutSuccess;

  /// No description provided for @settingEnableAutoPurchase.
  ///
  /// In ko, this message translates to:
  /// **'자동 구매 활성화'**
  String get settingEnableAutoPurchase;

  /// No description provided for @hintLoginRequired.
  ///
  /// In ko, this message translates to:
  /// **'로그인 후 사용 가능'**
  String get hintLoginRequired;

  /// No description provided for @gamesConfigured.
  ///
  /// In ko, this message translates to:
  /// **'{count}게임 설정됨'**
  String gamesConfigured(int count);

  /// No description provided for @hintChangeInNumberTab.
  ///
  /// In ko, this message translates to:
  /// **'번호 설정 탭에서 변경'**
  String get hintChangeInNumberTab;

  /// No description provided for @hintSetupGamesInNumberTab.
  ///
  /// In ko, this message translates to:
  /// **'번호 설정 탭에서 게임을 설정해주세요'**
  String get hintSetupGamesInNumberTab;

  /// No description provided for @settingPurchaseDay.
  ///
  /// In ko, this message translates to:
  /// **'구매 요일'**
  String get settingPurchaseDay;

  /// No description provided for @settingPurchaseTime.
  ///
  /// In ko, this message translates to:
  /// **'구매 시간'**
  String get settingPurchaseTime;

  /// No description provided for @settingBatteryOptimization.
  ///
  /// In ko, this message translates to:
  /// **'배터리 최적화 제외'**
  String get settingBatteryOptimization;

  /// No description provided for @hintBatteryOptimization.
  ///
  /// In ko, this message translates to:
  /// **'정시 실행을 위해 권장'**
  String get hintBatteryOptimization;

  /// No description provided for @settingPurchaseNoti.
  ///
  /// In ko, this message translates to:
  /// **'구매 완료 알림'**
  String get settingPurchaseNoti;

  /// No description provided for @settingResultNoti.
  ///
  /// In ko, this message translates to:
  /// **'당첨 결과 알림'**
  String get settingResultNoti;

  /// No description provided for @notificationResultTime.
  ///
  /// In ko, this message translates to:
  /// **'매주 토요일 21:00'**
  String get notificationResultTime;

  /// No description provided for @settingVersion.
  ///
  /// In ko, this message translates to:
  /// **'버전'**
  String get settingVersion;

  /// No description provided for @settingOpenSource.
  ///
  /// In ko, this message translates to:
  /// **'오픈소스'**
  String get settingOpenSource;

  /// No description provided for @settingResetData.
  ///
  /// In ko, this message translates to:
  /// **'데이터 초기화'**
  String get settingResetData;

  /// No description provided for @dialogResetTitle.
  ///
  /// In ko, this message translates to:
  /// **'데이터 초기화'**
  String get dialogResetTitle;

  /// No description provided for @dialogResetMessage.
  ///
  /// In ko, this message translates to:
  /// **'모든 설정과 구매 기록이 삭제됩니다.\n정말 초기화하시겠습니까?'**
  String get dialogResetMessage;

  /// No description provided for @buttonReset2.
  ///
  /// In ko, this message translates to:
  /// **'초기화'**
  String get buttonReset2;

  /// No description provided for @errorInvalidPurchaseTime.
  ///
  /// In ko, this message translates to:
  /// **'현재 설정된 시간이 해당 요일에 구매 불가합니다. 시간을 먼저 변경해주세요.'**
  String get errorInvalidPurchaseTime;

  /// No description provided for @errorPurchaseTimeRestriction.
  ///
  /// In ko, this message translates to:
  /// **'해당 시간에는 구매할 수 없습니다.\n평일/일: 06:00~23:59, 토: 06:00~19:59'**
  String get errorPurchaseTimeRestriction;

  /// No description provided for @snackbarBatteryAlreadyExcluded.
  ///
  /// In ko, this message translates to:
  /// **'✅ 이미 배터리 최적화에서 제외되어 있습니다'**
  String get snackbarBatteryAlreadyExcluded;

  /// No description provided for @snackbarBatteryManualDisable.
  ///
  /// In ko, this message translates to:
  /// **'앱 설정에서 배터리 최적화를 직접 해제해주세요'**
  String get snackbarBatteryManualDisable;

  /// No description provided for @errorBatterySettings.
  ///
  /// In ko, this message translates to:
  /// **'배터리 최적화 설정을 열 수 없습니다: {error}'**
  String errorBatterySettings(String error);

  /// No description provided for @dayMon.
  ///
  /// In ko, this message translates to:
  /// **'월'**
  String get dayMon;

  /// No description provided for @dayTue.
  ///
  /// In ko, this message translates to:
  /// **'화'**
  String get dayTue;

  /// No description provided for @dayWed.
  ///
  /// In ko, this message translates to:
  /// **'수'**
  String get dayWed;

  /// No description provided for @dayThu.
  ///
  /// In ko, this message translates to:
  /// **'목'**
  String get dayThu;

  /// No description provided for @dayFri.
  ///
  /// In ko, this message translates to:
  /// **'금'**
  String get dayFri;

  /// No description provided for @daySat.
  ///
  /// In ko, this message translates to:
  /// **'토'**
  String get daySat;

  /// No description provided for @daySun.
  ///
  /// In ko, this message translates to:
  /// **'일'**
  String get daySun;

  /// No description provided for @dayFormat.
  ///
  /// In ko, this message translates to:
  /// **'{day}요일'**
  String dayFormat(String day);

  /// No description provided for @weeklySchedule.
  ///
  /// In ko, this message translates to:
  /// **'매주 {day}요일 {time}'**
  String weeklySchedule(String day, String time);

  /// No description provided for @settingLanguage.
  ///
  /// In ko, this message translates to:
  /// **'언어'**
  String get settingLanguage;

  /// No description provided for @languageSystem.
  ///
  /// In ko, this message translates to:
  /// **'시스템 기본'**
  String get languageSystem;

  /// No description provided for @languageKo.
  ///
  /// In ko, this message translates to:
  /// **'한국어'**
  String get languageKo;

  /// No description provided for @languageEn.
  ///
  /// In ko, this message translates to:
  /// **'English'**
  String get languageEn;

  /// No description provided for @languageJa.
  ///
  /// In ko, this message translates to:
  /// **'日本語'**
  String get languageJa;

  /// No description provided for @errorInvalidCredentials.
  ///
  /// In ko, this message translates to:
  /// **'아이디 또는 비밀번호가 올바르지 않습니다.'**
  String get errorInvalidCredentials;

  /// No description provided for @balanceAlertTitle.
  ///
  /// In ko, this message translates to:
  /// **'잔액 부족 알림'**
  String get balanceAlertTitle;

  /// No description provided for @balanceAlertDesc.
  ///
  /// In ko, this message translates to:
  /// **'설정 금액 이하일 때 알림'**
  String get balanceAlertDesc;

  /// No description provided for @balanceThreshold.
  ///
  /// In ko, this message translates to:
  /// **'알림 기준 금액'**
  String get balanceThreshold;

  /// No description provided for @chargeNow.
  ///
  /// In ko, this message translates to:
  /// **'충전하기'**
  String get chargeNow;

  /// No description provided for @balanceLowNotifTitle.
  ///
  /// In ko, this message translates to:
  /// **'잔액 부족'**
  String get balanceLowNotifTitle;

  /// No description provided for @balanceLowNotifBody.
  ///
  /// In ko, this message translates to:
  /// **'예치금 잔액이 {amount}원입니다. 충전이 필요합니다.'**
  String balanceLowNotifBody(String amount);

  /// No description provided for @thresholdCustom.
  ///
  /// In ko, this message translates to:
  /// **'직접입력'**
  String get thresholdCustom;

  /// No description provided for @thresholdInputTitle.
  ///
  /// In ko, this message translates to:
  /// **'알림 기준 금액 입력'**
  String get thresholdInputTitle;

  /// No description provided for @thresholdInputHint.
  ///
  /// In ko, this message translates to:
  /// **'금액 (원)'**
  String get thresholdInputHint;

  /// No description provided for @buttonConfirm.
  ///
  /// In ko, this message translates to:
  /// **'확인'**
  String get buttonConfirm;

  /// No description provided for @sectionDonation.
  ///
  /// In ko, this message translates to:
  /// **'개발자 후원하기'**
  String get sectionDonation;

  /// No description provided for @donationTitle.
  ///
  /// In ko, this message translates to:
  /// **'커피 한 잔 후원'**
  String get donationTitle;
}

class _AppLocalizationsDelegate
    extends LocalizationsDelegate<AppLocalizations> {
  const _AppLocalizationsDelegate();

  @override
  Future<AppLocalizations> load(Locale locale) {
    return SynchronousFuture<AppLocalizations>(lookupAppLocalizations(locale));
  }

  @override
  bool isSupported(Locale locale) =>
      <String>['en', 'ja', 'ko'].contains(locale.languageCode);

  @override
  bool shouldReload(_AppLocalizationsDelegate old) => false;
}

AppLocalizations lookupAppLocalizations(Locale locale) {
  // Lookup logic when only language code is specified.
  switch (locale.languageCode) {
    case 'en':
      return AppLocalizationsEn();
    case 'ja':
      return AppLocalizationsJa();
    case 'ko':
      return AppLocalizationsKo();
  }

  throw FlutterError(
    'AppLocalizations.delegate failed to load unsupported locale "$locale". This is likely '
    'an issue with the localizations generation tool. Please file an issue '
    'on GitHub with a reproducible sample app and the gen-l10n configuration '
    'that was used.',
  );
}
