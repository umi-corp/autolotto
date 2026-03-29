// ignore: unused_import
import 'package:intl/intl.dart' as intl;
import 'app_localizations.dart';

// ignore_for_file: type=lint

/// The translations for Japanese (`ja`).
class AppLocalizationsJa extends AppLocalizations {
  AppLocalizationsJa([String locale = 'ja']) : super(locale);

  @override
  String get bottomNavHome => 'ホーム';

  @override
  String get bottomNavNumbers => '番号設定';

  @override
  String get bottomNavHistory => '履歴';

  @override
  String get bottomNavSettings => '設定';

  @override
  String get appTitle => 'AutoLotto';

  @override
  String get autoPurchaseEnabled => '自動購入 有効';

  @override
  String get autoPurchaseDisabled => '自動購入 無効';

  @override
  String autoPurchaseSchedule(String schedule) {
    return '$scheduleに自動購入されます';
  }

  @override
  String get enableInSettings => '設定で有効にしてください';

  @override
  String get buttonSetupNumbers => '🎯 番号を設定する';

  @override
  String countdownTitle(int round) {
    return '第$round回 抽選まで';
  }

  @override
  String get countdownDays => '日';

  @override
  String get countdownHours => '時';

  @override
  String get countdownMinutes => '分';

  @override
  String get countdownSeconds => '秒';

  @override
  String winningNumbersWithRound(int round) {
    return '🏆 第$round回 当選番号';
  }

  @override
  String get winningNumbersPrevious => '🏆 前回の当選番号';

  @override
  String get winningNumbersLoadError => '当選番号を読み込めません';

  @override
  String get balanceTitle => '預り金残高';

  @override
  String get numberSetupTitle => '番号設定';

  @override
  String get bannerEnableAutoPurchase => '設定で自動購入を有効にしてください';

  @override
  String get numberSetupInstruction =>
      '毎週自動購入する番号を設定してください。\n手動番号は毎週固定、自動は毎週ランダム生成されます。';

  @override
  String get modeManual => '✏️ 手動';

  @override
  String get modeAuto => '🎲 自動';

  @override
  String get autoNumberTitle => '自動番号';

  @override
  String get autoNumberSubtitle => '毎週ランダムに生成されます';

  @override
  String get buttonAllAuto => 'すべて自動';

  @override
  String get buttonReset => 'リセット';

  @override
  String buttonConfirmGame(String letter) {
    return 'ゲーム$letter 確定 ✓';
  }

  @override
  String selectionCount(int count) {
    return '選択: $count/6  ';
  }

  @override
  String get gameSummaryTitle => '📋 ゲーム設定';

  @override
  String get gameSummarySelecting => '選択中...';

  @override
  String get gameSummaryNotSet => '未設定';

  @override
  String get gameSummaryAuto => '🎲 自動 (毎週ランダム)';

  @override
  String get buttonSaveDone => '保存完了！';

  @override
  String buttonSaveGames(int count) {
    return '$countゲーム設定を保存';
  }

  @override
  String snackbarSaveSuccess(int count, String schedule) {
    return '✅ $countゲーム設定保存完了！ $scheduleに自動購入されます。';
  }

  @override
  String get historyTitle => '購入履歴';

  @override
  String get historyNoRecords => '購入履歴がありません';

  @override
  String get historyLoginToLoad => 'ログインすると購入履歴を表示します';

  @override
  String historyLoadError(String error) {
    return '読み込み失敗: $error';
  }

  @override
  String get statusPending => '確認待ち';

  @override
  String get statusNoWin => '落選';

  @override
  String rankWithEmoji(String rank) {
    return '$rank 🎉';
  }

  @override
  String roundLabel(int round) {
    return '第$round回';
  }

  @override
  String prizeLabel(String amount) {
    return '当選金: ₩$amount';
  }

  @override
  String get rank1st => '1等';

  @override
  String get rank2nd => '2等';

  @override
  String get rank3rd => '3等';

  @override
  String get rank4th => '4等';

  @override
  String get rank5th => '5等';

  @override
  String get settingsTitle => '設定';

  @override
  String get sectionAccount => '👤 アカウント';

  @override
  String get sectionAutoPurchase => '⏰ 自動購入';

  @override
  String get sectionNotifications => '🔔 通知';

  @override
  String get sectionAppInfo => '📱 アプリ情報';

  @override
  String get dhLotteryAccount => '同行福券アカウント';

  @override
  String get statusLoggedIn => 'ログイン済み';

  @override
  String get statusLoginRequired => 'ログインが必要';

  @override
  String get buttonLogout => 'ログアウト';

  @override
  String get buttonLogin => 'ログイン';

  @override
  String get dialogLoginTitle => '同行福券ログイン';

  @override
  String get inputUserId => 'ユーザーID';

  @override
  String get inputPassword => 'パスワード';

  @override
  String get buttonCancel => 'キャンセル';

  @override
  String get buttonLoggingIn => 'ログイン中...';

  @override
  String get snackbarLoginSuccess => '✅ ログイン成功！';

  @override
  String get snackbarLogoutSuccess => 'ログアウト完了';

  @override
  String get settingEnableAutoPurchase => '自動購入を有効にする';

  @override
  String get hintLoginRequired => 'ログイン後に利用可能';

  @override
  String gamesConfigured(int count) {
    return '$countゲーム設定済み';
  }

  @override
  String get hintChangeInNumberTab => '番号設定タブで変更';

  @override
  String get hintSetupGamesInNumberTab => '番号設定タブでゲームを設定してください';

  @override
  String get settingPurchaseDay => '購入曜日';

  @override
  String get settingPurchaseTime => '購入時間';

  @override
  String get settingBatteryOptimization => 'バッテリー最適化を除外';

  @override
  String get hintBatteryOptimization => '定時実行のため推奨';

  @override
  String get settingPurchaseNoti => '購入完了通知';

  @override
  String get settingResultNoti => '当選結果通知';

  @override
  String get notificationResultTime => '毎週土曜日 21:00';

  @override
  String get settingVersion => 'バージョン';

  @override
  String get settingOpenSource => 'オープンソース';

  @override
  String get settingResetData => 'データ初期化';

  @override
  String get dialogResetTitle => 'データ初期化';

  @override
  String get dialogResetMessage => 'すべての設定と購入履歴が削除されます。\n本当に初期化しますか？';

  @override
  String get buttonReset2 => '初期化';

  @override
  String get errorInvalidPurchaseTime => '現在の設定時間ではこの曜日に購入できません。先に時間を変更してください。';

  @override
  String get errorPurchaseTimeRestriction =>
      'この時間帯は購入できません。\n平日/日: 06:00~23:59、土: 06:00~19:59';

  @override
  String get snackbarBatteryAlreadyExcluded => '✅ すでにバッテリー最適化から除外されています';

  @override
  String get snackbarBatteryManualDisable => 'アプリ設定でバッテリー最適化を手動で解除してください';

  @override
  String errorBatterySettings(String error) {
    return 'バッテリー設定を開けません: $error';
  }

  @override
  String get dayMon => '月';

  @override
  String get dayTue => '火';

  @override
  String get dayWed => '水';

  @override
  String get dayThu => '木';

  @override
  String get dayFri => '金';

  @override
  String get daySat => '土';

  @override
  String get daySun => '日';

  @override
  String dayFormat(String day) {
    return '$day曜日';
  }

  @override
  String weeklySchedule(String day, String time) {
    return '毎週$day曜日 $time';
  }

  @override
  String get settingLanguage => '言語';

  @override
  String get languageSystem => 'システムデフォルト';

  @override
  String get languageKo => '한국어';

  @override
  String get languageEn => 'English';

  @override
  String get languageJa => '日本語';

  @override
  String get errorInvalidCredentials => 'IDまたはパスワードが正しくありません。';

  @override
  String get balanceAlertTitle => '残高不足通知';

  @override
  String get balanceAlertDesc => '設定金額以下で通知';

  @override
  String get balanceThreshold => '通知基準額';

  @override
  String get chargeNow => 'チャージする';

  @override
  String get balanceLowNotifTitle => '残高不足';

  @override
  String balanceLowNotifBody(String amount) {
    return '預り金残高が$amountウォンです。チャージが必要です。';
  }

  @override
  String get thresholdCustom => '直接入力';

  @override
  String get thresholdInputTitle => '通知基準額を入力';

  @override
  String get thresholdInputHint => '金額 (ウォン)';

  @override
  String get buttonConfirm => '確認';

  @override
  String get sectionDonation => '開発者を応援する';

  @override
  String get donationTitle => 'コーヒー1杯おごる';
}
