// ignore: unused_import
import 'package:intl/intl.dart' as intl;
import 'app_localizations.dart';

// ignore_for_file: type=lint

/// The translations for English (`en`).
class AppLocalizationsEn extends AppLocalizations {
  AppLocalizationsEn([String locale = 'en']) : super(locale);

  @override
  String get bottomNavHome => 'Home';

  @override
  String get bottomNavNumbers => 'Numbers';

  @override
  String get bottomNavHistory => 'History';

  @override
  String get bottomNavSettings => 'Settings';

  @override
  String get appTitle => 'AutoLotto';

  @override
  String get autoPurchaseEnabled => 'Auto Purchase Enabled';

  @override
  String get autoPurchaseDisabled => 'Auto Purchase Disabled';

  @override
  String autoPurchaseSchedule(String schedule) {
    return 'Auto purchase at $schedule';
  }

  @override
  String get enableInSettings => 'Enable in Settings';

  @override
  String get buttonSetupNumbers => '🎯 Set Up Numbers';

  @override
  String countdownTitle(int round) {
    return 'Draw #$round in';
  }

  @override
  String get countdownDays => 'D';

  @override
  String get countdownHours => 'H';

  @override
  String get countdownMinutes => 'M';

  @override
  String get countdownSeconds => 'S';

  @override
  String winningNumbersWithRound(int round) {
    return '🏆 Draw #$round Winning Numbers';
  }

  @override
  String get winningNumbersPrevious => '🏆 Previous Winning Numbers';

  @override
  String get winningNumbersLoadError => 'Unable to load winning numbers';

  @override
  String get balanceTitle => 'Deposit Balance';

  @override
  String get numberSetupTitle => 'Number Setup';

  @override
  String get bannerEnableAutoPurchase =>
      'Please enable auto purchase in Settings';

  @override
  String get numberSetupInstruction =>
      'Set numbers for weekly auto purchase.\nManual numbers are fixed weekly, auto numbers are randomly generated.';

  @override
  String get modeManual => '✏️ Manual';

  @override
  String get modeAuto => '🎲 Auto';

  @override
  String get autoNumberTitle => 'Auto Numbers';

  @override
  String get autoNumberSubtitle => 'Randomly generated each week';

  @override
  String get buttonAllAuto => 'All Auto';

  @override
  String get buttonReset => 'Reset';

  @override
  String buttonConfirmGame(String letter) {
    return 'Confirm Game $letter ✓';
  }

  @override
  String selectionCount(int count) {
    return 'Selected: $count/6  ';
  }

  @override
  String get gameSummaryTitle => '📋 Game Settings';

  @override
  String get gameSummarySelecting => 'Selecting...';

  @override
  String get gameSummaryNotSet => 'Not set';

  @override
  String get gameSummaryAuto => '🎲 Auto (Weekly Random)';

  @override
  String get buttonSaveDone => 'Saved!';

  @override
  String buttonSaveGames(int count) {
    return 'Save $count Games';
  }

  @override
  String snackbarSaveSuccess(int count, String schedule) {
    return '✅ $count games saved! Auto purchase at $schedule.';
  }

  @override
  String get historyTitle => 'Purchase History';

  @override
  String get historyNoRecords => 'No purchase records';

  @override
  String get historyLoginToLoad => 'Login to load purchase history';

  @override
  String historyLoadError(String error) {
    return 'Load failed: $error';
  }

  @override
  String get statusPending => 'Pending';

  @override
  String get statusNoWin => 'No Win';

  @override
  String rankWithEmoji(String rank) {
    return '$rank 🎉';
  }

  @override
  String roundLabel(int round) {
    return 'Draw #$round';
  }

  @override
  String prizeLabel(String amount) {
    return 'Prize: ₩$amount';
  }

  @override
  String get rank1st => '1st';

  @override
  String get rank2nd => '2nd';

  @override
  String get rank3rd => '3rd';

  @override
  String get rank4th => '4th';

  @override
  String get rank5th => '5th';

  @override
  String get settingsTitle => 'Settings';

  @override
  String get sectionAccount => '👤 Account';

  @override
  String get sectionAutoPurchase => '⏰ Auto Purchase';

  @override
  String get sectionNotifications => '🔔 Notifications';

  @override
  String get sectionAppInfo => '📱 App Info';

  @override
  String get dhLotteryAccount => 'DHLottery Account';

  @override
  String get statusLoggedIn => 'Logged in';

  @override
  String get statusLoginRequired => 'Login required';

  @override
  String get buttonLogout => 'Logout';

  @override
  String get buttonLogin => 'Login';

  @override
  String get dialogLoginTitle => 'DHLottery Login';

  @override
  String get inputUserId => 'User ID';

  @override
  String get inputPassword => 'Password';

  @override
  String get buttonCancel => 'Cancel';

  @override
  String get buttonLoggingIn => 'Logging in...';

  @override
  String get snackbarLoginSuccess => '✅ Login successful!';

  @override
  String get snackbarLogoutSuccess => 'Logged out';

  @override
  String get settingEnableAutoPurchase => 'Enable Auto Purchase';

  @override
  String get hintLoginRequired => 'Login required';

  @override
  String gamesConfigured(int count) {
    return '$count games configured';
  }

  @override
  String get hintChangeInNumberTab => 'Change in Numbers tab';

  @override
  String get hintSetupGamesInNumberTab => 'Set up games in Numbers tab';

  @override
  String get settingPurchaseDay => 'Purchase Day';

  @override
  String get settingPurchaseTime => 'Purchase Time';

  @override
  String get settingBatteryOptimization => 'Disable Battery Optimization';

  @override
  String get hintBatteryOptimization => 'Recommended for on-time execution';

  @override
  String get settingPurchaseNoti => 'Purchase Notification';

  @override
  String get settingResultNoti => 'Result Notification';

  @override
  String get notificationResultTime => 'Every Saturday 21:00';

  @override
  String get settingVersion => 'Version';

  @override
  String get settingOpenSource => 'Open Source';

  @override
  String get settingResetData => 'Reset Data';

  @override
  String get dialogResetTitle => 'Reset Data';

  @override
  String get dialogResetMessage =>
      'All settings and purchase records will be deleted.\nAre you sure?';

  @override
  String get buttonReset2 => 'Reset';

  @override
  String get errorInvalidPurchaseTime =>
      'Current time is not valid for the selected day. Please change the time first.';

  @override
  String get errorPurchaseTimeRestriction =>
      'Cannot purchase at this time.\nWeekday/Sun: 06:00~23:59, Sat: 06:00~19:59';

  @override
  String get snackbarBatteryAlreadyExcluded =>
      '✅ Already excluded from battery optimization';

  @override
  String get snackbarBatteryManualDisable =>
      'Please disable battery optimization in app settings';

  @override
  String errorBatterySettings(String error) {
    return 'Cannot open battery settings: $error';
  }

  @override
  String get dayMon => 'Mon';

  @override
  String get dayTue => 'Tue';

  @override
  String get dayWed => 'Wed';

  @override
  String get dayThu => 'Thu';

  @override
  String get dayFri => 'Fri';

  @override
  String get daySat => 'Sat';

  @override
  String get daySun => 'Sun';

  @override
  String dayFormat(String day) {
    return '$day';
  }

  @override
  String weeklySchedule(String day, String time) {
    return 'Every $day $time';
  }

  @override
  String get settingLanguage => 'Language';

  @override
  String get languageSystem => 'System Default';

  @override
  String get languageKo => '한국어';

  @override
  String get languageEn => 'English';

  @override
  String get languageJa => '日本語';

  @override
  String get errorInvalidCredentials => 'Invalid username or password.';

  @override
  String get balanceAlertTitle => 'Low Balance Alert';

  @override
  String get balanceAlertDesc => 'Alert when below threshold';

  @override
  String get balanceThreshold => 'Alert Threshold';

  @override
  String get chargeNow => 'Charge Now';

  @override
  String get balanceLowNotifTitle => 'Low Balance';

  @override
  String balanceLowNotifBody(String amount) {
    return 'Deposit balance is ₩$amount. Please charge.';
  }

  @override
  String get thresholdCustom => 'Custom';

  @override
  String get thresholdInputTitle => 'Enter Alert Threshold';

  @override
  String get thresholdInputHint => 'Amount (KRW)';

  @override
  String get buttonConfirm => 'OK';

  @override
  String get sectionDonation => 'Support Developer';

  @override
  String get donationTitle => 'Buy Me a Coffee';
}
